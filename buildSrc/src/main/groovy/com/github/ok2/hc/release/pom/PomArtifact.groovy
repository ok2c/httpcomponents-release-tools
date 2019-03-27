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

package com.github.ok2.hc.release.pom

import org.apache.maven.artifact.versioning.ArtifactVersion
import org.apache.maven.artifact.versioning.DefaultArtifactVersion

class PomArtifact {

    final String groupId
    final String id
    final String version
    final private ArtifactVersion parsedVersion

    PomArtifact(String groupId, String id, String version) {
        this.groupId = groupId
        this.id = id
        this.version = version
        this.parsedVersion = new DefaultArtifactVersion(version)
    }

    int getMajor() {
        parsedVersion.majorVersion
    }

    int getMinor() {
        parsedVersion.minorVersion
    }

    int getIncremental() {
        parsedVersion.incrementalVersion
    }

    int getBuildNumber() {
        parsedVersion.buildNumber
    }

    String getQualifier() {
        parsedVersion.qualifier
    }

}
