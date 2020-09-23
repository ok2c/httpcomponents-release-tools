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

import org.ccil.cowan.tagsoup.AttributesImpl
import org.ccil.cowan.tagsoup.Parser
import org.ccil.cowan.tagsoup.XMLWriter
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import java.io.*
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

internal class InternalXMLWriter(writer: Writer, private val block: (URI) -> URI): XMLWriter(writer) {

    override fun startElement(uri: String?, localName: String, qName: String?, atts: Attributes) {
        val idx = when (localName) {
            "a" -> {
                atts.getIndex("href")
            }
            "img" -> {
                atts.getIndex("src")
            }
            else -> -1
        }
        if (idx != -1) {
            val newAtts = AttributesImpl(atts)
            try {
                val oldLink = URI(atts.getValue(idx))
                val newLink = block(oldLink)
                newAtts.setValue(idx, newLink.toASCIIString())
            } catch (ex: URISyntaxException) {
            }
            super.startElement(uri, localName, qName, newAtts)
        } else {
            super.startElement(uri, localName, qName, atts)
        }
    }

}

class HtmlLinkFixer {

    fun rewrite(src: Reader, dst: Writer, block: (URI) -> URI) {
        val parser = Parser()
        parser.contentHandler = InternalXMLWriter(dst, block)
        parser.parse(InputSource(src))
    }

    fun rewrite(src: InputStream, dst: OutputStream, charset: Charset, block: (URI) -> URI) {
        val parser = Parser()
        val writer = InternalXMLWriter(OutputStreamWriter(dst, charset), block)
        writer.setOutputProperty(XMLWriter.ENCODING, charset.name())
        parser.contentHandler = writer
        parser.parse(InputSource(src))
    }

    fun rewrite(path: Path, charset: Charset, block: (URI) -> URI) {
        val tmp = Files.createTempFile("grrrr", path.fileName.toString())
        tmp.toFile().outputStream().use { dst ->
            path.toFile().inputStream().use { src ->
                rewrite(src, dst, charset, block)
            }
        }
        Files.delete(path)
        Files.move(tmp, path)
        Files.delete(tmp)

    }

}