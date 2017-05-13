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

package ok2c.httpcomponents.release.pom

class Pom {

    private final String name
    private final PomArtifact parent
    private final PomArtifact artifact
    private final Scm scm
    private final List<PomModule> modules

    Pom(String name, PomArtifact parent, PomArtifact artifact, Scm scm, List<PomModule> modules) {
        this.name = name
        this.parent = parent
        this.artifact = artifact
        this.scm = scm
        this.modules = modules
    }

    String getName() {
        return name
    }

    String getGroupId() {
        if (artifact.groupId) {
            artifact.groupId
        } else {
            parent ? parent.groupId : null
        }
    }

    String getArtifactId() {
        artifact.id
    }

    String getVersion() {
        artifact.version
    }

    int getMajor() {
        artifact.major
    }

    int getMinor() {
        artifact.minor
    }

    int getIncremental() {
        artifact.incremental
    }

    int getBuildNumber() {
        artifact.buildNumber
    }

    String getQualifier() {
        artifact.qualifier
    }

    Scm getScm() {
        return scm
    }

    List<PomModule> getModules() {
        modules
    }

    @Override
    String toString() {
        name
    }

    static Pom parsePom(File dir) {
        def pomFile = new File(dir, 'pom.xml')
        def pomModel = new XmlSlurper().parse(pomFile)
        def parentElement = pomModel['parent']
        def pomParent = parentElement ? new PomArtifact(
                parentElement.groupId.text(),
                parentElement.artifactId.text(),
                parentElement.version.text()) : null
        def artifact = new PomArtifact(
                pomModel.groupId.text(),
                pomModel.artifactId.text(),
                pomModel.version.text())
        def name = pomModel.name.text()
        def scmElement = pomModel['scm']
        def scm = null
        if (scmElement) {
            def connection = scmElement.connection.text()
            def tag = scmElement.tag.text()
            def uriPattern = scmElement.url.text()
            scm = new Scm(connection, tag, uriPattern)
        }

        def modules = []
        pomModel.modules.module.each { module ->

            def modulePomFile = new File(new File(dir, module.text()), 'pom.xml')
            def modulePomModel = new XmlSlurper().parse(modulePomFile)
            def moduleArtifact = new PomArtifact(
                    artifact.groupId ? artifact.groupId : pomParent.groupId,
                    modulePomModel.artifactId.text(),
                    artifact.version)
            def pomModule = new PomModule(module.text(), moduleArtifact)
            modules.add(pomModule)
        }
        new Pom(name, pomParent, artifact, scm, modules as List<PomModule>)
    }

}
