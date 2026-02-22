package com.github.ok2c.hc.release.svn

import com.github.ok2c.hc.release.gpg.GPGAgent
import com.github.ok2c.hc.release.gpg.GPGAgentRequest
import com.github.ok2c.hc.release.gpg.ResponseType
import org.slf4j.LoggerFactory
import org.tmatesoft.svn.core.SVNErrorMessage
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider
import org.tmatesoft.svn.core.auth.SVNAuthentication
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication
import java.net.UnixDomainSocketAddress
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

internal class CacheKey(val kind: String?, val url: SVNURL, val realm: String?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CacheKey

        if (kind != other.kind) return false
        if (url != other.url) return false
        if (realm != other.realm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind?.hashCode() ?: 0
        result = 31 * result + url.hashCode()
        result = 31 * result + (realm?.hashCode() ?: 0)
        return result
    }

}

class GPGAgentAuthenticationProvider(private val propertyResolver: Function<String, Any?>?): ISVNAuthenticationProvider {

    companion object {
        val GPG_AGENT_INFO = "GPG_AGENT_INFO"
        val LOG = LoggerFactory.getLogger(GPGAgentAuthenticationProvider::class.java)
    }

    private val attemptedAuth: ConcurrentHashMap<CacheKey, AtomicInteger>  = ConcurrentHashMap()

    override fun requestClientAuthentication(
        kind: String?,
        url: SVNURL?,
        realm: String?,
        errorMessage: SVNErrorMessage?,
        previousAuth: SVNAuthentication?,
        authMayBeStored: Boolean
    ): SVNAuthentication? {
        if (url == null) {
            return null;
        }
        var username = propertyResolver?.apply("asf.svn.user")?.toString()
        if (username == null && previousAuth != null) {
            username = previousAuth.userName
        }
        if (username == null) {
            return null;
        }

        val pgpAgentInfo = System.getenv(GPG_AGENT_INFO)
        if (pgpAgentInfo != null) {
            val pgpAgentInfoParts = pgpAgentInfo.split(":")
            if (pgpAgentInfoParts.size > 1 && pgpAgentInfoParts.size <= 3) {
                val gpgAgent = GPGAgent()
                try {
                    val socketAddress = UnixDomainSocketAddress.of(Paths.get(pgpAgentInfoParts[0]))
                    val connectResponse = gpgAgent.connect(socketAddress)
                    if (connectResponse.status == ResponseType.OK) {
                        LOG.debug("Successfully connected to the PGP agent: {}", connectResponse.data)

                        val cacheId = username + "@" + url?.host

                        val attemptCounter = attemptedAuth.computeIfAbsent(CacheKey(kind, url, realm), {k -> AtomicInteger(0)})
                        val count = attemptCounter.getAndIncrement()
                        if (count > 3) {
                            return null
                        }
                        if (count > 0) {
                            LOG.debug("Clearing credentials {}", cacheId)
                            val clearPassPhraseResponse = gpgAgent.exchange(
                                GPGAgentRequest(
                                    "CLEAR_PASSPHRASE",
                                    listOf(
                                        cacheId,
                                    )
                                )
                            )
                            if (clearPassPhraseResponse.status != ResponseType.OK) {
                                return null
                            }
                        }

                        LOG.debug("Requesting credentials {}", cacheId)
                        val passPhraseResponse = gpgAgent.exchange(
                            GPGAgentRequest(
                                "GET_PASSPHRASE",
                                listOf(
                                    "--data",
                                    cacheId,
                                    "x",
                                    "Authentication realm: $realm",
                                    "Please provide password for: $cacheId"
                                )
                            )
                        )
                        if (passPhraseResponse.status == ResponseType.D) {
                            return SVNPasswordAuthentication.newInstance(username, passPhraseResponse.data.toCharArray(), authMayBeStored, url, false)
                        } else {
                            LOG.warn("Failed to get password for {} to the PGP agent: {} {}", cacheId,  passPhraseResponse.status, passPhraseResponse.data)
                        }
                    } else if (connectResponse.status == ResponseType.ERR) {
                        LOG.error("Failed to connect to the PGP agent: {}", connectResponse.data)
                    } else if (connectResponse.status == ResponseType.INQUIRE) {
                        LOG.error("PGP agent requires authentication: {}", connectResponse.data)
                    } else {
                        LOG.error("Unexpected response {} {} from PGP agent", connectResponse.status, connectResponse.data)
                    }
                } finally {
                    gpgAgent.close()
                }
            } else {
                LOG.warn("GPG_AGENT_INFO env variable cound not be parsed")
            }
        } else {
            LOG.warn("GPG_AGENT_INFO env variable not set")
        }
        return null
    }

    override fun acceptServerAuthentication(
        url: SVNURL?,
        realm: String?,
        certificate: Any?,
        resultMayBeStored: Boolean
    ): Int {
        return ISVNAuthenticationProvider.ACCEPTED_TEMPORARY
    }

}