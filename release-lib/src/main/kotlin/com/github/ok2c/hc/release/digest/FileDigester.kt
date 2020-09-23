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
package com.github.ok2c.hc.release.digest

import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class FileDigester {

    internal fun digest(messageDigest: MessageDigest, inputStream: InputStream) {
        val buf = ByteArray(1024 * 4)
        var bytesRead = inputStream.read(buf)
        while (bytesRead != -1) {
            messageDigest.update(buf, 0, bytesRead)
            bytesRead = inputStream.read(buf)
        }
    }

    internal fun format(messageDigest: MessageDigest): String {
        val padding = when(messageDigest.algorithm.toLowerCase(Locale.ROOT)) {
            "sha-512" -> 128
            "sha-256" -> 64
            else -> 32
        }
        return BigInteger(1, messageDigest.digest()).toString(16).padStart(padding, '0')
    }

    fun digest(digestAlgo: String, file: File): String {
        val messageDigest = MessageDigest.getInstance(digestAlgo)
        file.inputStream().use {
            digest(messageDigest, it)
        }
        return format(messageDigest)
    }

    fun digest(digestAlgo: String, url: URL): String {
        val messageDigest = MessageDigest.getInstance(digestAlgo)
        url.openStream().use {
            digest(messageDigest, it)
        }
        return format(messageDigest)
    }

}