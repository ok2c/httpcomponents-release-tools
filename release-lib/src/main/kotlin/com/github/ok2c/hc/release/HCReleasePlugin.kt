/*
 * Copyright 2020, OK2 Consulting Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ok2c.hc.release

import com.github.ok2c.hc.release.git.getAllTags
import com.github.ok2c.hc.release.pom.PomArtifact
import com.github.ok2c.hc.release.pom.PomTool
import com.github.ok2c.hc.release.support.deleteContent
import com.github.ok2c.hc.release.svn.Svn
import com.github.ok2c.hc.release.svn.SvnBulkOp
import com.github.ok2c.hc.release.svn.SvnCpFileOp
import com.github.ok2c.hc.release.svn.SvnRmOp
import org.apache.tools.ant.filters.FixCrLfFilter
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.util.FS
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.Sign
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors

const val CRLF = "crlf"
const val LF = "lf"
const val SCM_SVN = "scm:svn:"

const val HC_DIST_URI = "https://dist.apache.org/repos/dist"

val PROJECT_NAME_MAP = mapOf(
        "httpcore5-parent" to "HttpCore",
        "httpclient5-parent" to "HttpClient",
        "httpcomponents-core" to "HttpCore",
        "httpcomponents-client" to "HttpClient",
        "httpcomponents-asyncclient" to "HttpAsyncClient"
)

val PACKAGE_NAME_MAP = mapOf(
        "httpcore5-parent" to "httpcomponents-core",
        "httpclient5-parent" to "httpcomponents-client"
)

class ReleaseException(message: String?) : RuntimeException(message)

inline fun <R> repoGit(dir: Path, block: (Git) -> R): R {
    RepositoryCache.open(RepositoryCache.FileKey.lenient(dir.toFile(), FS.DETECTED), true).use { repo ->
        val git = Git(repo)
        return block(git)
    }
}

fun prompt(message: String, defaultValue: String): String {
    val console = System.console() ?: return defaultValue

    System.out.println("${message}: [defaults to ${defaultValue}]")

    val input = console.readLine()
    return if (!input.isNullOrBlank()) input else defaultValue
}

class HCReleasePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val p1 = project.property("HC_RELEASE_DIR") as String?
        if (p1 == null) {
            project.logger.warn("HC release directory not specified")
            return
        }
        val releaseDir = Paths.get(p1)

        val p2 = project.property("HC_DIST_DIR") as String?
        if (p2 == null) {
            project.logger.warn("HC dist staging directory not specified")
        }
        val distStagingDir = if (p2 != null) Paths.get(p2) else null

        val pomTool = PomTool()
        val pom = pomTool.digest(releaseDir.resolve("pom.xml"))

        val artefactId = pom.artefactId ?: throw ReleaseException("Missing artefact ID in POM")
        val artefactVersion = pom.version ?: throw ReleaseException("Missing artefact version in POM")
        val release = artefactVersion.qualifier.isNullOrBlank() || !artefactVersion.qualifier.endsWith("SNAPSHOT")

        val modulePoms = pom.modules.stream().map {
            pomTool.digest(releaseDir.resolve(it).resolve("pom.xml"))
        }.collect(Collectors.toList())

        project.repositories.add(project.repositories.mavenLocal())

        val hc = project.configurations.create("hc")
        for (modulePom in modulePoms) {
            val dependency = project.dependencies.create("${modulePom.groupId}:${modulePom.artefactId}:${modulePom.version}")
            hc.dependencies.add(dependency)
        }

        val productName = PROJECT_NAME_MAP[artefactId] ?: artefactId
        val packageName = PACKAGE_NAME_MAP[artefactId] ?: artefactId

        if (project.logger.isLifecycleEnabled) {
            if (release) {
                project.logger.lifecycle("RELEASE: ${productName} ${artefactVersion}")
            } else {
                project.logger.lifecycle("SNAPSHOT: ${productName} ${artefactVersion}")
            }
        }

        val distDir = project.file(File(project.buildDir, "${packageName}-${artefactVersion.major}.${artefactVersion.minor}-dist"))

        project.tasks.register("releaseDetails") {
            it.group = "Release"
            it.description = "Prints release details"
            it.doLast {
                repoGit(releaseDir) { git ->
                    println("Project directory ${releaseDir}")
                    println("Git branch ${git.repository.branch}")
                    val describeCommand = git.describe()
                    val tag = describeCommand.call()
                    if (tag != null) {
                        println("Release tag ${tag}")
                    } else {
                        println("No tag")
                    }
                    println("")
                    println("${pom.name} ${pom.version}")

                    for (module in pom.modules) {
                        println("- module ${module}")
                    }
                }
            }
        }

        project.tasks.register("createRC") {
            it.group = "Release"
            it.description = "Prepares release candidate"
            it.doLast {
                if (artefactVersion.qualifier.isNullOrBlank()) {
                    throw ReleaseException("Unexpected version in POM: ${artefactVersion}; SNAPSHOT expected")
                }

                var releaseVersion = pomTool.determineNextReleaseVersion(artefactVersion).toString()
                releaseVersion = prompt("Please enter release version", releaseVersion)

                val lastRC = repoGit(releaseDir) { git ->
                    RevWalk(git.repository).use {
                        val releasePattern = "${releaseVersion}-RC"
                        val refMap = git.repository.refDatabase.getRefs(Constants.R_TAGS)
                        refMap.keys.stream()
                                .filter { ref -> ref.startsWith(releasePattern) }
                                .map { ref ->
                                    val s = ref.substring(releasePattern.length)
                                    try {
                                        Integer.parseInt(s)
                                    } catch (ex: NumberFormatException) {
                                        0
                                    }
                                }
                                .max(Integer::compare)
                                .orElse(0)
                    }
                }

                var rc = "RC${lastRC + 1}"
                rc = prompt("Please enter release candidate qualifier", rc)

                println("Preparing release ${productName} ${releaseVersion} ${rc}")

                val rcVer = "${releaseVersion}-${rc}"
                pomTool.updatePomVersion(releaseDir.resolve("pom.xml"), releaseVersion)
                for (module in pom.modules) {
                    pomTool.updatePomVersion(releaseDir.resolve(module).resolve("pom.xml"), releaseVersion)
                }

                println("Committing changes for release ${productName} ${releaseVersion} ${rc}")
                repoGit(releaseDir) { git ->
                    git.commit()
                            .setAll(true)
                            .setMessage("${productName} ${releaseVersion} release")
                            .call()

                    println("Creating tag for ${productName} ${releaseVersion} ${rc}")
                    git.tag()
                            .setName(rcVer)
                            .setAnnotated(true)
                            .setMessage("${productName} ${releaseVersion} ${rc} tag")
                            .call()
                    println("${rcVer} tag created")
                }

                val dir = project.mkdir(distDir)
                project.delete(project.fileTree(dir))
            }
        }

        project.tasks.register("nextSnapshot") {
            it.group = "Release"
            it.description = "Prepares next development version"
            it.doLast {
                var snapshotVersion = pomTool.determineNextSnapshotVersion(artefactVersion).toString()
                snapshotVersion = prompt("Please enter next development version", snapshotVersion)

                println("Upgrading ${productName} from ${artefactVersion} to ${snapshotVersion}")

                pomTool.updatePomVersion(releaseDir.resolve("pom.xml"), snapshotVersion.toString())
                for (module in pom.modules) {
                    pomTool.updatePomVersion(releaseDir.resolve(module).resolve("pom.xml"), snapshotVersion.toString())
                }

                repoGit(releaseDir) { git ->
                    println("Committing changes for snapshot ${productName} ${snapshotVersion}")
                    git.commit()
                            .setAll(true)
                            .setMessage("Upgraded ${productName} version to ${snapshotVersion}")
                            .call()
                }
            }
        }

        project.tasks.register("promoteRC") {
            it.group = "Release"
            it.description = "Promotes release candidate to official release"
            it.doLast {
                if (!release) {
                    throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                }
                repoGit(releaseDir) { git ->
                    println("Creating tag for ${productName} ${artefactVersion} release")
                    git.tag()
                            .setName("rel/v${artefactVersion}")
                            .setAnnotated(true)
                            .setMessage("${productName} ${artefactVersion} release tag")
                            .call()
                }
                println("${artefactVersion} tag created")
            }
        }

        val docs: (String) -> CopySpec = { lineDelimiter ->
            val filerParams = mutableMapOf(
                    "eol" to FixCrLfFilter.CrLf.newInstance(lineDelimiter),
                    "fixlast" to false
            )
            project.copySpec { copySpec ->
                copySpec.from("${releaseDir}") {
                    it.include("README.md")
                    it.include("LICENSE.txt")
                    it.include("NOTICE.txt")
                    it.include("RELEASE_NOTES.txt")
                    it.filter(filerParams, FixCrLfFilter::class.java)
                }
                copySpec.from("${releaseDir}/target/site/apidocs") {
                    it.into("javadoc")
                }
            }
        }

        val libs: () -> CopySpec = {
            project.copySpec { copySpec ->
                copySpec.into("lib")
                hc.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    copySpec.from(artifact.file.absolutePath) {
                        it.include(artifact.file.name)
                    }
                }
            }
        }

        val sources: () -> CopySpec = {
            project.copySpec { copySpec ->
                copySpec.from(releaseDir) {
                    it.exclude("**/bin/**")
                    it.exclude("**/target/**")
                    it.exclude("**/build/**")
                    it.exclude("**/lib/**")
                    it.exclude("**/.*")
                    it.exclude("**/*.iml")
                    it.exclude("**/log4j2-debug.xml")
                }
            }
        }

        if (!hc.resolvedConfiguration.hasError()) {

            project.tasks.register("distBinZip", Zip::class.java) { zip ->
                zip.group = "Release"
                zip.description = "Builds binary dist ZIP package"
                zip.archiveClassifier.set("bin")
                zip.with(docs(CRLF), libs())
            }

            project.tasks.register("distBinTar", Tar::class.java) { tar ->
                tar.group = "Release"
                tar.description = "Builds binary dist TAR.GZ package"
                tar.archiveClassifier.set("bin")
                tar.with(docs(LF), libs())
            }

        }

        project.tasks.register("distSrcZip", Zip::class.java) { zip ->
            zip.group = "Release"
            zip.description = "Builds source dist ZIP package"
            zip.archiveClassifier.set("src")
            zip.with(sources())
        }

        project.tasks.register("distSrcTar", Tar::class.java) { tar ->
            tar.group = "Release"
            tar.description = "Builds source dist TAR.GZ package"
            tar.archiveClassifier.set("src")
            tar.with(sources())
        }

        project.tasks.withType(AbstractArchiveTask::class.java) { archive ->
            archive.archiveBaseName.set(packageName)
            archive.archiveVersion.set("${artefactVersion}")
            archive.destinationDirectory.set(distDir)
        }

        project.tasks.withType(Tar::class.java) { tar ->
            tar.archiveExtension.set("tar.gz")
            tar.compression = Compression.GZIP
        }

        project.configurations.create("distPackages")

        project.tasks.withType(AbstractArchiveTask::class.java) { archive ->
            project.artifacts.add("distPackages", archive)
        }

        if (release) {

            project.plugins.apply("signing")

            val digest = project.tasks.register("digest", Digest::class.java) { digester ->
                digester.group = "Release"
                digester.description = "Creates digest hashes of the release dist packages"
                digester.digest(project.configurations.getByName("distPackages"))
            }

            project.tasks.withType(Digest::class.java) { digester ->
                for (hash in digester.hashes) {
                    project.artifacts.add("archives", hash)
                }
            }

            project.tasks.register("sign", Sign::class.java) { signator ->
                signator.group = "Release"
                signator.description = "Creates digital signatures of the release dist packages"
                signator.sign(project.configurations.getByName("distPackages"))
            }

            val sign = project.tasks.withType(Sign::class.java) { signator ->
                for (signature in signator.signatures) {
                    project.artifacts.add("archives", signature)
                }
            }

            val releaseNotesCopy = project.tasks.register("releaseNotes", Copy::class.java) { copy ->
                copy.group = "Release"
                copy.description = "Copies release notes to the dist directory"
                copy.from("${releaseDir}/RELEASE_NOTES.txt")
                copy.into(distDir)
                copy.rename { "RELEASE_NOTES-${artefactVersion.major}.${artefactVersion.minor}.x.txt" }
            }

            project.tasks.getByName("assemble").dependsOn(digest, sign, releaseNotesCopy)
        }

        project.tasks.register("showDistArtefacts") {
            it.group = "Release"
            it.description = "Shows release artifacts and dist packages"
            it.doLast {
                println("-----")
                println("Distribution ${pom.name} ${pom.version}")
                println("Repository tag ${pom.scm?.tag}")
                println("Repository URL ${pom.scm?.uri}")
                println("-----")
                println("Binary artifacts: ")
                val resolvedArtifacts = hc.resolvedConfiguration.resolvedArtifacts
                for (artifact in resolvedArtifacts) {
                    println("- ${artifact.file}")
                }
                val archives = project.configurations.getByName("archives")
                println("-----")
                println("Dist artifacts: ")
                for (artifact in archives.allArtifacts) {
                    if (artifact.file.exists()) {
                        println("- ${artifact.file}")
                    }
                }
                println("-----")
            }
        }

        if (distStagingDir != null) {

            project.tasks.register("prepareDistStaging") {
                it.group = "Release"
                it.description = "Prepares dist staging SVN directory"
                it.doLast {
                    println("-----")
                    println("Dist staging location: ${distStagingDir}")
                    val svn = Svn()
                    if (Files.exists(distStagingDir)) {
                        svn.update(distStagingDir)
                    } else {
                        svn.checkout(URI("${HC_DIST_URI}/dev/httpcomponents"), distStagingDir)
                    }
                }
            }

            project.tasks.register("deleteRCDist") {
                it.group = "Release"
                it.description = "Deletes RC dist packages from the remote staging location"
                it.doLast {
                    if (!release) {
                        throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                    }
                    val releaseTag = repoGit(releaseDir) { git ->
                        val describeCommand = git.describe()
                        describeCommand.call()
                    } ?: throw ReleaseException("Release has not been tagged")
                    val rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${releaseTag}"
                    val rcDistStagingDir = distStagingDir.resolve(rcFullName)
                    if (Files.exists(rcDistStagingDir)) {
                        val svn = Svn()
                        val localInfo = svn.info(rcDistStagingDir)
                        val rcDistURI = URI("${localInfo.repositoryRootUrl}/dev/httpcomponents/${rcFullName}")
                        val remoteInfo = svn.infoRemote(rcDistURI)
                        if (remoteInfo != null) {
                            svn.deleteRemote(rcDistURI, "Deleted ${productName} ${releaseTag} dist")
                            svn.update(rcDistStagingDir)
                            println("Deleted ${rcDistURI}")
                        }
                    } else {
                        println("RC dist staging location does not exist: ${rcDistStagingDir}")
                    }
                }
            }

            project.tasks.register("stageRCDist") {
                it.group = "Release"
                it.description = "Commits RC dist packages to the remote staging location"
                it.dependsOn(project.tasks.getByPath("prepareDistStaging"))
                it.doLast {
                    if (!release) {
                        throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                    }
                    val rcTag = repoGit(releaseDir) { git ->
                        val describeCommand = git.describe()
                        describeCommand.call()
                    } ?: throw ReleaseException("Release has not been tagged")

                    val rc = PomArtifact(pom.groupId, artefactId, rcTag)

                    if (artefactVersion.major != rc.major ||
                            artefactVersion.minor != rc.minor ||
                            artefactVersion.phase != rc.phase ||
                            artefactVersion.patch != rc.patch) {
                        throw ReleaseException("Inconsistent POM and RC tag versions: POM = ${artefactVersion}; RC tag = ${rcTag}")
                    }

                    val rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${rcTag}"
                    val rcDistStagingDir = distStagingDir.resolve(rcFullName)

                    project.copy { copySpec ->
                        copySpec.from(distDir)
                        copySpec.into(rcDistStagingDir)
                    }

                    println("Committing ${productName} ${artefactVersion} ${rc.qualifier} dist")

                    val svn = Svn()
                    svn.scheduleForAddition(distStagingDir)
                    val revision = svn.commit(distStagingDir, "${productName} ${artefactVersion} ${rc.qualifier} dist")
                    println("Committed as r${revision}")
                }
            }

            project.tasks.register("prepareVote") { it ->
                it.group = "Release"
                it.description = "Generates release vote message content"
                it.doLast {
                    if (!release) {
                        throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                    }
                    val rcTag = repoGit(releaseDir) { git ->
                        git.getAllTags().setStartingWith("${artefactVersion}-RC").call().firstOrNull()
                                ?: throw ReleaseException("No RC tag found for ${artefactId} ${artefactVersion}")
                    }
                    val rc = PomArtifact(pom.groupId, artefactId, rcTag)
                    if (artefactVersion.major != rc.major ||
                            artefactVersion.minor != rc.minor ||
                            artefactVersion.phase != rc.phase ||
                            artefactVersion.patch != rc.patch) {
                        throw ReleaseException("Inconsistent POM and RC tag versions: POM = ${artefactVersion}; RC tag = ${rcTag}")
                    }
                    val rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${rcTag}"
                    val productPath = productName.toLowerCase(Locale.ROOT)
                    val rcDistStagingDir = distStagingDir.resolve(rcFullName)
                    if (Files.notExists(rcDistStagingDir)) {
                        throw ReleaseException("RC dist ${rcDistStagingDir} does not exist")
                    }
                    val svn = Svn()
                    val svnInfo = svn.info(rcDistStagingDir)
                    val repoURL = svnInfo.url
                    val distRevision = svnInfo.lastChangedRevision

                    println("----------------8<-------------[ cut here ]------------------")
                    println("[VOTE] Release ${productName} ${artefactVersion} based on ${rc.qualifier}")
                    println()
                    println("Please vote on releasing these packages as ${productName} ${artefactVersion}.")
                    println("The vote is open for the at least 72 hours, and only votes from")
                    println("HttpComponents PMC members are binding. The vote passes if at least")
                    println("three binding +1 votes are cast and there are more +1 than -1 votes.")
                    println()
                    println("Release notes:")
                    println(" ${repoURL}/RELEASE_NOTES-${artefactVersion.major}.${artefactVersion.minor}.x.txt")
                    println()
                    println("Maven artefacts:")
                    println(" [link]")
                    println()
                    println("Git Tag: ${rcTag}")
                    println(" ${pom.scm?.uriPattern?.replace("\${project.scm.tag}", rcTag)}")
                    println()
                    println("Packages:")
                    println(" ${repoURL}")
                    println(" revision ${distRevision}")
                    println()
                    println("Hashes:")
                    val distPattern = Regex("^.*\\.(zip|tar\\.gz)$")
                    Files.newDirectoryStream(rcDistStagingDir).use {
                        it.filter { path ->
                            val filename = path.fileName.toString();
                            distPattern.matches(filename) && filename.startsWith("${packageName}-${artefactVersion}")
                        }.forEach {path ->
                            val hash = path.parent.resolve(path.fileName.toString() + ".sha512")
                            if (Files.exists(hash)) {
                                hash.toFile().bufferedReader().use { reader ->
                                    println(" ${reader.readLine()} ${path.fileName}")
                                }
                            }
                        }
                    }
                    println()
                    println("Keys:")
                    println(" https://www.apache.org/dist/httpcomponents/${productPath}/KEYS")
                    println()
                    println("--------------------------------------------------------------------------")
                    println("Vote: ${productName} ${artefactVersion} release")
                    println("[ ] +1 Release the packages as ${productName} ${artefactVersion}.")
                    println("[ ] -1 I am against releasing the packages (must include a reason).")
                    println("----------------8<-------------[ cut here ]------------------")
                }
            }

            project.tasks.register("svnmucc") { it ->
                it.group = "Release"
                it.description = "Generates svnmucc command file to release dist packages"
                it.doLast {
                    if (!release) {
                        throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                    }
                    val rcTag = repoGit(releaseDir) { git ->
                        git.getAllTags().setStartingWith("${artefactVersion}-RC").call().firstOrNull()
                                ?: throw ReleaseException("No RC tag found for ${artefactId} ${artefactVersion}")
                    }
                    val rc = PomArtifact(pom.groupId, artefactId, rcTag)
                    if (artefactVersion.major != rc.major ||
                            artefactVersion.minor != rc.minor ||
                            artefactVersion.phase != rc.phase ||
                            artefactVersion.patch != rc.patch) {
                        throw ReleaseException("Inconsistent POM and RC tag versions: POM = ${artefactVersion}; RC tag = ${rcTag}")
                    }
                    val rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${rcTag}"
                    val productPath = productName.toLowerCase(Locale.ROOT)
                    val rcDistStagingDir = distStagingDir.resolve(rcFullName)
                    if (Files.notExists(rcDistStagingDir)) {
                        throw ReleaseException("RC dist ${rcDistStagingDir} does not exist")
                    }
                    val releaseNotes = "RELEASE_NOTES-${artefactVersion.major}.${artefactVersion.minor}.x.txt"

                    val svn = Svn()
                    val releaseNotesExist = svn.exists(URI("${HC_DIST_URI}/release/httpcomponents/${productPath}/${releaseNotes}"));

                    println("svnmucc file")
                    println("----------------8<-------------[ cut here ]------------------")
                    if (releaseNotesExist) {
                        println("rm")
                        println("release/httpcomponents/${productPath}/${releaseNotes}")
                        println()
                    }
                    val prefix = "${packageName}-${artefactVersion}-"
                    Files.newDirectoryStream(rcDistStagingDir).use {
                        it.forEach { path ->
                            val filename = path.fileName
                            val classifier = filename.toString().removePrefix(prefix)
                            println("mv")
                            println("dev/httpcomponents/${rcFullName}/${filename}")
                            when {
                                classifier.startsWith("src.") -> {
                                    println("release/httpcomponents/${productPath}/source/${filename}")
                                }
                                classifier.startsWith("bin.") -> {
                                    println("release/httpcomponents/${productPath}/binary/${filename}")
                                }
                                else -> {
                                    println("release/httpcomponents/${productPath}/${filename}")
                                }
                            }
                            println()
                        }
                    }
                    println("rm")
                    println("dev/httpcomponents/${rcFullName}")
                    println("----------------8<-------------[ cut here ]------------------")
                }
            }

            project.tasks.register("promoteDist") { it ->
                it.group = "Release"
                it.description = "Promotes dist packages to the release location"
                it.doLast {
                    if (!release) {
                        throw ReleaseException("Unexpected version in POM: ${artefactVersion}; non-SNAPSHOT expected")
                    }
                    val rcTag = repoGit(releaseDir) { git ->
                        git.getAllTags().setStartingWith("${artefactVersion}-RC").call().firstOrNull()
                                ?: throw ReleaseException("No RC tag found for ${artefactId} ${artefactVersion}")
                    }
                    val rc = PomArtifact(pom.groupId, artefactId, rcTag)
                    if (artefactVersion.major != rc.major ||
                            artefactVersion.minor != rc.minor ||
                            artefactVersion.phase != rc.phase ||
                            artefactVersion.patch != rc.patch) {
                        throw ReleaseException("Inconsistent POM and RC tag versions: POM = ${artefactVersion}; RC tag = ${rcTag}")
                    }
                    val rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${rcTag}"
                    val productPath = productName.toLowerCase(Locale.ROOT)
                    val rcDistStagingDir = distStagingDir.resolve(rcFullName)
                    if (Files.notExists(rcDistStagingDir)) {
                        throw ReleaseException("RC dist ${rcDistStagingDir} does not exist")
                    }

                    val releaseNotes = "RELEASE_NOTES-${artefactVersion.major}.${artefactVersion.minor}.x.txt"

                    val svn = Svn()
                    val releaseNotesExist = svn.exists(URI("${HC_DIST_URI}/release/httpcomponents/${productPath}/${releaseNotes}"));

                    val svnInfo = svn.info(rcDistStagingDir)
                    val rcLocation = svnInfo.url.toString().removePrefix("$HC_DIST_URI/")

                    val bulkOps = mutableListOf<SvnBulkOp>()

                    if (releaseNotesExist) {
                        bulkOps.add(SvnRmOp(Paths.get("release/httpcomponents/${productPath}/${releaseNotes}")))
                    }

                    val prefix = "${packageName}-${pom.version}-"
                    Files.newDirectoryStream(rcDistStagingDir).use {
                        it.forEach { path ->
                            val filename = path.fileName
                            val classifier = filename.toString().removePrefix(prefix)
                            val src = Paths.get("${rcLocation}/${filename}")
                            val dst = when {
                                classifier.startsWith("src.") -> {
                                    Paths.get("release/httpcomponents/${productPath}/source/${filename}")
                                }
                                classifier.startsWith("bin.") -> {
                                    Paths.get("release/httpcomponents/${productPath}/binary/${filename}")
                                }
                                else -> {
                                    Paths.get("release/httpcomponents/${productPath}/${filename}")
                                }
                            }
                            bulkOps.add(SvnCpFileOp(dst, src))
                            bulkOps.add(SvnRmOp(src))
                        }
                    }
                    bulkOps.add(SvnRmOp(Paths.get(rcLocation)))
                    println("Promoting ${productName} ${artefactVersion} dist")

                    val revision = svn.mucc(URI(HC_DIST_URI), bulkOps, "${productName} ${artefactVersion} release dist")
                    println("Committed as r${revision}")

                    project.delete(rcDistStagingDir)
                    project.delete(distDir)
                }

            }

        }

        val siteContent: (Path) -> CopySpec = { dir ->
            project.copySpec { copySpec ->
                copySpec.from("${dir}/target/site") {
                    it.exclude("**/*.html")
                }
                copySpec.from("${dir}/target/site") {
                    it.include("**/*.html")
                    it.filter(FixCrLfFilter::class.java)
                }
            }
        }

        project.tasks.register("stageSite") {
            it.group = "Release"
            it.description = "Stages project website content"
            it.doLast {
                val stagingDir = project.buildDir.toPath().resolve("${packageName}-${artefactVersion.major}.${artefactVersion.minor}-site")
                if (!Files.exists(stagingDir)) {
                    Files.createDirectory(stagingDir)
                } else {
                    deleteContent(stagingDir)
                }
                println("Staging project website content of ${productName} ${artefactVersion} to ${stagingDir}")
                project.copy { copy ->
                    copy.into(stagingDir)
                    copy.with(siteContent(releaseDir))
                }
                for (module in pom.modules) {
                    project.copy { copy ->
                        copy.into(stagingDir.resolve(module))
                        copy.with(siteContent(releaseDir.resolve(module)))
                    }
                }
            }
        }

        project.tasks.register("deploySite") { it ->
            it.group = "Release"
            it.description = "Deploys project website content"
            it.doLast {

                val siteUrl = pom.distributionManagement?.site?.url?:
                    throw ReleaseException("Site URL not specified in POM distributionManagement")
                val svnUri = URI.create(if (siteUrl.startsWith(SCM_SVN)) siteUrl.substring(SCM_SVN.length) else siteUrl)

                if (!svnUri.scheme.equals("https", true) || !svnUri.isAbsolute || svnUri.path.isEmpty()) {
                    throw ReleaseException("${svnUri} is not a valid deployment target")
                }
                val pathSegments = svnUri.rawPath.split("/").filter { segment -> segment.isNotBlank() }
                val targetName = pathSegments.last()
                val baseUri = URI.create("https://${svnUri.rawAuthority}/${pathSegments.subList(0, pathSegments.size - 1).joinToString("/")}")
                val stagingDir = project.buildDir.toPath().resolve("${packageName}-${artefactVersion.major}.${artefactVersion.minor}-site-checkout")

                println("Checking out project website content from ${baseUri} to ${stagingDir}")

                val svn = Svn()
                if (Files.exists(stagingDir)) {
                    svn.cleanup(stagingDir)
                    svn.update(stagingDir)
                } else {
                    svn.checkout(baseUri, stagingDir)
                }

                val targetDir = stagingDir.resolve(targetName)
                println("Staging project website content of ${productName} ${artefactVersion} to ${targetDir}")

                if (Files.exists(targetDir)) {
                    deleteContent(targetDir)
                } else {
                    Files.createDirectory(targetDir)
                }

                project.copy { copy ->
                    copy.into(targetDir)
                    copy.with(siteContent(releaseDir))
                }
                for (module in pom.modules) {
                    project.copy { copy ->
                        copy.into(targetDir.resolve(module))
                        copy.with(siteContent(releaseDir.resolve(module)))
                    }
                }
                svn.scheduleForAddition(targetDir)

                val revision = svn.commit(stagingDir, "${productName} ${artefactVersion} project content")
                println("Committed as r${revision}")
            }
        }

        if (release) {

            project.tasks.register("promoteSite") { it ->
                it.group = "Release"
                it.description = "Promotes project website content"
                it.doLast {
                    val siteUrl = pom.distributionManagement?.site?.url?:
                    throw ReleaseException("Site URL not specified in POM distributionManagement")
                    val svnUri = URI.create(if (siteUrl.startsWith(SCM_SVN)) siteUrl.substring(SCM_SVN.length) else siteUrl)

                    if (!svnUri.scheme.equals("https", true) || !svnUri.isAbsolute || svnUri.path.isEmpty()) {
                        throw ReleaseException("${svnUri} is not a valid deployment target")
                    }
                    val pathSegments = svnUri.rawPath.split("/").filter { segment -> segment.isNotBlank() }
                    val targetUri = URI.create("https://${svnUri.rawAuthority}/${pathSegments.subList(0, pathSegments.size - 1).joinToString("/")}/${artefactVersion}")

                    println("Moving project website content of ${productName} ${artefactVersion} to ${targetUri}")

                    val svn = Svn()
                    val revision = svn.moveRemote(svnUri, targetUri, "${productName} ${artefactVersion} release")
                    println("Committed as r${revision}")
                }
            }

        }

    }

}
