package io.nekohasekai.sagernet.privacy

import android.net.ConnectivityManager
import android.os.Build
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

data class PrivacyHoneypotEvent(
    val timestamp: Long,
    val transport: String,
    val localPort: Int,
    val remotePort: Int,
    val uid: Int,
    val packageName: String?,
    val classification: String,
)

object PrivacyHoneypot {

    private const val IPPROTO_TCP = 6
    private const val LOG_LIMIT = 200

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<Closeable>()
    private var scope: CoroutineScope? = null

    fun start() {
        if (!DataStore.privacyHoneypotEnabled) return
        synchronized(lock) {
            if (scope != null) return
            val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = nextScope
            parsePorts().forEach { port ->
                startTcpListener(nextScope, "127.0.0.1", port)
                startTcpListener(nextScope, "::1", port)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            listeners.forEach {
                runCatching { it.close() }
            }
            listeners.clear()
            scope?.cancel()
            scope = null
        }
    }

    fun clearLog() {
        DataStore.privacyHoneypotLog = ""
    }

    fun parseLog(raw: String = DataStore.privacyHoneypotLog): List<PrivacyHoneypotEvent> {
        return raw.lineSequence()
            .mapNotNull(::parseLine)
            .sortedByDescending { it.timestamp }
            .toList()
    }

    private fun startTcpListener(scope: CoroutineScope, host: String, port: Int) {
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(host), port))
            }
        } catch (_: IOException) {
            return
        }
        listeners += server
        scope.launch {
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                } catch (e: IOException) {
                    Logs.w(e)
                    break
                }
                launch {
                    handleTcpSocket(socket)
                }
            }
        }
    }

    private fun handleTcpSocket(socket: Socket) {
        socket.use { current ->
            val local = current.localSocketAddress as? InetSocketAddress ?: return
            val remote = current.remoteSocketAddress as? InetSocketAddress ?: return
            current.soTimeout = 150
            val classification = detectTcpPayload(current)
            val uid = lookupOwnerUid(remote, local)
            val packageName = packageNameForUid(uid)
            appendLog(
                PrivacyHoneypotEvent(
                    timestamp = System.currentTimeMillis(),
                    transport = "tcp",
                    localPort = local.port,
                    remotePort = remote.port,
                    uid = uid,
                    packageName = packageName,
                    classification = classification,
                )
            )
            writeTcpResponse(current, classification)
        }
    }

    private fun detectTcpPayload(socket: Socket): String {
        return try {
            val payload = ByteArray(64)
            val read = socket.getInputStream().read(payload)
            classifyPayload(payload, read)
        } catch (_: IOException) {
            "connect"
        }
    }

    private fun classifyPayload(payload: ByteArray, read: Int): String {
        if (read <= 0) return "connect"
        val first = payload[0].toInt() and 0xFF
        if (first == 0x05) return "socks5"
        if (first == 0x04) return "socks4"
        val sample = payload.copyOf(read).decodeToString().trimStart().lowercase()
        return when {
            sample.startsWith("connect ") -> "http-connect"
            sample.startsWith("get ") ||
                sample.startsWith("post ") ||
                sample.startsWith("head ") ||
                sample.startsWith("options ") -> "http"
            else -> "connect"
        }
    }

    private fun writeTcpResponse(socket: Socket, classification: String) {
        runCatching {
            val output = socket.getOutputStream()
            when (classification) {
                "socks5" -> output.write(byteArrayOf(0x05, 0xFF.toByte()))
                "socks4" -> output.write(byteArrayOf(0x00, 0x5B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                else -> output.write(
                    (
                        "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                            "Connection: close\r\n" +
                            "Proxy-Authenticate: Basic realm=\"Exclave Honeypot\"\r\n\r\n"
                        ).toByteArray()
                )
            }
            output.flush()
        }
    }

    private fun lookupOwnerUid(
        remote: InetSocketAddress,
        local: InetSocketAddress,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return -1
        return runCatching {
            connectivity.getConnectionOwnerUid(IPPROTO_TCP, remote, local)
        }.getOrDefault(-1)
    }

    private fun packageNameForUid(uid: Int): String? {
        if (uid < 0) return null
        PackageCache.awaitLoadSync()
        return PackageCache[uid]?.firstOrNull()
    }

    private fun parsePorts(): List<Int> {
        val takenPorts = buildSet {
            add(DataStore.socksPort)
            add(DataStore.httpPort)
            add(DataStore.localDNSPort)
            add(DataStore.transproxyPort)
        }
        return DataStore.privacyHoneypotPorts
            .split(',', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..65535 && it !in takenPorts }
            .distinct()
    }

    @Synchronized
    private fun appendLog(event: PrivacyHoneypotEvent) {
        val events = ArrayDeque(parseLog())
        events.addFirst(event)
        while (events.size > LOG_LIMIT) {
            events.removeLast()
        }
        DataStore.privacyHoneypotLog = events.joinToString("\n", transform = ::serialize)
    }

    private fun serialize(event: PrivacyHoneypotEvent): String {
        return listOf(
            event.timestamp,
            event.transport,
            event.localPort,
            event.remotePort,
            event.uid,
            event.packageName.orEmpty(),
            event.classification,
        ).joinToString("\t")
    }

    private fun parseLine(line: String): PrivacyHoneypotEvent? {
        val parts = line.split('\t')
        if (parts.size < 7) return null
        return PrivacyHoneypotEvent(
            timestamp = parts[0].toLongOrNull() ?: return null,
            transport = parts[1],
            localPort = parts[2].toIntOrNull() ?: return null,
            remotePort = parts[3].toIntOrNull() ?: return null,
            uid = parts[4].toIntOrNull() ?: return null,
            packageName = parts[5].ifBlank { null },
            classification = parts[6],
        )
    }
}
