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
    kotlin("jvm") version "2.2.20"
    `java-gradle-plugin`
}

group = "com.github.ok2c.hc.releasetools"
version = "1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.dom4j)
    implementation(libs.pullparser)
    implementation(libs.jgit)
    implementation(libs.svnkit)
    implementation(libs.svnkit.cli)
    implementation(libs.tagsoup)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.engine)
    testImplementation(libs.junit.params)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins.register("hc-release-plugin") {
        id = "hc-release-plugin"
        implementationClass = "com.github.ok2c.hc.release.HCReleasePlugin"
    }
}