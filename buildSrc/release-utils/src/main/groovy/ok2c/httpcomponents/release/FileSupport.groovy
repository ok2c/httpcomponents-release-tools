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

package ok2c.httpcomponents.release

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

class FileSupport {

    static boolean copyFile(Path src, Path dstDir) {
        def result = false;
        if (Files.exists(src) && Files.isRegularFile(src)) {
            if (!Files.exists(dstDir)) {
                Files.createDirectories(dstDir)
            } else {
                assert Files.isDirectory(dstDir) && Files.isWritable(dstDir)
            }
            Files.copy(src, dstDir.resolve(src.getFileName()), StandardCopyOption.REPLACE_EXISTING)
            result = true
        }
        return result
    }

    static long copyDirectory(Path src, Path dstDir, Closure<Boolean> closure) {
        def count = 0L
        if (Files.exists(src) && Files.isDirectory(src)) {
            if (!Files.exists(dstDir)) {
                Files.createDirectories(dstDir)
            } else {
                assert Files.isDirectory(dstDir) && Files.isWritable(dstDir)
            }
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    def filename = dir.fileName
                    if (filename.toString().startsWith('.')) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!closure || closure.call(file)) {
                        def dst = dstDir.resolve(src.relativize(file))
                        def dir = dst.parent
                        if (dir && !Files.exists(dir)) {
                            Files.createDirectories(dir)
                        }
                        Files.copy(file, dst, StandardCopyOption.REPLACE_EXISTING)
                        count++
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return count
    }

}
