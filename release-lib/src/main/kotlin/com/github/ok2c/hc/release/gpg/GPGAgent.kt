package com.github.ok2c.hc.release.gpg

import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.URLEncoder
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

class GPGAgentRequest {

    val command: String
    val parameters: List<String>

    constructor(command: String, parameters: List<String>) {
        this.command = command
        this.parameters = parameters
    }

    constructor(command: String) {
        this.command = command
        this.parameters = emptyList<String>()
    }

    override fun toString(): String {
        return "$command ($parameters)"
    }

}

enum class ResponseType() {
    OK,
    ERR,
    S,
    D,
    INQUIRE
}

class GPGAgentResponse(val status: ResponseType, val data: String, val comments: List<String>) {

    override fun toString(): String {
        return "$status ($data)"
    }

}

class GPGAgent() : Closeable {

    companion object {
        val MAX_OCTET_PER_LINE = 10000
        val RESP_REGEX = "^(OK|ERR|S|D|INQUIRE) (.*)$".toRegex()
    }

    private val socketChannel: SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)
    private val encoder = StandardCharsets.UTF_8.newEncoder()
    private val decoder = StandardCharsets.UTF_8.newDecoder()
    private val buf = ByteBuffer.allocate(MAX_OCTET_PER_LINE)
    private val cbuf = CharBuffer.allocate(MAX_OCTET_PER_LINE)

    override fun close() {
        socketChannel.close()
    }

    fun connect(socketAddress: UnixDomainSocketAddress): GPGAgentResponse {
        socketChannel.connect(socketAddress)
        return readResponse()
    }

    fun exchange(request: GPGAgentRequest): GPGAgentResponse {
        writeRequest(request)
        return readResponse()
    }

    fun writeRequest(request: GPGAgentRequest) {
        buf.clear()
        cbuf.clear()
        cbuf.append(request.command)
        for (param in request.parameters) {
            if (!cbuf.hasRemaining()) {
                throw IllegalArgumentException("Request is too long")
            }
            cbuf.append(' ')
            if (param.startsWith("--")) {
                cbuf.append(param)
            } else {
                cbuf.append(URLEncoder.encode(param, StandardCharsets.UTF_8))
            }
        }
        cbuf.append('\n')
        cbuf.flip()
        encoder.encode(cbuf, buf, true)
        if (cbuf.hasRemaining()) {
            throw IllegalArgumentException("Command is too long")
        }
        buf.flip()
        socketChannel.write(buf)
        buf.clear()
        cbuf.clear()
    }

    fun readResponse(): GPGAgentResponse {
        buf.clear()
        cbuf.clear()

        var responseLine: String? = null
        val commends = mutableListOf<String>()
        val sbuf = StringBuilder()
        do {
            sbuf.setLength(0)
            val readBytes = socketChannel.read(buf)
            if (readBytes == -1) {
                throw ClosedChannelException()
            }
            buf.flip()
            decoder.decode(buf, cbuf, true)
            buf.compact()

            cbuf.flip()
            for (ch in cbuf) {
                if (ch == '\n') {
                    if (!sbuf.isEmpty()) {
                        val line = sbuf.toString()
                        if (line.startsWith('#')) {
                            commends.add(line)
                        } else {
                            responseLine = line
                            break;
                        }
                    }
                    sbuf.setLength(0)
                } else {
                    sbuf.append(ch)
                }
            }
            cbuf.compact()
        } while (responseLine == null)

        buf.clear()
        cbuf.clear()

        if (responseLine.equals(ResponseType.OK.name)) {
            return GPGAgentResponse(ResponseType.OK, "", commends)
        } else {
            val matchResult = RESP_REGEX.find(responseLine)
            if (matchResult != null) {
                return GPGAgentResponse(
                    ResponseType.valueOf(matchResult.groups[1]!!.value),
                    matchResult.groups[2]!!.value,
                    commends
                )
            } else {
                throw IOException("Unexpected GPG agent response")
            }
        }
    }

}