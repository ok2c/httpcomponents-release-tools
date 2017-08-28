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


import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact

final class DigestHash extends AbstractPublishArtifact  {

    private final PublishArtifact digestArtifact
    private final extension
    private final algo

    DigestHash(PublishArtifact toDigest, String extension, String algo, Object... tasks) {
        super(tasks)
        this.digestArtifact = toDigest
        this.extension = extension
        this.algo = algo
    }

    PublishArtifact getDigestArtifact() {
        return digestArtifact;
    }

    @Override
    String getName() {
        digestArtifact.name
    }

    @Override
    String getExtension() {
        extension
    }

    @Override
    String getType() {
        algo
    }

    String getAlgo() {
        algo
    }

    @Override
    String getClassifier() {
        digestArtifact.classifier
    }

    @Override
    File getFile() {
        new File(digestArtifact.file.path + ".${getExtension()}")
    }

    @Override
    Date getDate() {
        digestArtifact.date
    }

}