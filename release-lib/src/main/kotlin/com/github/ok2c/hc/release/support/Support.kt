package com.github.ok2c.hc.release.support

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

fun deleteContent(path: Path) {

    Files.walkFileTree(path, object: FileVisitor<Path> {

        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes?): FileVisitResult {
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, ex: IOException?): FileVisitResult {
            return if (ex != null) {
                FileVisitResult.TERMINATE
            } else {
                FileVisitResult.CONTINUE
            }
        }

        override fun postVisitDirectory(dir: Path, ex: IOException?): FileVisitResult {
            return if (ex != null) {
                FileVisitResult.TERMINATE
            } else {
                Files.delete(dir)
                FileVisitResult.CONTINUE
            }
        }

    })

}
