package com.github.ok2c.hc.release.svn

import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider
import org.tmatesoft.svn.cli.svn.SVNCommandEnvironment
import org.tmatesoft.svn.cli.svn.SVNNotifyPrinter
import org.tmatesoft.svn.cli.svn.SVNStatusPrinter
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNWCUtil
import org.tmatesoft.svn.core.wc2.*
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

class Svn {

    private fun <R> svn(block: (SvnOperationFactory) -> R): R {
        val env = SVNCommandEnvironment("SVN", System.out, System.err, System.`in`)
        val opfactory = SvnOperationFactory()
        return try {
            val authmanager = SVNWCUtil.createDefaultAuthenticationManager()
            authmanager.setAuthenticationProvider(SVNConsoleAuthenticationProvider(false))
            opfactory.authenticationManager = authmanager
            opfactory.eventHandler = SVNNotifyPrinter(env)
            block(opfactory)
        } finally {
            opfactory.dispose()
        }
    }

    private fun <R> svnRepo(src: URI, block: (SVNRepository) -> R): R {
        val url = SVNURL.parseURIEncoded(src.toASCIIString())
        val repo = SVNRepositoryFactory.create(url)
        return try {
            val authmanager = SVNWCUtil.createDefaultAuthenticationManager()
            authmanager.setAuthenticationProvider(SVNConsoleAuthenticationProvider(false))
            repo.authenticationManager = authmanager
            block(repo)
        } finally {
            repo.closeSession()
        }
    }

    fun info(dir: Path): SvnInfo {
        return svn { opfactory ->
            val infoOp = opfactory.createGetInfo()
            infoOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            infoOp.run()
            infoOp.first()
        }
    }

    fun infoRemote(uri: URI): SvnInfo? {
        return svn { opfactory ->
            val infoOp = opfactory.createGetInfo()
            infoOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(uri.toASCIIString())))
            infoOp.run()
            infoOp.first()
        }
    }

    fun exists(uri: URI): Boolean {
        try {
            val svnInfo = infoRemote(uri)
            if (svnInfo != null) {
                return true
            }
        } catch (ex: SVNException) {
            if (ex.errorMessage.errorCode != SVNErrorCode.RA_ILLEGAL_URL) {
                throw ex
            }
        }
        return false
    }

    fun checkout(src: URI, dst: Path) {
        svn { opfactory ->
            val checkoutOp = opfactory.createCheckout()
            checkoutOp.source = SvnTarget.fromURL(SVNURL.parseURIEncoded(src.toASCIIString()))
            checkoutOp.setSingleTarget(SvnTarget.fromFile(dst.toFile()))
            checkoutOp.run()
        }
    }

    fun update(dir: Path) {
        svn { opfactory ->
            val updateOp = opfactory.createUpdate()
            updateOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            updateOp.run()
        }
    }

    fun status(dir: Path) {
        svn { opfactory ->
            val env = SVNCommandEnvironment("SVN", System.out, System.err, System.`in`)
            val statusPrinter = SVNStatusPrinter(env)
            val context = opfactory.wcContext
            val statusOp = opfactory.createGetStatus()
            statusOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            statusOp.isReportAll = false
            statusOp.receiver = ISvnObjectReceiver<SvnStatus> { target, obj ->
                val root = dir.toAbsolutePath().toString();
                var f = target.file.absolutePath
                if (f.startsWith(root)) {
                    f = f.substring(root.length)
                    if (f.startsWith('/')) {
                        f = f.substring(1, f.length)
                    }
                }
                statusPrinter.printStatus(f, SvnCodec.status(context, obj), false, true, true, false)
            }
            statusOp.run()
        }
    }

    fun revert(dir: Path) {
        svn { opfactory ->
            val revertOp = opfactory.createRevert()
            revertOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            revertOp.depth = SVNDepth.INFINITY
            revertOp.isPreserveModifiedCopies = false
            revertOp.isRevertMissingDirectories = true
            revertOp.run()
        }
    }

    fun cleanup(dir: Path) {
        svn { opfactory ->
            val cleanUpOp = opfactory.createCleanup()
            cleanUpOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            cleanUpOp.depth = SVNDepth.INFINITY
            cleanUpOp.isRemoveUnversionedItems = true
            cleanUpOp.isRemoveIgnoredItems = true
            cleanUpOp.run()
        }
    }

    fun scheduleForAddition(dir: Path) {
        svn { opfactory ->
            val schedulingOp = opfactory.createScheduleForAddition()
            schedulingOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            schedulingOp.depth = SVNDepth.INFINITY
            schedulingOp.isForce = true
            schedulingOp.isIncludeIgnored = false
            schedulingOp.run()
        }
    }

    fun commit(dir: Path, message: String): Long {
        return svn { opfactory ->
            val commitOp = opfactory.createCommit()
            commitOp.setSingleTarget(SvnTarget.fromFile(dir.toFile()))
            commitOp.depth = SVNDepth.INFINITY
            commitOp.commitMessage = message
            val result = commitOp.run()
            result.newRevision
        }
    }

    fun copyLocal(dir: Path, dst: URI, message: String): Long {
        return svn { opfactory ->
            val copyOp = opfactory.createRemoteCopy()
            copyOp.addCopySource(SvnCopySource.create(SvnTarget.fromFile(dir.toFile()), SVNRevision.WORKING))
            copyOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(dst.toASCIIString())))
            copyOp.isFailWhenDstExists = true
            copyOp.isDisableLocalModifications = false
            copyOp.commitMessage = message
            val result = copyOp.run()
            result.newRevision
        }
    }

    fun copyRemote(src: URI, dst: URI, message: String): Long {
        return svn { opfactory ->
            val copyOp = opfactory.createRemoteCopy()
            copyOp.addCopySource(SvnCopySource.create(SvnTarget.fromURL(
                    SVNURL.parseURIEncoded(src.toASCIIString())), SVNRevision.HEAD))
            copyOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(dst.toASCIIString())))
            copyOp.isFailWhenDstExists = true
            copyOp.commitMessage = message
            val result = copyOp.run()
            result.newRevision
        }
    }

    fun deleteRemote(src: URI, message: String) {
        svn { opfactory ->
            val deleteOp = opfactory.createRemoteDelete()
            deleteOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(src.toASCIIString())))
            deleteOp.commitMessage = message
            deleteOp.run()
        }
    }

    fun mucc(root: URI, bulkOps: List<SvnBulkOp>, message: String): Long {
        return svnRepo(root) { repo ->
            val commitEditor = repo.getCommitEditor(message, null)
            commitEditor.openRoot(-1)
            for (op in bulkOps) {
                val parents = mutableListOf<Path>()
                var parent: Path? = op.path.parent
                while (parent != null) {
                    parents.add(0, parent)
                    parent = parent.parent
                }
                for (dir in parents) {
                    commitEditor.openDir(dir.toString(), op.revision)
                }
                when (op) {
                    is SvnRmOp -> {
                        commitEditor.deleteEntry(op.path.toString(), op.revision)
                    }
                    is SvnMkDirOp -> {
                        commitEditor.addDir(op.path.toString(), null, op.revision)
                        commitEditor.closeDir()
                    }
                    is SvnCpDirOp -> {
                        commitEditor.addDir(op.path.toString(), op.copyFrom?.toString(), op.revision)
                        commitEditor.closeDir()
                    }
                    is SvnCpFileOp -> {
                        commitEditor.addFile(op.path.toString(), op.copyFrom?.toString(), op.revision)
                    }
                }
                for (dir in parents) {
                    commitEditor.closeDir()
                }
            }
            val result = commitEditor.closeEdit()
            result.newRevision
        }
    }

}

open class SvnBulkOp(val path: Path, val copyFrom: Path?, val revision: Long = -1) {

    override fun toString(): String {
        return "op: ${javaClass.simpleName}; path: ${path}; copyFrom=${copyFrom}; revision=${revision}"
    }

}

class SvnCpDirOp(path: Path, copyFrom: Path, revision: Long = -1) : SvnBulkOp(path, copyFrom, revision)

class SvnCpFileOp(path: Path, copyFrom: Path, revision: Long = -1) : SvnBulkOp(path, copyFrom, revision)

class SvnMkDirOp(path: Path) : SvnBulkOp(path, null, -1)

class SvnRmOp(path: Path) : SvnBulkOp(path, null, -1)

fun main(args: Array<String>) {
    val svn = Svn()
    val dir = Paths.get("/home/oleg/src/apache.org/httpcomponents/dist-staging")
    val svnInfo = svn.info(dir)
    println("r${svnInfo.revision} ${svnInfo.url}")
}