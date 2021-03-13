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
package com.github.ok2c.hc.release.pom

import org.dom4j.Document
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import java.io.Reader
import java.io.Writer
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors
import kotlin.streams.toList

enum class DevPhase(val id: String) {
    ALPHA("alpha"), BETA("beta"), UNSPECIFIED("")
}

class ArtefactVersion internal constructor(val sequence: List<Int>, val phase: DevPhase, val qualifier: String? = null) {

    val major: Int? get() = if (sequence.size > 0) sequence[0] else null
    val minor: Int? get() = if (sequence.size > 1) sequence[1] else null
    val patch: Int? get() = if (sequence.size > 2) sequence[2] else null

    private val normalizedQualifier = qualifier?.toUpperCase(Locale.ROOT)

    companion object {

        fun of(major: Int, qualifier: String? = null): ArtefactVersion {
            return ArtefactVersion(listOf(major), DevPhase.UNSPECIFIED, qualifier)
        }

        fun of(major: Int, minor: Int, qualifier: String? = null): ArtefactVersion {
            return ArtefactVersion(listOf(major, minor), DevPhase.UNSPECIFIED, qualifier)
        }

        fun of(major: Int, minor: Int, patch: Int, qualifier: String? = null): ArtefactVersion {
            return ArtefactVersion(listOf(major, minor, patch), DevPhase.UNSPECIFIED, qualifier)
        }

        fun of(qualifier: String? = null): ArtefactVersion {
            return ArtefactVersion(emptyList(), DevPhase.UNSPECIFIED, qualifier)
        }

        fun of(major: Int, minor: Int, phase: DevPhase, dev: Int, qualifier: String? = null): ArtefactVersion {
            return ArtefactVersion(listOf(major, minor, dev), phase, qualifier)
        }

        fun parse(s: CharSequence): ArtefactVersion {
            val qualifier: CharSequence?
            val s2: CharSequence
            val idx = s.indexOf('-')
            if (idx != -1) {
                s2 = s.subSequence(0, idx)
                qualifier = s.subSequence(idx + 1, s.length)
            } else {
                s2 = s
                qualifier = null
            }
            try {
                val tokens = s2.split('.')
                val versions = tokens.stream().map { token -> Integer.parseInt(token) }.collect(Collectors.toList())
                if (qualifier != null) {
                    val regexp = """^(alpha|beta)(\d+)(-(.*))?$""".toRegex(RegexOption.IGNORE_CASE)
                    val matchResult = regexp.matchEntire(qualifier)
                    if (matchResult != null) {
                        val phaseText = matchResult.groupValues[1]
                        val devPhase = when {
                            phaseText.equals("alpha", true) -> DevPhase.ALPHA
                            phaseText.equals("beta", true) -> DevPhase.BETA
                            else -> null
                        }
                        if (devPhase != null) {
                            versions.add(matchResult.groupValues[2].toInt())
                            val qualifier2 = matchResult.groupValues[4]
                            return ArtefactVersion(versions, devPhase, if (qualifier2.isNotBlank()) qualifier2 else null )
                        }
                    }
                }
                return ArtefactVersion(versions, DevPhase.UNSPECIFIED, qualifier?.toString())
            } catch (ex: NumberFormatException) {
                return ArtefactVersion(emptyList(), DevPhase.UNSPECIFIED, s.toString())
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass == other?.javaClass) {
            other as ArtefactVersion
            return sequence == other.sequence
                    && phase == other.phase
                    && normalizedQualifier.equals(other.normalizedQualifier)
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + phase.hashCode()
        result = 31 * result + (normalizedQualifier?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val buf = StringBuilder()
        for (i in sequence.indices) {
            if (i > 0) {
                if (i == sequence.lastIndex) {
                    when(phase) {
                        DevPhase.ALPHA, DevPhase.BETA -> buf.append("-").append(phase.id)
                        else -> buf.append('.')
                    }
                } else {
                    buf.append('.')
                }
            }
            buf.append(sequence[i])
        }
        if (qualifier != null) {
            if (buf.isNotEmpty()) {
                buf.append('-')
            }
            buf.append(qualifier)
        }
        return buf.toString()
    }

}

class Pom(val name: String?,
          val parent: PomArtifact?,
          val artefact: PomArtifact?,
          val scm: Scm?,
          val distributionManagement: DistributionManagement?,
          val modules: List<String>) {

    val groupId: String? get() = artefact?.groupId ?: parent?.groupId
    val artefactId: String? get() = artefact?.id
    val version: ArtefactVersion? get() = artefact?.parsedVersion ?: parent?.parsedVersion

    override fun toString(): String {
        return "Pom(name='$name', parent=$parent, artefact=$artefact)"
    }

}

class PomArtifact(val groupId: String?, val id: String, val version: String?) {

    internal val parsedVersion = if (version != null) ArtefactVersion.parse(version) else null
    val major: Int? get() = parsedVersion?.major
    val minor: Int? get() = parsedVersion?.minor
    val patch: Int? get() = parsedVersion?.patch
    val phase: DevPhase? get() = parsedVersion?.phase
    val qualifier: String? get() = parsedVersion?.qualifier

    override fun toString(): String {
        return "PomArtifact(groupId='$groupId', id='$id', version='$version')"
    }

}

class Scm(val connection: String?, val tag: String?, val uriPattern: String?) {

    val uri: URI?
        get() {
            return if (!uriPattern.isNullOrBlank() && !tag.isNullOrBlank()) {
                URI.create(uriPattern.replace("\${project.scm.tag}", tag, ignoreCase = true))
            } else {
                null
            }
        }

    override fun toString(): String {
        return "Scm(connection='$connection', tag='$tag', uriPattern='$uriPattern')"
    }

}

class DistributionManagement(val site: Site) {

    override fun toString(): String {
        return "DistributionManagement(site=$site)"
    }

}

class Site(val id: String?, val name: String?, val url: String?) {

    override fun toString(): String {
        return "Site(id='$id', name='$name', url='$url')"
    }

}

class PomTool {

    private fun createPomArtifact(groupId: String?, artifactId: String?, version: String?): PomArtifact? {
        return if (!artifactId.isNullOrBlank()) PomArtifact(groupId, artifactId, version) else null
    }

    private fun read(reader: Reader): Document {
        return SAXReader().read(reader)
    }

    private fun read(pomURL: URL): Document {
        val reader = SAXReader()
        return reader.read(pomURL)
    }

    private fun read(path: Path): Document {
        val reader = SAXReader()
        return reader.read(path.toFile())
    }

    private fun digest(document: Document): Pom {
        val rootElement = document.rootElement
        val parentElement = rootElement.element("parent")
        val name = rootElement.element("name")?.textTrim
        val artefact = createPomArtifact(
                rootElement.element("groupId")?.textTrim,
                rootElement.element("artifactId")?.textTrim,
                rootElement.element("version")?.textTrim)
        val parentArtefact = if (parentElement != null) createPomArtifact(
                parentElement.element("groupId")?.textTrim,
                parentElement.element("artifactId")?.textTrim,
                parentElement.element("version")?.textTrim) else null
        val scmElement = rootElement.element("scm")
        val scm = if (scmElement != null) Scm(
                scmElement.element("connection")?.textTrim,
                scmElement.element("tag")?.textTrim,
                scmElement.element("url")?.textTrim) else null

        val dmElement = rootElement.element("distributionManagement")
        val siteElement = dmElement?.element("site")
        val dm = if (siteElement != null) DistributionManagement(
            Site(siteElement.element("id")?.textTrim,
                siteElement.element("name")?.textTrim,
                siteElement.element("url")?.textTrim)) else null

        val modulesElement = rootElement.element("modules")
        val modules: List<String> = modulesElement?.elements("module")?.stream()?.map { it.textTrim }?.toList()
                ?: emptyList()
        return Pom(name, parentArtefact, artefact, scm, dm, modules)
    }

    fun digest(reader: Reader): Pom {
        return digest(read(reader))
    }

    fun digest(path: Path): Pom {
        return digest(read(path))
    }

    fun digest(url: URL): Pom {
        return digest(read(url))
    }

    private fun updatePomVersion(document: Document, version: String, out: Writer) {
        val rootElement = document.rootElement
        val versionElement = rootElement.element("version")
        if (versionElement != null) {
            versionElement.text = version
        } else {
            rootElement.element("parent")?.element("version")?.text = version
        }
        rootElement.element("scm")?.element("tag")?.text = version

        val outputFormat = OutputFormat()
        val xmlWriter = XMLWriter(out, outputFormat)
        xmlWriter.write(document)
        xmlWriter.flush()
    }

    fun updatePomVersion(reader: Reader, version: String, out: Writer) {
        updatePomVersion(read(reader), version, out)
    }

    fun updatePomVersion(path: Path, version: String, out: Writer) {
        updatePomVersion(read(path), version, out)
    }

    fun updatePomVersion(url: URL, version: String, out: Writer) {
        updatePomVersion(read(url), version, out)
    }

    fun updatePomVersion(path: Path, version: String) {
        val pomFile = path.toFile()
        val reader = SAXReader()
        val document = reader.read(pomFile)
        pomFile.writer().use {
            updatePomVersion(document, version, it)
        }
    }

    private fun nonSnapshotQualifier(version: ArtefactVersion): String? {
        val s = version.qualifier
        return when {
            s.isNullOrBlank() -> null
            s.equals("SNAPSHOT", true) -> null
            s.startsWith("RC", true) -> null
            s.endsWith("-SNAPSHOT", true) -> s.substring(0, s.lastIndex - 8)
            else -> version.qualifier
        }
    }

    fun determineNextReleaseVersion(version: ArtefactVersion): ArtefactVersion {
        val qualifier = nonSnapshotQualifier(version)
        return ArtefactVersion(version.sequence, version.phase, qualifier)
    }

    fun determineNextSnapshotVersion(version: ArtefactVersion): ArtefactVersion {
        val qualifier = nonSnapshotQualifier(version)
        val newSequence = version.sequence.toMutableList()
        if (newSequence.isNotEmpty()) {
            newSequence[newSequence.lastIndex] = newSequence[newSequence.lastIndex] + 1
        }
        return ArtefactVersion(newSequence, version.phase, if (qualifier == null) "SNAPSHOT" else "${qualifier}-SNAPSHOT")
    }

}