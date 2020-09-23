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

import org.assertj.core.api.Assertions
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.junit.jupiter.api.Test
import java.io.StringWriter

class PomTest {

    @Test
    fun `artefact version equality`() {
        Assertions.assertThat(ArtefactVersion.of(12)).isEqualTo(ArtefactVersion.of(12))
        Assertions.assertThat(ArtefactVersion.of(12)).isNotEqualTo(ArtefactVersion.of(12, 0, 0, null))
        Assertions.assertThat(ArtefactVersion.of(12)).isNotEqualTo(ArtefactVersion.of(12, 0))
        Assertions.assertThat(ArtefactVersion.of(12, 1, 2, "SNAPSHOT")).isEqualTo(ArtefactVersion.of(12, 1, 2, "SnapShot"))
        Assertions.assertThat(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 1)).isEqualTo(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 1))
        Assertions.assertThat(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 1)).isNotEqualTo(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 2))
        Assertions.assertThat(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 1)).isNotEqualTo(ArtefactVersion.of(12, 5, DevPhase.BETA, 1))
        Assertions.assertThat(ArtefactVersion.of(12, 5, DevPhase.ALPHA, 1)).isNotEqualTo(ArtefactVersion.of(12, 5, 1))
    }

    @Test
    fun `artefact version parsing`() {
        Assertions.assertThat(ArtefactVersion.parse("12")).isEqualTo(ArtefactVersion.of(12))
        Assertions.assertThat(ArtefactVersion.parse("12.134")).isEqualTo(ArtefactVersion.of(12, 134))
        Assertions.assertThat(ArtefactVersion.parse("0.0.12")).isEqualTo(ArtefactVersion.of(0,0, 12))
        Assertions.assertThat(ArtefactVersion.parse("12.134.34")).isEqualTo(ArtefactVersion.of(12, 134, 34))
        Assertions.assertThat(ArtefactVersion.parse("12-blah")).isEqualTo(ArtefactVersion.of(12, "blah"))
        Assertions.assertThat(ArtefactVersion.parse("12.0.0-blah-blah")).isEqualTo(ArtefactVersion.of(12, 0, 0, "blah-blah"))
        Assertions.assertThat(ArtefactVersion.parse("blah-blah")).isEqualTo(ArtefactVersion.of("blah-blah"))
        Assertions.assertThat(ArtefactVersion.parse("12.45-alpha67-SNAPSHOT")).isEqualTo(ArtefactVersion.of(12, 45, DevPhase.ALPHA, 67, "SNAPSHOT"))
        Assertions.assertThat(ArtefactVersion.parse("1.2-Beta2-SnapShot")).isEqualTo(ArtefactVersion.of(1, 2, DevPhase.BETA, 2, "SNAPSHOT"))
        Assertions.assertThat(ArtefactVersion.parse("5.1-BETA2")).isEqualTo(ArtefactVersion.of(5, 1, DevPhase.BETA, 2))
    }

    @Test
    fun `artefact version as string`() {
        Assertions.assertThat(ArtefactVersion.of(12).toString()).isEqualTo("12")
        Assertions.assertThat(ArtefactVersion.of(12, 134).toString()).isEqualTo("12.134")
        Assertions.assertThat(ArtefactVersion.of(12, 0, 23).toString()).isEqualTo("12.0.23")
        Assertions.assertThat(ArtefactVersion.of(12, 1, "blah").toString()).isEqualTo("12.1-blah")
        Assertions.assertThat(ArtefactVersion.of("blah").toString()).isEqualTo("blah")
        Assertions.assertThat(ArtefactVersion.of(1, 2, DevPhase.ALPHA, 3).toString()).isEqualTo("1.2-alpha3")
        Assertions.assertThat(ArtefactVersion.of(123, 56, DevPhase.BETA, 367).toString()).isEqualTo("123.56-beta367")
        Assertions.assertThat(ArtefactVersion.of(1, 2, DevPhase.ALPHA, 3, "blah").toString()).isEqualTo("1.2-alpha3-blah")
    }

    @Test
    fun `read and write POM with DOM4J`() {
        val parser = SAXReader()
        val resource = javaClass.getResource("/test.bin")
        val document = parser.read(resource)

        val outputFormat = OutputFormat()
        val buf = StringWriter()
        val xmlWriter = XMLWriter(buf, outputFormat)
        xmlWriter.write(document)
        xmlWriter.flush()
        Assertions.assertThat(buf.toString()).isEqualTo(Assertions.contentOf(resource))
    }

    @Test
    fun `read top level Pom`() {
        val resource = javaClass.getResource("/test.bin")
        val pomTool = PomTool()
        val pom = pomTool.digest(resource)
        Assertions.assertThat(pom).isNotNull

        Assertions.assertThat(pom.name).isEqualTo("Stuff")

        Assertions.assertThat(pom.artefact?.groupId).isEqualTo("com.github.ok2.stuff")
        Assertions.assertThat(pom.artefact?.id).isEqualTo("stuff")
        Assertions.assertThat(pom.artefact).isNotNull
        Assertions.assertThat(pom.artefact?.version).isEqualTo("1.2.3-SNAPSHOT")
        Assertions.assertThat(pom.artefact?.major).isEqualTo(1)
        Assertions.assertThat(pom.artefact?.minor).isEqualTo(2)
        Assertions.assertThat(pom.artefact?.patch).isEqualTo(3)
        Assertions.assertThat(pom.artefact?.qualifier).isEqualTo("SNAPSHOT")

        Assertions.assertThat(pom.parent?.groupId).isEqualTo("com.github.ok2")
        Assertions.assertThat(pom.parent?.id).isEqualTo("ok2c-parent")
        Assertions.assertThat(pom.parent).isNotNull
        Assertions.assertThat(pom.parent?.version).isEqualTo("25")
        Assertions.assertThat(pom.parent?.major).isEqualTo(25)
        Assertions.assertThat(pom.parent?.minor).isNull()
        Assertions.assertThat(pom.parent?.patch).isNull()
        Assertions.assertThat(pom.parent?.qualifier).isNull()

        Assertions.assertThat(pom.scm).isNotNull
        Assertions.assertThat(pom.scm?.connection).isEqualTo("scm:git:https://somehost/repos/stuff.git")
        Assertions.assertThat(pom.scm?.uriPattern).isEqualTo("https://somehost/stuff/tree/\${project.scm.tag}")
        Assertions.assertThat(pom.scm?.tag).isEqualTo("master")

        Assertions.assertThat(pom.modules).containsExactly("module1", "module2", "module3")

        Assertions.assertThat(pom.groupId).isEqualTo("com.github.ok2.stuff")
        Assertions.assertThat(pom.artefactId).isEqualTo("stuff")
        Assertions.assertThat(pom.version).isEqualTo(ArtefactVersion.of(1, 2, 3, "SNAPSHOT"))
    }

    @Test
    fun `read module Pom`() {
        val resource = javaClass.getResource("/test2.bin")
        val pomTool = PomTool()
        val pom = pomTool.digest(resource)
        Assertions.assertThat(pom).isNotNull

        Assertions.assertThat(pom.name).isEqualTo("Module one")

        Assertions.assertThat(pom.artefact).isNotNull
        Assertions.assertThat(pom.artefact?.groupId).isNull()
        Assertions.assertThat(pom.artefact?.id).isEqualTo("stuff-module1")
        Assertions.assertThat(pom.artefact?.version).isNull()


        Assertions.assertThat(pom.parent).isNotNull
        Assertions.assertThat(pom.parent?.groupId).isEqualTo("com.github.ok2.stuff")
        Assertions.assertThat(pom.parent?.id).isEqualTo("stuff")
        Assertions.assertThat(pom.parent?.version).isEqualTo("1.2.3-SNAPSHOT")

        Assertions.assertThat(pom.scm).isNull()

        Assertions.assertThat(pom.modules).isEmpty()

        Assertions.assertThat(pom.groupId).isEqualTo("com.github.ok2.stuff")
        Assertions.assertThat(pom.artefactId).isEqualTo("stuff-module1")
        Assertions.assertThat(pom.version).isEqualTo(ArtefactVersion.of(1, 2, 3, "SNAPSHOT"))
    }

    @Test
    fun `rewrite version of top level Pom`() {
        val resource1 = javaClass.getResource("/test.bin")
        val pomTool = PomTool()
        val buf = StringWriter()
        pomTool.updatePomVersion(resource1, "1.2.0-GA", buf)

        val resource2 = javaClass.getResource("/test3.bin")
        Assertions.assertThat(buf.toString()).isEqualTo(Assertions.contentOf(resource2))
    }

    @Test
    fun `rewrite version of module Pom`() {
        val resource1 = javaClass.getResource("/test2.bin")
        val pomTool = PomTool()
        val buf = StringWriter()
        pomTool.updatePomVersion(resource1, "1.2.0-GA", buf)

        val resource2 = javaClass.getResource("/test4.bin")
        Assertions.assertThat(buf.toString()).isEqualTo(Assertions.contentOf(resource2))
    }

    @Test
    fun `determine release version`() {
        val pomTool = PomTool()
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1))).isEqualTo(ArtefactVersion.of(1))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, 2, 3))).isEqualTo(ArtefactVersion.of(1, 2, 3))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, 0, 0))).isEqualTo(ArtefactVersion.of(1, 0, 0))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(1))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, 2, 3, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(1, 2, 3))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, "STUFF"))).isEqualTo(ArtefactVersion.of(1, "STUFF"))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, "STUFF-SNAPSHOT"))).isEqualTo(ArtefactVersion.of(1, "STUFF"))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, "Stuff-SnapShot"))).isEqualTo(ArtefactVersion.of(1, "Stuff"))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(1, 0, DevPhase.BETA, 1, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(1, 0, DevPhase.BETA, 1))
        Assertions.assertThat(pomTool.determineNextReleaseVersion(
                ArtefactVersion.of(2, 3, DevPhase.ALPHA, 4, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(2, 3, DevPhase.ALPHA, 4))
    }

    @Test
    fun `determine next snapshot version`() {
        val pomTool = PomTool()
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1))).isEqualTo(ArtefactVersion.of(2, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, 2, 3))).isEqualTo(ArtefactVersion.of(1, 2, 4, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, 0, 0))).isEqualTo(ArtefactVersion.of(1, 0, 1, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(2, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, 2, 3, "SNAPSHOT"))).isEqualTo(ArtefactVersion.of(1, 2, 4, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, "STUFF"))).isEqualTo(ArtefactVersion.of(2, "STUFF-SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, "STUFF-SNAPSHOT"))).isEqualTo(ArtefactVersion.of(2, "STUFF-SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, "Stuff-Snapshot"))).isEqualTo(ArtefactVersion.of(2, "Stuff-SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(1, 0, DevPhase.BETA, 1))).isEqualTo(ArtefactVersion.of(1, 0, DevPhase.BETA, 2, "SNAPSHOT"))
        Assertions.assertThat(pomTool.determineNextSnapshotVersion(
                ArtefactVersion.of(2, 3, DevPhase.ALPHA, 4))).isEqualTo(ArtefactVersion.of(2, 3, DevPhase.ALPHA, 5, "SNAPSHOT"))
    }

}
