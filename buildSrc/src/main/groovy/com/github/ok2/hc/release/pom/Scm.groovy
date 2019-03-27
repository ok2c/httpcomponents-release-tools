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

package com.github.ok2.hc.release.pom

class Scm {

    private final String connection
    private final String tag
    private final String uriPattern

    Scm(String connection, String tag, String uriPattern) {
        this.connection = connection
        this.tag = tag
        this.uriPattern = uriPattern
    }

    String getConnection() {
        return connection
    }

    String getTag() {
        return tag
    }

    String getUriPattern() {
        return uriPattern
    }

    URI getUri() {
        if (uriPattern && tag) {
            URI.create(uriPattern.replace('${project.scm.tag}', tag))
        } else {
            null
        }
    }

}
