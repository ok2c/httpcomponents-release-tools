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

class SvnCpDir extends SvnBulkOp {

    SvnCpDir(File path, File copyFrom, long revision) {
        super(path, copyFrom, revision)
    }

    SvnCpDir(File path, File copyFrom) {
        super(path, copyFrom, -1)
    }

    SvnCpDir(String path, String copyFrom, long revision) {
        super(new File(path), new File(copyFrom), revision)
    }

    SvnCpDir(String path, String copyFrom) {
        super(new File(path), new File(copyFrom), -1)
    }

}