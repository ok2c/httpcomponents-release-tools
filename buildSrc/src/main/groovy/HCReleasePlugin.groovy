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

import ok2c.httpcomponents.release.pom.Pom
import ok2c.httpcomponents.release.pom.PomModule
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class HCReleasePlugin implements Plugin<Project> {

    void apply(Project project) {
        if (!project.'HC_RELEASE_DIR') {
            throw new GradleException('HC release dir not specified')
        }
        def hcDir = new File(project.'HC_RELEASE_DIR')
        if (!hcDir.exists()) {
            throw new GradleException("${hcDir} does not exist")
        }
        Pom hcPom = Pom.parsePom(hcDir)
        Configuration hcCfg = project.configurations.create('hc')
        // Declare dependencies (excluding OSGi bundle)
        hcPom.modules.each { PomModule submodule ->
            if (!submodule.name.endsWith('-osgi')) {
                project.dependencies.add(
                        hcCfg.name,
                        ['group': submodule.artifact.groupId, 'name': submodule.artifact.id, 'version': submodule.artifact.version])
            }
        }
        project.extensions.add('hcPom', hcPom)
        project.extensions.add('hcDir', hcDir)
        if (project.'HC_DIST_DIR') {
            project.extensions.add('hcDistDir', new File(project.'HC_DIST_DIR' as String))
        }
    }

}
