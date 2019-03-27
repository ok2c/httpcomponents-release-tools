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

package com.github.ok2.hc.release.svn

class SvnBulkOp {

    final File path;
    final File copyFrom;
    final long revision;

    protected SvnBulkOp(File path, File copyFrom, long revision) {
        this.path = path
        this.copyFrom = copyFrom
        this.revision = revision
    }

    @Override
    String toString() {
        "op: ${getClass().simpleName}; path: ${path}; copyFrom=${copyFrom}; revision=${revision}"
    }

}