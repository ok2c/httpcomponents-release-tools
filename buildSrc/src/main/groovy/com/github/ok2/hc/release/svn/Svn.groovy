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

import org.tmatesoft.svn.cli.SVNConsoleAuthenticationProvider
import org.tmatesoft.svn.cli.svn.SVNCommandEnvironment
import org.tmatesoft.svn.cli.svn.SVNNotifyPrinter
import org.tmatesoft.svn.cli.svn.SVNStatusPrinter
import org.tmatesoft.svn.core.SVNCommitInfo
import org.tmatesoft.svn.core.SVNDepth
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec
import org.tmatesoft.svn.core.io.ISVNEditor
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.ISVNOptions
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.wc.SVNWCUtil
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver
import org.tmatesoft.svn.core.wc2.SvnCheckout
import org.tmatesoft.svn.core.wc2.SvnCommit
import org.tmatesoft.svn.core.wc2.SvnCopySource
import org.tmatesoft.svn.core.wc2.SvnGetInfo
import org.tmatesoft.svn.core.wc2.SvnGetStatus
import org.tmatesoft.svn.core.wc2.SvnInfo
import org.tmatesoft.svn.core.wc2.SvnOperationFactory
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete
import org.tmatesoft.svn.core.wc2.SvnRevert
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition
import org.tmatesoft.svn.core.wc2.SvnStatus
import org.tmatesoft.svn.core.wc2.SvnTarget
import org.tmatesoft.svn.core.wc2.SvnUpdate

class Svn {

    private static SVNCommandEnvironment getSVNCommandEnvironment() {
        new SVNCommandEnvironment("SVN", System.out, System.err, System.in);
    }

    private static SvnOperationFactory createOperationFactory(SVNCommandEnvironment env) {
        SvnOperationFactory opfactory = new SvnOperationFactory()
        ISVNAuthenticationManager authmanager = SVNWCUtil.createDefaultAuthenticationManager()
        authmanager.setAuthenticationProvider(new SVNConsoleAuthenticationProvider(false))
        opfactory.setAuthenticationManager(authmanager)
        opfactory.setEventHandler(new SVNNotifyPrinter(env))
        opfactory
    }

    static ISVNOptions getOptions() {
        SVNWCUtil.createDefaultOptions(SVNWCUtil.getDefaultConfigurationDirectory(), true);
    }

    static SVNRepository getRepository(URI src) {
        SVNURL url = SVNURL.parseURIEncoded(src.toASCIIString())
        SVNRepository repo = SVNRepositoryFactory.create(url)
        ISVNAuthenticationManager authmanager = SVNWCUtil.createDefaultAuthenticationManager()
        authmanager.setAuthenticationProvider(new SVNConsoleAuthenticationProvider(false))
        repo.setAuthenticationManager(authmanager)
        repo
    }

    static SvnInfo info(File dir) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnGetInfo infoOp = opfactory.createGetInfo()
            infoOp.setSingleTarget(SvnTarget.fromFile(dir))
            infoOp.run()
            infoOp.first()
        } finally {
            opfactory.dispose()
        }
    }

    static SvnInfo infoRemote(URI uri) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnGetInfo infoOp = opfactory.createGetInfo()
            infoOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(uri.toASCIIString())))
            infoOp.run()
            infoOp.first()
        } catch (SVNException ex) {
            if (SVNErrorCode.RA_ILLEGAL_URL == ex.errorMessage.errorCode) {
                null
            } else {
                throw ex
            }
        } finally {
            opfactory.dispose()
        }
    }

    static void checkout(URI src, File dst) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnCheckout checkoutOp = opfactory.createCheckout()
            checkoutOp.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(src.toASCIIString())))
            checkoutOp.setSingleTarget(SvnTarget.fromFile(dst))
            checkoutOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static void update(File dir) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnUpdate updateOp = opfactory.createUpdate()
            updateOp.setSingleTarget(SvnTarget.fromFile(dir))
            updateOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static void status(File dir) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SVNStatusPrinter statusPrinter = new SVNStatusPrinter(env)
            SVNWCContext context = opfactory.getWcContext();
            SvnGetStatus statusOp = opfactory.createGetStatus()
            statusOp.setSingleTarget(SvnTarget.fromFile(dir))
            statusOp.setReportAll(false)
            statusOp.setReceiver(new ISvnObjectReceiver<SvnStatus>() {

                @Override
                void receive(SvnTarget target, SvnStatus object) throws SVNException {
                    String root = dir.getAbsoluteFile();
                    String f = target.getFile().getAbsolutePath()
                    if (f.startsWith(root)) {
                        f = f.substring(root.length())
                        if (f.startsWith('/')) {
                            f = f.substring(1, f.length())
                        }
                    }
                    statusPrinter.printStatus(f,
                            SvnCodec.status(context, object), false, true, true, false)
                }

            })
            statusOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static void revert(File dir) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnRevert revertOp = opfactory.createRevert()
            revertOp.setSingleTarget(SvnTarget.fromFile(dir))
            revertOp.setDepth(SVNDepth.INFINITY)
            revertOp.setPreserveModifiedCopies(false)
            revertOp.setRevertMissingDirectories(true)
            revertOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static void scheduleForAddition(File dir) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnScheduleForAddition schedulingOp = opfactory.createScheduleForAddition()
            schedulingOp.setSingleTarget(SvnTarget.fromFile(dir))
            schedulingOp.setDepth(SVNDepth.INFINITY)
            schedulingOp.setForce(true)
            schedulingOp.setIncludeIgnored(false)
            schedulingOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static long commit(File dir, String message) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnCommit commitOp = opfactory.createCommit()
            commitOp.setSingleTarget(SvnTarget.fromFile(dir))
            commitOp.setDepth(SVNDepth.INFINITY)
            commitOp.setCommitMessage(message)
            SVNCommitInfo result = commitOp.run()
            result.newRevision
        } finally {
            opfactory.dispose()
        }
    }

    static long copyLocal(File dir, URI dst, String message) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnRemoteCopy copyOp = opfactory.createRemoteCopy()
            copyOp.addCopySource(
                    SvnCopySource.create(SvnTarget.fromFile(dir), SVNRevision.WORKING))
            copyOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(dst.toASCIIString())))
            copyOp.setFailWhenDstExists(true)
            copyOp.setDisableLocalModifications(false)
            copyOp.setCommitMessage(message)
            SVNCommitInfo result = copyOp.run()
            result.newRevision
        } finally {
            opfactory.dispose()
        }
    }

    static long copyRemote(URI src, URI dst, String message) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnRemoteCopy copyOp = opfactory.createRemoteCopy()
            copyOp.addCopySource(
                    SvnCopySource.create(SvnTarget.fromURL(
                            SVNURL.parseURIEncoded(src.toASCIIString())), SVNRevision.HEAD))
            copyOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(dst.toASCIIString())))
            copyOp.setFailWhenDstExists(true)
            copyOp.setCommitMessage(message)
            SVNCommitInfo result = copyOp.run()
            result.newRevision
        } finally {
            opfactory.dispose()
        }
    }

    static void deleteRemote(URI src, String message) {
        SVNCommandEnvironment env = getSVNCommandEnvironment()
        SvnOperationFactory opfactory = createOperationFactory(env)
        try {
            SvnRemoteDelete deleteOp = opfactory.createRemoteDelete()
            deleteOp.setSingleTarget(SvnTarget.fromURL(SVNURL.parseURIEncoded(src.toASCIIString())))
            deleteOp.setCommitMessage(message)
            deleteOp.run()
        } finally {
            opfactory.dispose()
        }
    }

    static long mucc(URI root, List<SvnBulkOp> bulkOps, String message) {
        SVNRepository repository = Svn.getRepository(root)
        try {
            ISVNEditor commitEditor = repository.getCommitEditor(message, null)
            commitEditor.openRoot(-1)
            for (SvnBulkOp op in bulkOps) {

                LinkedList<File> parents = new ArrayList<>()
                File parent = op.path.parentFile
                while (parent) {
                    parents.addFirst(parent)
                    parent = parent.parentFile
                }

                int revision = op.revision ?: -1

                for (File dir in parents) {
                    commitEditor.openDir(dir.path, revision)
                }

                switch (op) {
                    case SvnRm:
                        File entry = op.path
                        commitEditor.deleteEntry(entry.path, revision)
                        break;
                    case SvnMkdir:
                        File dir = op.path
                        commitEditor.addDir(dir.path, null, revision)
                        commitEditor.closeDir()
                        break;
                    case SvnCpDir:
                        File dir = op.path
                        File src = op.copyFrom
                        commitEditor.addDir(dir.path, src.path, revision)
                        commitEditor.closeDir()
                        break;
                    case SvnCpFile:
                        File file = op.path
                        File src = op.copyFrom
                        commitEditor.addFile(file.path, src.path, revision)
                        break;
                }

                for (File dir in parents) {
                    commitEditor.closeDir()
                }
            }

            SVNCommitInfo result = commitEditor.closeEdit()
            result.newRevision
        } finally {
            repository.closeSession();
        }
    }

}
