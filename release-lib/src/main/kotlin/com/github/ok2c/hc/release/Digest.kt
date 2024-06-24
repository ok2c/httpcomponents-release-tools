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

package com.github.ok2c.hc.release

import com.github.ok2c.hc.release.digest.FileDigester
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter
import java.util.*

open class Digest : DefaultTask() {

    @Internal
    val hashes = DefaultDomainObjectSet<DigestHash>(DigestHash::class.java, CollectionCallbackActionDecorator.NOOP)

    fun digest(artifact: PublishArtifact) {
        dependsOn(artifact)
        hashes.add(DigestHash(artifact, "sha512", "sha-512"))
    }

    fun digest(artifacts: Array<PublishArtifact>) {
        for (artifact in artifacts) {
            digest(artifact)
        }
    }

    fun digest(configuration: Configuration) {
        for (artifact in configuration.allArtifacts) {
            digest(artifact)
        }
        configuration.allArtifacts.whenObjectRemoved { artifact ->
            val hash: DigestHash? = hashes.find { it.digestArtifact == artifact }
            if (hash != null) {
                hashes.remove(hash)
            }
        }
    }

    fun digest(configurations: Array<Configuration>) {
        for (configuration in configurations) {
            digest(configuration)
        }
    }

    @InputFiles
    fun getSourceFiles(): List<File> {
        return hashes.map { it.digestArtifact.file }
    }

    @OutputFiles
    fun getDigestFiles(): List<File> {
        return hashes.map { it.file }
    }

    @TaskAction
    fun generate() {
        val digester = FileDigester()
        for (digestHash in hashes) {
            val file = digestHash.file
            val hash = digester.digest(digestHash.algo, digestHash.digestArtifact.file)
            FileWriter(file).use {
                it.write(hash)
                it.write(" *")
                it.write(digestHash.digestArtifact.file.name)
            }
        }
    }

}

open class DigestHash(
        val digestArtifact: PublishArtifact,
        private val extension: String,
        val algo: String) : AbstractPublishArtifact(DefaultTaskDependencyFactory.withNoAssociatedProject()) {

    override fun getName(): String {
        return digestArtifact.name
    }

    override fun getExtension(): String {
        return extension
    }

    override fun getType(): String {
        return algo
    }

    override fun getClassifier(): String? {
        return digestArtifact.classifier
    }

    override fun getFile(): File {
        val file = digestArtifact.file
        val parent = file.parentFile?.path
        return when {
            parent != null -> {
                File(parent, "${file.name}.${extension}")
            } else -> {
                File("${file}.${extension}")
            }
        }
    }

    override fun getDate(): Date? {
        return digestArtifact.date
    }

    override fun shouldBePublished(): Boolean {
        return true
    }

}

