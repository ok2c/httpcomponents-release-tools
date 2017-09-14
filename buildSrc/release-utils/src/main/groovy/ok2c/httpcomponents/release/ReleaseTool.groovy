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

package ok2c.httpcomponents.release

import ok2c.httpcomponents.release.pom.Pom
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryCache
import org.eclipse.jgit.util.FS

class ReleaseTool {

    private final File dir
    private final Repository repository
    private final Git git

    ReleaseTool(File dir) {
        this.dir = dir;
        this.repository = RepositoryCache.open(RepositoryCache.FileKey.lenient(dir, FS.DETECTED), true)
        this.git = new Git(this.repository)
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

        def tagList = git.tagList()
        def refs = tagList.call()
        def releaseTagRef = "refs/tags/${releaseVer}-RC"
        def lastRC = refs
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
        ReleaseSupport.rewritePom(dir, releaseVer)

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

    void cancelRelease() {
        def pom = Pom.parsePom(dir)

        def artifactId = pom.artifactId
        def name = getProductName(artifactId)

        def qualifier = pom.qualifier
        if (qualifier) {
            println "Unexpected version: ${pom.version}"
            return
        }
        def releaseVer = pom.version

        println "Cancelling ${name} ${releaseVer} release"

        def snapshotVer = releaseVer + '-SNAPSHOT'

        ReleaseSupport.rewritePom(dir, snapshotVer)

        println "Committing changes for snapshot ${name} ${snapshotVer}"
        git.commit()
                .setAll(true)
                .setMessage("Cancelled ${name} ${releaseVer} release")
                .call()
    }

    void makeNextSnapshot() {
        def pom = Pom.parsePom(dir)

        def artifactId = pom.artifactId
        def name = getProductName(artifactId)

        def currentVer = pom.version
        def snapshotVer = ReleaseSupport.upgradeVersion(currentVer)

        println "Upgrading ${name} from ${currentVer} to ${snapshotVer}"

        ReleaseSupport.rewritePom(dir, snapshotVer)

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
                .setName(releaseVer)
                .setAnnotated(true)
                .setMessage("${name} ${releaseVer} release tag")
                .call()
        println "${releaseVer} tag created"
    }

    private static def PROJECT_NAME_MAP = [
            'httpcore5-parent':'HttpCore',
            'httpclient5-parent':'HttpClient',
            'httpcomponents-core':'HttpCore',
            'httpcomponents-client':'HttpClient',
            'httpcomponents-asyncclient':'HttpAsyncClient'
    ]

    static def getProductName(String artifactId) {
        String s = PROJECT_NAME_MAP[artifactId]
        s ? s : artifactId
    }

}
