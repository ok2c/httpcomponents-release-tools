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

import java.security.MessageDigest

class Digester {

    static String digest(String digestAlgo, File file) {
        MessageDigest digest = MessageDigest.getInstance(digestAlgo)
        file.withInputStream { inputStream ->
            def buf = new byte[2048]
            int bytesRead
            while ((bytesRead = inputStream.read(buf)) != -1) {
                digest.update(buf, 0, bytesRead)
            }
        }
        int padding
        switch (digestAlgo.toLowerCase(Locale.ROOT)) {
            case 'sha-512':
                padding = 128
                break
            case 'sha-256':
                padding = 64
                break
            default:
                padding = 32
        }
        new BigInteger(1, digest.digest()).toString(16).padLeft(padding, "0")
    }

}
