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

class DigestHash extends AbstractPublishArtifact  {

    final PublishArtifact toDigestArtifact

    DigestHash(PublishArtifact toDigest, Object... tasks) {
        super(tasks)
        this.toDigestArtifact = toDigest
    }

    @Override
    String getName() {
        toDigestArtifact.name
    }

    @Override
    String getExtension() {
        'md5'
    }

    @Override
    String getType() {
        'md5'
    }

    @Override
    String getClassifier() {
        toDigestArtifact.classifier
    }

    @Override
    File getFile() {
        new File(toDigestArtifact.file.path + ".${getExtension()}")
    }

    @Override
    Date getDate() {
        toDigestArtifact.date
    }

}