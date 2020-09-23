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

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class FileDigesterTest {

    @Test
    fun `digest resource`() {
        val fileDigester = FileDigester()
        val digest = fileDigester.digest("sha-256", javaClass.getResource("/test.bin"))
        Assertions.assertThat(digest).isEqualTo("5fb44f94afaf3e48b98ed20740340924133096f6d1ccd40f3fecd527c02a16e8")
    }

    @Test
    fun `digest formatting`() {
        val fileDigester = FileDigester()
        val md5 = MessageDigest.getInstance("md5")
        md5.digest(byteArrayOf(1, 2, 3, 4))
        Assertions.assertThat(fileDigester.format(md5)).isEqualTo("d41d8cd98f00b204e9800998ecf8427e")
        val sha256 = MessageDigest.getInstance("sha-256")
        sha256.digest(byteArrayOf(1, 2, 3, 4))
        Assertions.assertThat(fileDigester.format(sha256)).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        val sha512 = MessageDigest.getInstance("sha-512")
        sha512.digest(byteArrayOf(1, 2, 3, 4))
        Assertions.assertThat(fileDigester.format(sha512)).isEqualTo("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e")
    }

}
