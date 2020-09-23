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
package com.github.ok2c.hc.release.html

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter
import java.net.URI

class HtmlLinkFixerTest {

    @Test
    fun `html link rewriting`() {
        val fixer = HtmlLinkFixer()
        val src = StringReader("<html><body>" +
                "<p><a stuff=\"blah\" href=\"link.html\"/>" +
                "<p><img stuff=\"blah\" src=\"image.jpg\"/>" +
                "</body></html>")
        val dst = StringWriter()
        fixer.rewrite(src, dst) { uri ->
            URI("http", null, "somehost", -1, "/${uri.path}", uri.query, uri.fragment)
        }
        Assertions.assertThat(dst.toString()).isEqualTo("<?xml version=\"1.0\" standalone=\"yes\"?>\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" +
                "<p><a shape=\"rect\" stuff=\"blah\" href=\"http://somehost/link.html\"></a></p>" +
                "<p><img stuff=\"blah\" src=\"http://somehost/image.jpg\"></img></p>" +
                "</body></html>\n")
    }

}
