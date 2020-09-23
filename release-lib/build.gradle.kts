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

plugins {
    kotlin("jvm") version "1.3.50"
    `java-gradle-plugin`
}

group = "com.github.ok2c.hc.releasetools"
version = "1-SNAPSHOT"

repositories {
    jcenter()
}

object Versions {
    const val dom4j = "2.1.3"
    const val `pull-parser` = "2.1.10"
    const val jgit = "4.5.3.201708160445-r"
    const val svnkit = "1.10.1"
    const val tagsoup = "1.2.1"
    const val slf4j = "1.7.30"
    const val `junit-jupiter` = "5.6.0"
    const val mockito = "2.28.2"
    const val assertj = "3.15.0"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.dom4j:dom4j:${Versions.dom4j}")
    implementation("pull-parser:pull-parser:${Versions.`pull-parser`}")
    implementation("org.eclipse.jgit:org.eclipse.jgit:${Versions.jgit}")
    implementation("org.tmatesoft.svnkit:svnkit:${Versions.svnkit}")
    implementation("org.tmatesoft.svnkit:svnkit-cli:${Versions.svnkit}")
    implementation("org.ccil.cowan.tagsoup:tagsoup:${Versions.tagsoup}")
    implementation("org.slf4j:slf4j-api:${Versions.slf4j}")
    runtimeOnly("org.slf4j:slf4j-simple:${Versions.slf4j}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${Versions.`junit-jupiter`}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.`junit-jupiter`}")
    testImplementation("org.assertj:assertj-core:${Versions.assertj}")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    plugins.register("hc-release-plugin") {
        id = "hc-release-plugin"
        implementationClass = "com.github.ok2c.hc.release.HCReleasePlugin"
    }
}