/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.ok2.hc.release

import com.github.ok2.hc.release.pom.Pom
import com.github.ok2.hc.release.pom.PomArtifact
import com.github.ok2.hc.release.svn.Svn
import com.github.ok2.hc.release.svn.SvnBulkOp
import com.github.ok2.hc.release.svn.SvnCpFile
import com.github.ok2.hc.release.svn.SvnRm
import org.apache.maven.artifact.versioning.ArtifactVersion
import org.apache.maven.artifact.versioning.DefaultArtifactVersion
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.util.FS
import org.jdom2.Comment
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.filter.ContentFilter
import org.jdom2.input.SAXBuilder
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter

import java.util.regex.Matcher

class ReleaseTool {

    private final File dir
    private final File stagingDir
    private final Repository repository
    private final Git git

    ReleaseTool(File dir, File stagingDir) {
        this.dir = dir
        this.stagingDir = stagingDir
        this.repository = RepositoryCache.open(RepositoryCache.FileKey.lenient(dir, FS.DETECTED), true)
        this.git = new Git(this.repository)
    }

    File getDir() {
        return dir
    }

    File getStagingDir() {
        return stagingDir
    }

    Repository getRepository() {
        return repository
    }

    Pom parsePom() {
        return Pom.parsePom(dir)
    }

    private static def PROJECT_NAME_MAP = [
            'httpcore5-parent':'HttpCore',
            'httpclient5-parent':'HttpClient',
            'httpcomponents-core':'HttpCore',
            'httpcomponents-client':'HttpClient',
            'httpcomponents-asyncclient':'HttpAsyncClient'
    ]

    def getProductName(String artifactId) {
        String s = PROJECT_NAME_MAP[artifactId]
        s ? s : artifactId
    }

    private static def PACKAGE_NAME_MAP = [
            'httpcore5-parent':'httpcomponents-core',
            'httpclient5-parent':'httpcomponents-client'
    ]

    def getPackageName(String artifactId) {
        String s = PACKAGE_NAME_MAP[artifactId]
        s ? s : artifactId
    }

    void showStatus() {
        def pom = Pom.parsePom(dir)
        println "Project directory ${dir}"
        println "Git branch ${repository.branch}"
        def describeCommand = git.describe()
        def tag = describeCommand.call()
        if (tag) {
            println "Release tag ${tag}"
        } else {
            println "No tag"
        }
        println ""
        println "${pom.name} ${pom.version}"
        pom.modules.forEach { subpom ->
            println "- module ${subpom.name}"
        }
    }

    String getReleaseTag() {
        git.describe().call()
    }

    void prepareReleaseCandidate() {
        def pom = Pom.parsePom(dir)
        def artifactId = pom.artifactId
        def name = getProductName(artifactId)

        def qualifier = pom.qualifier
        if (!qualifier.endsWith('SNAPSHOT')) {
            println "Unexpected version: ${pom.version}"
            return
        }
        def releaseVer = pom.version - ('-SNAPSHOT')
        println "Please enter release version: [defaults to ${releaseVer}]"

        def console = System.console()
        if (console) {
            def text1 = console.readLine()
            if (text1) {
                releaseVer = text1
            }
        }

        def tagRefs = git.tagList().call()
        def releaseTagRef = "refs/tags/${releaseVer}-RC"
        def lastRC = tagRefs
                .grep { ref -> ref.name.startsWith(releaseTagRef) }
                .collect( { ref -> ref.name.substring(releaseTagRef.length()) as Integer } )
                .max()
        if (!lastRC) {
            lastRC = 0
        }
        def rcQualifier = 'RC' + ++lastRC
        println "Please enter release candidate qualifier: [defaults to ${rcQualifier}]"
        if (console) {
            def text2 = console.readLine()
            if (text2) {
                rcQualifier = text2
            }
        }

        println "Preparing release ${name} ${releaseVer}"
        rewritePom(dir, releaseVer)

        println "Committing changes for release ${name} ${releaseVer}"
        git.commit()
                .setAll(true)
                .setMessage("${name} ${releaseVer} release")
                .call()

        println "Creating tag for ${name} ${releaseVer} ${rcQualifier}"
        def rcVer = releaseVer + '-' + rcQualifier
        git.tag()
                .setName(rcVer)
                .setAnnotated(true)
                .setMessage("${name} ${releaseVer} ${rcQualifier} tag")
                .call()
        println "${rcVer} tag created"
    }

    void makeNextSnapshot() {
        def pom = Pom.parsePom(dir)

        def artifactId = pom.artifactId
        def name = getProductName(artifactId)

        def currentVer = pom.version
        def snapshotVer = upgradeVersion(currentVer)

        println "Upgrading ${name} from ${currentVer} to ${snapshotVer}"

        rewritePom(dir, snapshotVer)

        println "Committing changes for snapshot ${name} ${snapshotVer}"
        git.commit()
                .setAll(true)
                .setMessage("Upgraded ${name} version to ${snapshotVer}")
                .call()
    }

    void promoteReleaseCandidate() {
        def pom = Pom.parsePom(dir)
        def artifactId = pom.artifactId
        def name = getProductName(artifactId)

        def qualifier = pom.qualifier

        if (qualifier && qualifier.endsWith("SNAPSHOT")) {
            println "Unexpected version: ${pom.version}"
            return
        }
        def releaseVer = pom.version

        println "Creating tag for ${name} ${releaseVer} release"
        git.tag()
                .setName("rel/v${releaseVer}")
                .setAnnotated(true)
                .setMessage("${name} ${releaseVer} release tag")
                .call()
        println "${releaseVer} tag created"
    }

    private static DELIM = System.getProperty('separator', '\n')

    static String upgradeVersion(String version) {
        ArtifactVersion ver = new DefaultArtifactVersion(version)
        int major = ver.majorVersion
        int minor = ver.minorVersion
        int patch = ver.incrementalVersion
        String qualifier = ver.qualifier

        if (qualifier) {
            if (qualifier == 'SNAPSHOT') {
                qualifier = null
            } else if (qualifier.endsWith('-SNAPSHOT')) {
                qualifier = qualifier - '-SNAPSHOT'
            }
        }

        if (qualifier) {
            Matcher m = qualifier =~ '(alpha|beta|rc)(\\d)'
            if (m.find()) {
                int n = Integer.parseInt(m.group(2))
                qualifier = m.group(1) + (++n)
            } else {
                patch++
            }
        } else {
            patch++
        }
        StringBuilder buf = new StringBuilder()
        buf.append(major).append('.').append(minor)
        if (patch > 0) {
            buf.append('.').append(patch)
        }
        if (qualifier) {
            buf.append('-').append(qualifier)
        }
        buf.append('-SNAPSHOT')
        buf.toString()
    }

    // Copied from Maven release manager
    static private fixLineDelimitersInComments(Document document) {
        ContentFilter filter = new ContentFilter(ContentFilter.COMMENT)
        for (Iterator it = document.getDescendants(filter); it.hasNext(); ) {
            Comment c = (Comment) it.next();
            c.setText(c.getText().replaceAll('\n', DELIM));
        }
    }

    static void rewritePom(File dir, String version) {
        File pomFile = new File(dir, 'pom.xml')
        SAXBuilder parser = new SAXBuilder()

        Namespace ns = Namespace.getNamespace("http://maven.apache.org/POM/4.0.0")

        Document maindoc = parser.build(pomFile)

        Element rootEl = maindoc.rootElement
        Element versionEl = rootEl.getChild('version', ns)
        if (versionEl) {
            versionEl.text = version
        }

        Element scmEl = rootEl.getChild('scm', ns)
        if (scmEl) {
            Element connectionEl = scmEl.getChild('tag', ns)
            if (connectionEl) {
                connectionEl.text = version
            }
        }

        Format format = Format.getRawFormat()
        format.lineSeparator = DELIM

        Element modulesEl = rootEl.getChild('modules', ns)
        if (modulesEl) {
            List<Element> moduleEls = modulesEl.getChildren('module', ns)
            for (Element moduleEl: moduleEls) {
                File moduleDir = new File(dir, moduleEl.text)
                File modulePomFile = new File(moduleDir, 'pom.xml')
                Document doc = parser.build(modulePomFile)
                Element moduleParentEl = doc.rootElement.getChild('parent', ns)
                if (moduleParentEl) {
                    Element moduleVersionEl = moduleParentEl.getChild('version', ns)
                    if (moduleVersionEl) {
                        moduleVersionEl.text = version
                    }
                }
                fixLineDelimitersInComments(doc)
                XMLOutputter xmlOutputter = new XMLOutputter(format)
                modulePomFile.withWriter('UTF-8') { Writer writer ->
                    xmlOutputter.output(doc, writer)
                }
            }
        }

        fixLineDelimitersInComments(maindoc)
        XMLOutputter xmlOutputter = new XMLOutputter(format)
        pomFile.withWriter('UTF-8') { Writer writer ->
            xmlOutputter.output(maindoc, writer)
        }
    }

    void updateStagingDir() {
        if (stagingDir.exists()) {
            Svn.update(stagingDir)
        }
    }

    void commitDist(Closure closure) {
        def releaseTag = git.describe().call()
        if (!releaseTag) {
            println "Release has not been tagged"
            return
        }
        def pom = Pom.parsePom(dir)
        def rc = new PomArtifact(pom.groupId, pom.artifactId, releaseTag)
        if (pom.major != rc.major || pom.minor != rc.minor || pom.incremental != rc.incremental) {
            println "Inconsistent POM and RC tag versions: POM = ${pom.version}; RC tag = ${releaseTag}"
            return
        }
        def productName = getProductName(pom.artifactId)
        def rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${releaseTag}"
        def rcDistDir = new File(stagingDir, "${rcFullName}")
        def rcQualifier = rc.qualifier
        if (pom.qualifier && rcQualifier.startsWith(pom.qualifier + "-")) {
            rcQualifier = rcQualifier.substring(pom.qualifier.length() + 1)
        }

        closure.call(rcDistDir);

        println "Committing ${productName} ${pom.version} ${rcQualifier} dist"

        Svn.scheduleForAddition(rcDistDir)
        def revision = Svn.commit(rcDistDir, "${productName} ${pom.version} ${rcQualifier} dist")
        println "Committed as r${revision}"
    }

    void deleteDist() {
        def releaseTag = git.describe().call()
        if (!releaseTag) {
            println "Release has not been tagged"
            return
        }
        def pom = Pom.parsePom(dir)
        def productName = getProductName(pom.artifactId)
        def rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${releaseTag}"
        def rcDistDir = new File(stagingDir, "${rcFullName}")
        if (!rcDistDir.exists()) {
            println "RC dist ${rcDistDir} does not exist"
            return
        }
        def localInfo = Svn.info(rcDistDir)
        def repoRootURL = localInfo.repositoryRootUrl as String
        def distUri = URI.create("${repoRootURL}/dev/httpcomponents/${rcFullName}")
        def remoteInfo = Svn.infoRemote(distUri)
        if (remoteInfo) {
            Svn.deleteRemote(distUri, "Deleted ${productName} ${releaseTag} dist")
            println "Deleted ${repoRootURL}/dev/httpcomponents/${rcFullName}"
        }
        Svn.update(stagingDir)
    }

    void prepareVote() {
        def releaseTag = git.describe().call()
        if (!releaseTag) {
            println "Release has not been tagged"
            return
        }
        def pom = Pom.parsePom(dir)
        def releaseVer = pom.version
        def rc = new PomArtifact(pom.groupId, pom.artifactId, releaseTag)
        if (pom.major != rc.major || pom.minor != rc.minor || pom.incremental != rc.incremental) {
            println "Inconsistent POM and RC tag versions: POM = ${pom.version}; RC tag = ${releaseTag}"
            return
        }

        def productName = getProductName(pom.artifactId)
        def rcFullName = "${productName.toLowerCase(Locale.ROOT)}-${releaseTag}"
        def rcQualifier = rc.qualifier
        if (pom.qualifier && rcQualifier.startsWith(pom.qualifier + "-")) {
            rcQualifier = rcQualifier.substring(pom.qualifier.length() + 1)
        }

        def rcDistDir = new File(stagingDir, "${rcFullName}")
        if (!rcDistDir.exists()) {
            println "RC dist ${rcDistDir} does not exist"
            return
        }

        def svnInfo = Svn.info(rcDistDir)
        def repoURL = svnInfo.url
        def distRevision = svnInfo.lastChangedRevision

        println '----------------8<-------------[ cut here ]------------------'
        println "[VOTE] Release ${productName} ${releaseVer} based on ${rcQualifier}"
        println ""
        println "Please vote on releasing these packages as ${productName} ${releaseVer}."
        println "The vote is open for the at least 72 hours, and only votes from"
        println "HttpComponents PMC members are binding. The vote passes if at least"
        println "three binding +1 votes are cast and there are more +1 than -1 votes."
        println ""
        println "Release notes:"
        println " ${repoURL}/RELEASE_NOTES-${pom.major}.${pom.minor}.x.txt"
        println ""
        println "Maven artefacts:"
        println " [link]"
        println ""
        println "Git Tag: ${releaseTag}"
        println " ${pom.scm.uriPattern.replace('${project.scm.tag}', releaseTag)}"
        println ""
        println "Packages:"
        println " ${repoURL}"
        println " revision ${distRevision}"
        println ""
        println "Hashes:"
        rcDistDir.eachFileMatch ~/^.*\.(zip|tar\.gz)$/, { File file ->
            def hash = new File(file.parentFile, file.name + '.sha512')
            if (hash.exists()) {
                println " ${hash.text} ${file.name}"
            }
        }
        println ""
        println "Keys:"
        println " http://www.apache.org/dist/httpcomponents/${productName.toLowerCase(Locale.ROOT)}/KEYS"
        println ""
        println "--------------------------------------------------------------------------"
        println "Vote: ${productName} ${releaseVer} release"
        println "[ ] +1 Release the packages as ${productName} ${releaseVer}."
        println "[ ] -1 I am against releasing the packages (must include a reason)."
        println '----------------8<-------------[ cut here ]------------------'
    }

    void svnmucc() {
        def releaseTag = git.describe().call()
        if (!releaseTag) {
            println "Release has not been tagged"
            return
        }

        def pom = Pom.parsePom(dir)
        def productName = getProductName(pom.artifactId)
        def packageName = getPackageName(pom.artifactId)
        def productPath = productName.toLowerCase(Locale.ROOT)
        def rcFullName = "${productPath}-${releaseTag}"

        def rcDistDir = new File(stagingDir, "${rcFullName}")
        if (!rcDistDir.exists()) {
            println "RC dist ${rcDistDir} does not exist"
            return
        }

        println 'svnmucc file'
        println '----------------8<-------------[ cut here ]------------------'
        println "rm"
        println "release/httpcomponents/${productPath}/RELEASE_NOTES-${pom.major}.${pom.minor}.x.txt"
        println ""
        rcDistDir.eachFile { File file ->
            def prefix = "${packageName}-${pom.version}-"
            def classifier = file.name.startsWith(prefix) ? file.name - prefix : ""
            println "mv"
            println "dev/httpcomponents/${rcFullName}/${file.name}"
            switch(classifier) {
                case ~/^bin\..*$/:
                    println "release/httpcomponents/${productPath}/binary/${file.name}"
                    break
                case ~/^src\..*$/:
                    println "release/httpcomponents/${productPath}/source/${file.name}"
                    break
                default:
                    println "release/httpcomponents/${productPath}/${file.name}"
                    break
            }
            println ""
        }
        println "rm"
        println "dev/httpcomponents/${rcFullName}"
        println '----------------8<-------------[ cut here ]------------------'

    }

    void promoteDist() {
        def headId = repository.resolve(Constants.HEAD);
        def tagRefs = git.tagList().call()
        def rcTagRef = tagRefs.find {ref ->
            def peeledRef = repository.peel(ref)
            peeledRef.peeledObjectId == headId && ref.name =~ /^.*-RC\d+$/
        }

        if (!rcTagRef) {
            println "RC tag does not exist"
            return
        }

        def rcTag = (rcTagRef.name =~ /^refs\/tags\/(.*)$/)[0][1]

        def pom = Pom.parsePom(dir)
        def productName = getProductName(pom.artifactId)
        def packageName = getPackageName(pom.artifactId)
        def productPath = productName.toLowerCase(Locale.ROOT)
        def rcFullName = "${productPath}-${rcTag}"

        def rcDistDir = new File(stagingDir, "${rcFullName}")
        if (!rcDistDir.exists()) {
            println "RC dist ${rcDistDir} does not exist"
            return
        }

        def svnInfo = Svn.info(rcDistDir)
        def repoURL = svnInfo.url as String
        def repoRootURL = svnInfo.repositoryRootUrl as String
        def rcLocation = repoURL - repoRootURL
        if (rcLocation.startsWith('/')) {
            rcLocation = rcLocation.substring(1)
        }

        def bulkOps = []
        bulkOps.add(new SvnRm(new File("release/httpcomponents/${productPath}/RELEASE_NOTES-${pom.major}.${pom.minor}.x.txt")))
        rcDistDir.eachFile { File file ->
            def prefix = "${packageName}-${pom.version}-"
            def classifier = file.name.startsWith(prefix) ? file.name - prefix : ""
            File src = new File("${rcLocation}/${file.name}")
            File dst
            switch (classifier) {
                case ~/^bin\..*$/:
                    dst = new File("release/httpcomponents/${productPath}/binary/${file.name}")
                    break
                case ~/^src\..*$/:
                    dst = new File("release/httpcomponents/${productPath}/source/${file.name}")
                    break
                default:
                    dst = new File("release/httpcomponents/${productPath}/${file.name}")
                    break
            }
            bulkOps.add(new SvnCpFile(dst, src))
            bulkOps.add(new SvnRm(src))
        }
        bulkOps.add(new SvnRm(new File(rcLocation)))

        println "Promoting ${productName} ${pom.version} dist"

        def revision = Svn.mucc(new URI(repoRootURL), bulkOps as List<SvnBulkOp>, "${productName} ${pom.version} release dist")
        println "Committed as r${revision}"

    }

}
