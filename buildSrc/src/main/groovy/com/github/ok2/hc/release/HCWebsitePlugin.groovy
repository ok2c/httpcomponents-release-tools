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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class HCWebsitePlugin implements Plugin<Project> {

    void apply(Project project) {
        if (!project.'HC_RELEASE_DIR') {
            throw new GradleException('HC release project dir not specified')
        }
        def hcDir = project.file(project.'HC_RELEASE_DIR')
        if (!project.'HC_DIST_DIR') {
            throw new GradleException('HC release staging dir not specified')
        }
        def stagingDir = project.file(project.'HC_DIST_DIR')
        project.extensions.create("releaseTool", ReleaseTool, hcDir, stagingDir)

        if (!project.'HC_WEBSITE_DIR') {
            throw new GradleException('HC website dir not specified')
        }
        def websiteDir = project.file(project.'HC_WEBSITE_DIR')
        if (!project.'HC_WEB_STAGE_DIR') {
            throw new GradleException('HC website staging dir not specified')
        }
        def websiteStagingDir = project.file(project.'HC_WEB_STAGE_DIR')
        project.extensions.create("websiteTool", WebsiteTool, websiteDir, websiteStagingDir)
    }

}
