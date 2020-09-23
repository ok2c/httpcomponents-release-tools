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
package com.github.ok2c.hc.release.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.errors.RefNotFoundException
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import java.text.MessageFormat

class GetTagsCommand(repo: Repository) : GitCommand<List<String>>(repo) {

    private var target: String? = null
    private var startingWith: String? = null

    fun setTarget(target: String?): GetTagsCommand {
        this.target = target
        return this
    }

    fun setStartingWith(startingWith: String?): GetTagsCommand {
        this.startingWith = startingWith
        return this
    }

    override fun call(): List<String> {
        checkCallable()
        val id = repo.resolve(target ?: Constants.HEAD)
                ?: throw RefNotFoundException(MessageFormat.format(JGitText.get().refNotResolved, target))
        val tags = mutableListOf<String>()
        RevWalk(repo).use { walker ->
            val commitRef = walker.parseCommit(id)
            for (ref in repo.refDatabase.getRefs(Constants.R_TAGS).values) {
                val peeledRef = repo.peel(ref)
                if (peeledRef.peeledObjectId == commitRef) {
                    val name = peeledRef.name.removePrefix("refs/tags/")
                    val pattern = startingWith
                    if (pattern == null || name.startsWith(pattern)) {
                        tags.add(name)
                    }
                }
            }
        }
        setCallable(false)
        return tags
    }

}

fun Git.getAllTags(): GetTagsCommand {
    return GetTagsCommand(repository)
}
