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

import org.ccil.cowan.tagsoup.AttributesImpl
import org.ccil.cowan.tagsoup.Parser
import org.ccil.cowan.tagsoup.XMLWriter
import org.gradle.util.GFileUtils
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException

class Html {

    static void rewriteLinks(File file, String charset, Closure<URI> c) {
        File tmp = File.createTempFile("grrrr", file.name)
        try {
            Writer writer = tmp.newWriter(charset)
            try {
                Reader reader = file.newReader(charset)
                try {
                    rewriteLinks(reader, writer, c)
                } finally {
                    reader.close()
                }
            } finally {
                writer.close()
            }
            file.delete()
            GFileUtils.moveFile(tmp, file)
        } finally {
            GFileUtils.deleteQuietly(tmp)
        }
    }

    static void rewriteLinks(File file, Closure<URI> c) {
        rewriteLinks(file, 'UTF-8', c)
    }

    static void rewriteLinks(Reader src, Writer dst, Closure<URI> c) {
        Parser parser = new Parser()
        parser.setContentHandler(new XMLWriter(dst) {

            @Override
            void startElement(
                    String uri,
                    String localName,
                    String qName,
                    Attributes atts) throws SAXException {
                AttributesImpl newatts = null;
                int idx = -1;
                switch (localName) {
                    case 'a':
                        idx = atts.getIndex('href')
                        break;
                    case 'img':
                        idx = atts.getIndex('src')
                        break;
                }
                if (idx != -1) {
                    newatts = new AttributesImpl(atts)
                    try {
                        URI origURI = new URI(atts.getValue(idx))
                        URI newURI = c.call(origURI, localName)
                        newatts.setValue(idx, newURI.toASCIIString())
                    } catch (URISyntaxException ignore) {
                    }
                }
                super.startElement(uri, localName, qName, newatts != null ? newatts :atts)
            }
        })
        parser.parse(new InputSource(src))
    }

}
