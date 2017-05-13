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

import org.gradle.api.DefaultTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

class Digest extends DefaultTask {

    private final DefaultDomainObjectSet<DigestHash> hashes = new DefaultDomainObjectSet<DigestHash>(DigestHash)

    void digest(PublishArtifact... artifacts) {
        for (PublishArtifact artifact in artifacts) {
            dependsOn(artifact)
            hashes.add(new DigestHash(artifact))
        }
    }

    void digest(Configuration... configurations) {
        for (Configuration configuration in configurations) {
            configuration.allArtifacts.all { PublishArtifact artifact ->
                digest(artifact)
            }
            configuration.allArtifacts.whenObjectRemoved { DigestHash artifact ->
                hashes.remove(hashes.find { it.toDigestArtifact == artifact })
            }
        }
    }

    DomainObjectSet<DigestHash> getHashes() {
        hashes
    }

    @InputFiles
    List<File> getSourceFiles() {
        hashes*.toDigestArtifact.file
    }

    @OutputFiles
    List<File> getDigestFiles() {
        hashes*.file
    }

    @TaskAction
    void generate() {
        hashes.each { DigestHash artifact ->
            File digest = artifact.file
            digest.text = MD5.digest(artifact.toDigestArtifact.file)
            digest
        }
    }

}