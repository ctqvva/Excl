/******************************************************************************
 *                                                                            *
 * Copyright (C) 2024  dyhkwong                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.wireguard

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.getArray
import io.nekohasekai.sagernet.ktx.joinHostPort
import io.nekohasekai.sagernet.ktx.listByLineOrComma
import io.nekohasekai.sagernet.ktx.parseJson
import io.nekohasekai.sagernet.ktx.queryParameter
import libsagernetcore.Libsagernetcore
import com.sshtools.jini.INI
import com.sshtools.jini.INIWriter
import io.nekohasekai.sagernet.ktx.queryParameterNotBlank
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.Inflater
import kotlin.jvm.optionals.getOrNull

private const val AMNEZIA_WG_RESERVED_PREFIX = "EXAWG1\u0000"
private val AMNEZIA_OPTION_KEYS = listOf(
    "jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5"
)
private val AMNEZIA_OPTION_ALIASES = mapOf(
    "jc" to listOf("jc", "junkPacketCount", "junk_packet_count"),
    "jmin" to listOf("jmin", "junkPacketMinSize", "junk_packet_min_size"),
    "jmax" to listOf("jmax", "junkPacketMaxSize", "junk_packet_max_size"),
    "s1" to listOf("s1", "initPacketJunkSize", "init_packet_junk_size", "handshakeInitialPacketJunkSize"),
    "s2" to listOf("s2", "responsePacketJunkSize", "response_packet_junk_size", "handshakeResponsePacketJunkSize"),
    "s3" to listOf("s3", "cookieReplyPacketJunkSize", "cookie_reply_packet_junk_size", "underloadPacketJunkSize"),
    "s4" to listOf("s4", "transportPacketJunkSize", "transport_packet_junk_size"),
    "h1" to listOf("h1", "initPacketMagicHeader", "init_packet_magic_header", "handshakeInitialPacketMagicHeader"),
    "h2" to listOf("h2", "responsePacketMagicHeader", "response_packet_magic_header", "handshakeResponsePacketMagicHeader"),
    "h3" to listOf("h3", "cookieReplyPacketMagicHeader", "cookie_reply_packet_magic_header", "underloadPacketMagicHeader"),
    "h4" to listOf("h4", "transportPacketMagicHeader", "transport_packet_magic_header"),
    "i1" to listOf("i1", "specialJunk1", "special_junk_1", "specialJunkPacket1"),
    "i2" to listOf("i2", "specialJunk2", "special_junk_2", "specialJunkPacket2"),
    "i3" to listOf("i3", "specialJunk3", "special_junk_3", "specialJunkPacket3"),
    "i4" to listOf("i4", "specialJunk4", "special_junk_4", "specialJunkPacket4"),
    "i5" to listOf("i5", "specialJunk5", "special_junk_5", "specialJunkPacket5"),
)
private val AMNEZIA_OPTION_ALIAS_LOOKUP = AMNEZIA_OPTION_ALIASES
    .flatMap { (canonical, aliases) -> aliases.map { normalizeConfigKey(it) to canonical } }
    .toMap()

private fun normalizeConfigKey(key: String): String {
    return key.filter { it.isLetterOrDigit() }.lowercase()
}

private fun canonicalAmneziaOptionKey(key: String): String? {
    return AMNEZIA_OPTION_ALIAS_LOOKUP[normalizeConfigKey(key)]
}

private val X25519_P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
private val X25519_A24 = BigInteger.valueOf(121665)

private fun ByteArray.toLittleEndianBigInteger(): BigInteger {
    return BigInteger(1, reversedArray())
}

private fun BigInteger.modP(): BigInteger {
    return mod(X25519_P)
}

private fun BigInteger.toLittleEndian32(): ByteArray {
    val bigEndian = modP().toByteArray().dropWhile { it == 0.toByte() }
    return ByteArray(32).also { output ->
        bigEndian.takeLast(32).reversed().forEachIndexed { index, byte ->
            output[index] = byte
        }
    }
}

private fun x25519PublicKey(privateKey: ByteArray): ByteArray {
    val scalar = privateKey.clone()
    scalar[0] = (scalar[0].toInt() and 248).toByte()
    scalar[31] = ((scalar[31].toInt() and 127) or 64).toByte()

    val k = scalar.toLittleEndianBigInteger()
    val x1 = BigInteger.valueOf(9)
    var x2 = BigInteger.ONE
    var z2 = BigInteger.ZERO
    var x3 = x1
    var z3 = BigInteger.ONE
    var swap = 0

    for (position in 254 downTo 0) {
        val bit = if (k.testBit(position)) 1 else 0
        swap = swap xor bit
        if (swap == 1) {
            val tx = x2
            val tz = z2
            x2 = x3
            z2 = z3
            x3 = tx
            z3 = tz
        }
        swap = bit

        val a = (x2 + z2).modP()
        val aa = a.multiply(a).modP()
        val b = (x2 - z2).modP()
        val bb = b.multiply(b).modP()
        val e = (aa - bb).modP()
        val c = (x3 + z3).modP()
        val d = (x3 - z3).modP()
        val da = d.multiply(a).modP()
        val cb = c.multiply(b).modP()
        x3 = (da + cb).modP().let { it.multiply(it).modP() }
        z3 = (da - cb).modP().let { x1.multiply(it.multiply(it).modP()).modP() }
        x2 = aa.multiply(bb).modP()
        z2 = e.multiply((aa + X25519_A24.multiply(e)).modP()).modP()
    }

    if (swap == 1) {
        val tx = x2
        x2 = x3
        x3 = tx
        val tz = z2
        z2 = z3
        z3 = tz
    }

    return x2.multiply(z2.modInverse(X25519_P)).modP().toLittleEndian32()
}

private fun generateWireGuardKeyPair(): Pair<String, String> {
    val privateKey = ByteArray(32)
    SecureRandom().nextBytes(privateKey)
    privateKey[0] = (privateKey[0].toInt() and 248).toByte()
    privateKey[31] = ((privateKey[31].toInt() and 127) or 64).toByte()
    val publicKey = x25519PublicKey(privateKey)
    return Base64.getEncoder().encodeToString(privateKey) to Base64.getEncoder().encodeToString(publicKey)
}

private fun WireGuardBean.amneziaUapiLines(): List<String> {
    val lines = mutableListOf<String>()
    fun addInt(name: String, value: Int?) {
        value?.takeIf { it > 0 }?.let { lines.add("$name=$it") }
    }
    fun addString(name: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let { lines.add("$name=$it") }
    }
    addInt("jc", amneziaJc)
    addInt("jmin", amneziaJmin)
    addInt("jmax", amneziaJmax)
    addInt("s1", amneziaS1)
    addInt("s2", amneziaS2)
    addInt("s3", amneziaS3)
    addInt("s4", amneziaS4)
    addString("h1", amneziaH1)
    addString("h2", amneziaH2)
    addString("h3", amneziaH3)
    addString("h4", amneziaH4)
    addString("i1", amneziaI1)
    addString("i2", amneziaI2)
    addString("i3", amneziaI3)
    addString("i4", amneziaI4)
    addString("i5", amneziaI5)
    return lines
}

fun parseWireGuardReservedPayload(raw: String): List<Int>? {
    val values = raw.listByLineOrComma()
    if (values.size == 3) {
        val parsed = values.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
        return parsed
    }
    if (raw.isNotBlank()) {
        try {
            val decoded = Base64.getDecoder().decode(raw)
            if (decoded.size == 3) {
                return decoded.map { it.toInt() and 0xff }
            }
        } catch (_: Exception) {
        }
    }
    return null
}

fun WireGuardBean.toV2RayReservedPayload(): List<Int>? {
    val wireGuardReserved = parseWireGuardReservedPayload(reserved).orEmpty()
    val awgLines = amneziaUapiLines()
    if (awgLines.isEmpty()) return wireGuardReserved.takeIf { it.isNotEmpty() }
    require(wireGuardReserved.size <= 255) { "WireGuard reserved payload is too long" }
    return buildList {
        addAll(AMNEZIA_WG_RESERVED_PREFIX.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff })
        add(wireGuardReserved.size)
        addAll(wireGuardReserved)
        addAll(awgLines.joinToString(separator = "\n", postfix = "\n").toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff })
    }
}

fun WireGuardBean.putAmneziaOption(name: String, value: String?) {
    val normalized = canonicalAmneziaOptionKey(name) ?: name.lowercase()
    val intValue = value?.toIntOrNull()
    when (normalized) {
        "jc" -> amneziaJc = intValue
        "jmin" -> amneziaJmin = intValue
        "jmax" -> amneziaJmax = intValue
        "s1" -> amneziaS1 = intValue
        "s2" -> amneziaS2 = intValue
        "s3" -> amneziaS3 = intValue
        "s4" -> amneziaS4 = intValue
        "h1" -> amneziaH1 = value.orEmpty()
        "h2" -> amneziaH2 = value.orEmpty()
        "h3" -> amneziaH3 = value.orEmpty()
        "h4" -> amneziaH4 = value.orEmpty()
        "i1" -> amneziaI1 = value.orEmpty()
        "i2" -> amneziaI2 = value.orEmpty()
        "i3" -> amneziaI3 = value.orEmpty()
        "i4" -> amneziaI4 = value.orEmpty()
        "i5" -> amneziaI5 = value.orEmpty()
    }
}

private fun decodeAmneziaVpnPayload(server: String): String {
    val trimmed = server.trim()
    val encoded = if (trimmed.startsWith("vpn://", ignoreCase = true)) {
        trimmed.substring("vpn://".length)
    } else {
        trimmed
    }.trim()
    val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
    val decoded = runCatching {
        Base64.getUrlDecoder().decode(padded)
    }.getOrElse {
        Base64.getDecoder().decode(padded)
    }
    val uncompressed = inflateQtPayload(decoded)
        ?: inflatePayload(decoded)
        ?: decoded
    return String(uncompressed, Charsets.UTF_8)
}

private fun inflateQtPayload(bytes: ByteArray): ByteArray? {
    if (bytes.size <= 4) return null
    val expectedSize = (
        (bytes[0].toInt() and 0xff) shl 24
            or ((bytes[1].toInt() and 0xff) shl 16)
            or ((bytes[2].toInt() and 0xff) shl 8)
            or (bytes[3].toInt() and 0xff)
    )
    return inflatePayload(bytes.copyOfRange(4, bytes.size))?.takeIf {
        expectedSize <= 0 || it.size == expectedSize
    }
}

private fun inflatePayload(bytes: ByteArray): ByteArray? = runCatching {
    val inflater = Inflater()
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    inflater.setInput(bytes)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0) {
            return@runCatching null
        } else {
            output.write(buffer, 0, count)
        }
    }
    inflater.end()
    output.toByteArray()
}.getOrNull()

private fun JsonObject.elementValue(vararg keys: String): JsonElement? {
    keys.forEach { key ->
        get(key)?.takeUnless { it.isJsonNull }?.let { return it }
    }
    val normalizedKeys = keys.map { normalizeConfigKey(it) }.toSet()
    for ((actualKey, value) in entrySet()) {
        if (normalizeConfigKey(actualKey) in normalizedKeys && !value.isJsonNull) {
            return value
        }
    }
    return null
}

private fun JsonObject.stringValue(vararg keys: String): String? {
    val element = elementValue(*keys) ?: return null
    return when {
        element.isJsonPrimitive -> element.asJsonPrimitive.asString
        element.isJsonArray -> element.asJsonArray
            .mapNotNull { item -> item.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.asString }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        else -> null
    }
}

private fun JsonObject.objectValue(vararg keys: String): JsonObject? {
    return elementValue(*keys)?.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.intValue(vararg keys: String): Int? {
    return stringValue(*keys)?.toIntOrNull()
}

private fun JsonObject.amneziaOptionValue(canonicalKey: String): String? {
    val aliases = AMNEZIA_OPTION_ALIASES[canonicalKey].orEmpty()
    return stringValue(*aliases.toTypedArray())
}

private fun splitAmneziaEndpoint(raw: String): Pair<String, Int?> {
    val endpoint = raw.trim()
        .removePrefix("udp://")
        .removePrefix("tcp://")
        .removePrefix("UDP://")
        .removePrefix("TCP://")
    if (endpoint.startsWith("[")) {
        val end = endpoint.indexOf(']')
        if (end > 0) {
            val host = endpoint.substring(1, end)
            val port = endpoint.substring(end + 1).removePrefix(":").toIntOrNull()
            return host to port
        }
    }
    val colon = endpoint.lastIndexOf(':')
    if (colon > 0) {
        val port = endpoint.substring(colon + 1).toIntOrNull()
        if (port != null) {
            return endpoint.substring(0, colon).removePrefix("[").removeSuffix("]") to port
        }
    }
    return endpoint.removePrefix("[").removeSuffix("]") to null
}

private fun parseAmneziaEmbeddedConfig(
    raw: String,
    fallbackName: String?,
    fallbackHost: String?,
    fallbackPrivateKey: String? = null,
): List<WireGuardBean> {
    val config = raw.trim().takeIf { it.isNotBlank() } ?: return emptyList()
    if (config.contains("[Interface]", ignoreCase = true) && config.contains("[Peer]", ignoreCase = true)) {
        return parseWireGuardConfig(config).onEach {
            if (it.name.isBlank()) it.name = fallbackName.orEmpty()
            if (it.serverAddress.isBlank()) fallbackHost?.let { host -> it.serverAddress = host }
            if (it.privateKey.isBlank()) fallbackPrivateKey?.let { privateKey -> it.privateKey = privateKey }
        }
    }
    val jsonElement = runCatching { parseJson(config, lenient = true) }.getOrNull() ?: return emptyList()
    val jsonObject = when {
        jsonElement.isJsonObject -> jsonElement.asJsonObject
        jsonElement.isJsonPrimitive -> {
            val unquoted = jsonElement.asJsonPrimitive.asString
            if (unquoted == config) return emptyList()
            return parseAmneziaEmbeddedConfig(unquoted, fallbackName, fallbackHost, fallbackPrivateKey)
        }
        else -> return emptyList()
    }
    return parseAmneziaJsonObject(jsonObject, fallbackName, fallbackHost, fallbackPrivateKey)
}

private fun distinctAmneziaBeans(beans: List<WireGuardBean>): List<WireGuardBean> {
    return beans.distinctBy {
        listOf(
            it.name,
            it.privateKey,
            it.peerPublicKey,
            it.serverAddress,
            it.serverPort,
            it.localAddress,
        ).joinToString("|")
    }
}

private fun parseAmneziaWireGuardObject(
    config: JsonObject,
    fallbackName: String?,
    fallbackHost: String?,
    fallbackPrivateKey: String? = null,
): WireGuardBean? {
    val iface = config.objectValue("Interface", "interface", "client", "clientConfig", "device")
    val peer = config.objectValue("Peer", "peer", "server", "serverConfig")
    fun readString(vararg keys: String): String? {
        return listOf(iface, peer, config).firstNotNullOfOrNull { scope -> scope?.stringValue(*keys) }
    }
    fun readPeerString(vararg keys: String): String? {
        return listOf(peer, config).firstNotNullOfOrNull { scope -> scope?.stringValue(*keys) }
    }
    fun readInt(vararg keys: String): Int? {
        return listOf(iface, peer, config).firstNotNullOfOrNull { scope -> scope?.intValue(*keys) }
    }

    val bean = config.stringValue("config", "last_config", "lastConfig", "wgConfig", "wireguardConfig", "awgConfig")
        ?.takeIf { it.contains("[Interface]", ignoreCase = true) && it.contains("[Peer]", ignoreCase = true) }
        ?.let { parseWireGuardConfig(it).firstOrNull() }
        ?: WireGuardBean().applyDefaultValues()

    readString(
        "client_ip",
        "clientIp",
        "client_address",
        "clientAddress",
        "Address",
        "address",
        "addresses",
        "ip",
    )?.let {
        if (bean.localAddress.isBlank()) bean.localAddress = it.replace(", ", "\n").replace(",", "\n")
    }
    readString(
        "client_priv_key",
        "clientPrivKey",
        "clientPrivateKey",
        "PrivateKey",
        "private_key",
        "privateKey",
    )?.let { bean.privateKey = it }
    if (bean.privateKey.isBlank()) {
        fallbackPrivateKey?.let { bean.privateKey = it }
    }
    readPeerString(
        "server_pub_key",
        "serverPubKey",
        "serverPublicKey",
        "PublicKey",
        "public_key",
        "publicKey",
        "peer_public_key",
        "peerPublicKey",
    )?.let { bean.peerPublicKey = it }
    readPeerString(
        "psk_key",
        "pskKey",
        "PresharedKey",
        "PreSharedKey",
        "pre_shared_key",
        "preshared_key",
        "preSharedKey",
        "presharedKey",
    )?.let { bean.peerPreSharedKey = it }
    readInt("mtu", "MTU")?.takeIf { it > 0 }?.let { bean.mtu = it }
    readInt("persistent_keep_alive", "persistentKeepAlive", "PersistentKeepalive", "PersistentKeepAlive", "keepalive", "keepAlive")?.let {
        bean.keepaliveInterval = it
    }

    readPeerString("Endpoint", "endpoint")?.let { endpoint ->
        val (host, port) = splitAmneziaEndpoint(endpoint)
        if (host.isNotBlank()) bean.serverAddress = host
        port?.takeIf { it > 0 }?.let { bean.serverPort = it }
    }
    readPeerString("hostName", "hostname", "host", "serverHost", "server_host", "server", "serverIp", "server_ip")?.let { host ->
        val (endpointHost, endpointPort) = splitAmneziaEndpoint(host)
        if (endpointHost.isNotBlank()) bean.serverAddress = endpointHost
        endpointPort?.takeIf { it > 0 }?.let { bean.serverPort = it }
    }
    readInt("port", "serverPort", "server_port", "endpointPort", "endpoint_port")?.takeIf { it > 0 }?.let { bean.serverPort = it }
    if (bean.serverAddress.isBlank()) {
        fallbackHost?.let { bean.serverAddress = it }
    }

    AMNEZIA_OPTION_KEYS.forEach { key ->
        listOf(iface, peer, config, config.objectValue("amnezia", "amneziaWG", "amnezia_wg", "amnezia-wg", "awg", "obfuscation", "options"))
            .firstNotNullOfOrNull { scope -> scope?.amneziaOptionValue(key) }
            ?.let {
                bean.putAmneziaOption(key, it)
            }
    }

    bean.name = bean.name.ifBlank {
        readString("description", "name", "profileName", "profile_name") ?: fallbackName.orEmpty()
    }
    bean.initializeDefaultValues()
    return bean.takeIf {
        it.privateKey.isNotBlank() &&
            it.peerPublicKey.isNotBlank() &&
            it.serverAddress.isNotBlank() &&
            it.serverPort > 0
    }
}

private fun parseAmneziaJsonObject(
    root: JsonObject,
    fallbackName: String?,
    fallbackHost: String?,
    fallbackPrivateKey: String? = null,
): List<WireGuardBean> {
    val localName = root.stringValue("description", "name", "profileName", "profile_name") ?: fallbackName
    val localHost = root.stringValue("hostName", "hostname", "host", "serverHost", "server_host") ?: fallbackHost
    val beans = mutableListOf<WireGuardBean>()

    root.getArray("containers", ignoreCase = true)?.let { containers ->
        val preferred = root.stringValue("defaultContainer")
        val ordered = if (preferred == null) {
            containers
        } else {
            containers.sortedBy { container ->
                if (container.stringValue("container", "name", "id") == preferred) 0 else 1
            }
        }
        beans += ordered.flatMap { parseAmneziaContainer(root, it, fallbackPrivateKey) }
    }

    beans += listOfNotNull(parseAmneziaWireGuardObject(root, localName, localHost, fallbackPrivateKey))

    listOf(
        "last_config",
        "lastConfig",
        "config",
        "wgConfig",
        "wireguardConfig",
        "awgConfig",
        "rawConfig",
        "configText",
    ).forEach { key ->
        root.stringValue(key)?.let { beans += parseAmneziaEmbeddedConfig(it, localName, localHost, fallbackPrivateKey) }
    }

    for ((_, value) in root.entrySet()) {
        when {
            value.isJsonObject -> beans += parseAmneziaJsonObject(value.asJsonObject, localName, localHost, fallbackPrivateKey)
            value.isJsonArray -> value.asJsonArray.forEach { item ->
                when {
                    item.isJsonObject -> beans += parseAmneziaJsonObject(item.asJsonObject, localName, localHost, fallbackPrivateKey)
                    item.isJsonPrimitive -> beans += parseAmneziaEmbeddedConfig(item.asJsonPrimitive.asString, localName, localHost, fallbackPrivateKey)
                }
            }
            value.isJsonPrimitive -> {
                val text = value.asJsonPrimitive.asString
                if (
                    text.contains("[Interface]", ignoreCase = true) ||
                    text.trim().startsWith("{")
                ) {
                    beans += parseAmneziaEmbeddedConfig(text, localName, localHost, fallbackPrivateKey)
                }
            }
        }
    }

    return distinctAmneziaBeans(beans)
}

private fun parseAmneziaContainer(
    root: JsonObject,
    container: JsonObject,
    fallbackPrivateKey: String? = null,
): List<WireGuardBean> {
    val fallbackName = root.stringValue("description", "name")
    val fallbackHost = root.stringValue("hostName", "hostname", "host")
    val protocolConfig = listOf("awg", "wireguard", "amnezia-awg", "amnezia-awg2")
        .firstNotNullOfOrNull { key -> container.objectValue(key) }
        ?: return emptyList()
    val lastConfig = protocolConfig.stringValue("last_config", "lastConfig", "config")
    val beans = when {
        lastConfig.isNullOrBlank() -> listOfNotNull(parseAmneziaWireGuardObject(protocolConfig, fallbackName, fallbackHost, fallbackPrivateKey))
        lastConfig.contains("[Interface]", ignoreCase = true) && lastConfig.contains("[Peer]", ignoreCase = true) -> {
            parseWireGuardConfig(lastConfig).onEach {
                if (it.name.isBlank()) it.name = fallbackName.orEmpty()
                if (it.serverAddress.isBlank()) fallbackHost?.let { host -> it.serverAddress = host }
                if (it.privateKey.isBlank()) fallbackPrivateKey?.let { privateKey -> it.privateKey = privateKey }
            }
        }
        else -> {
            val lastObject = runCatching { parseJson(lastConfig, lenient = true).asJsonObject }.getOrNull()
            lastObject?.let { parseAmneziaJsonObject(it, fallbackName, fallbackHost, fallbackPrivateKey) }.orEmpty()
        }
    }
    return distinctAmneziaBeans(beans)
}

private fun provisionAmneziaApiConfig(root: JsonObject): List<WireGuardBean> {
    val endpoint = root.stringValue("api_endpoint", "apiEndpoint")
        ?: error("Amnezia API vpn:// payload is missing api_endpoint.")
    val apiKey = root.stringValue("api_key", "apiKey")
        ?: error("Amnezia API vpn:// payload is missing api_key.")
    val (privateKey, publicKey) = generateWireGuardKeyPair()
    val requestJson = """
        {"public_key":"$publicKey","uuid":"${UUID.randomUUID()}"}
    """.trimIndent()

    val response = (URL(endpoint).openConnection() as HttpURLConnection).run {
        connectTimeout = 15_000
        readTimeout = 20_000
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Api-Key $apiKey")
        outputStream.use { output ->
            output.write(requestJson.toByteArray(Charsets.UTF_8))
        }
        val body = if (responseCode in 200..299) {
            inputStream.bufferedReader().use { it.readText() }
        } else {
            errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        if (responseCode !in 200..299) {
            error("Amnezia API provisioning failed: HTTP $responseCode ${body.take(160)}")
        }
        body
    }.trim()

    val fallbackName = root.stringValue("description", "name")
    val fallbackHost = root.stringValue("hostName", "hostname", "host")
    val jsonElement = parseJson(response, lenient = true)
    val beans = when {
        jsonElement.isJsonObject -> parseAmneziaJsonObject(jsonElement.asJsonObject, fallbackName, fallbackHost, privateKey)
        jsonElement.isJsonPrimitive -> parseAmneziaEmbeddedConfig(jsonElement.asJsonPrimitive.asString, fallbackName, fallbackHost, privateKey)
        else -> emptyList()
    }.onEach {
        if (it.privateKey.isBlank()) it.privateKey = privateKey
    }
    if (beans.isEmpty()) {
        error("Amnezia API provisioning returned no WireGuard or AmneziaWG profile.")
    }
    return beans
}

fun parseAmneziaVpn(server: String): List<WireGuardBean> {
    val config = decodeAmneziaVpnPayload(server).trim()
    if (config.contains("[Interface]", ignoreCase = true) && config.contains("[Peer]", ignoreCase = true)) {
        return parseWireGuardConfig(config)
    }

    val root = parseJson(config, lenient = true).asJsonObject
    val beans = parseAmneziaJsonObject(root, root.stringValue("description", "name"), root.stringValue("hostName"))
    if (beans.isNotEmpty()) {
        return beans
    }
    if (root.stringValue("api_endpoint", "apiEndpoint") != null && root.stringValue("api_key", "apiKey") != null) {
        return provisionAmneziaApiConfig(root)
    }
    error("Unsupported Amnezia vpn:// payload: no WireGuard or AmneziaWG profile found.")
}

fun parseWireGuard(server: String): WireGuardBean {
    val link = Libsagernetcore.parseURL(server)
    return WireGuardBean().apply {
        serverAddress = link.host.ifEmpty { error("empty host") }
        serverPort = link.port.takeIf { it > 0 } ?: 51820
        if (link.username.isNotEmpty()) {
            // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L126-L148
            privateKey = link.username.replace('_', '/').replace('-', '+')
            if (privateKey.length == 43) privateKey += "="
            // v2rayNG style link
            // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L75
            localAddress = "10.0.0.1/32\nfd59:7153:2388:b5fd:0000:0000:0000:0001/128"
        }
        (link.queryParameter("privatekey") ?: link.queryParameter("privateKey")) ?.let {
            if (it.length == 64) {
                privateKey = Base64.getEncoder().encodeToString(it.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            } else {
                privateKey = it.replace('_', '/').replace('-', '+')
                if (privateKey.length == 43) privateKey += "="
            }
        }
        (link.queryParameter("address") ?: link.queryParameter("ip")) ?.takeIf { it.isNotEmpty() }?.also {
            localAddress = it.split(",").joinToString("\n")
        }
        (link.queryParameter("publickey") ?: link.queryParameter("publicKey")) ?.let {
            if (it.length == 64) {
                peerPublicKey = Base64.getEncoder().encodeToString(it.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            } else {
                peerPublicKey = it.replace('_', '/').replace('-', '+')
                if (peerPublicKey.length == 43) peerPublicKey += "="
            }
        }
        (link.queryParameterNotBlank("presharedkey") ?: link.queryParameterNotBlank("preSharedKey")) ?.let {
            if (peerPreSharedKey.length == 43) peerPreSharedKey += "="
            if (it.length == 64) {
                peerPreSharedKey = Base64.getEncoder().encodeToString(it.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            } else {
                peerPreSharedKey = it.replace('_', '/').replace('-', '+')
                if (peerPreSharedKey.length == 43) peerPreSharedKey += "="
            }
        }
        link.queryParameter("mtu")?.toIntOrNull()?.takeIf { it > 0 }?.let {
            mtu = it
        }
        link.queryParameter("reserved")?.let {
            reserved = it
        }
        AMNEZIA_OPTION_KEYS.forEach { key ->
            AMNEZIA_OPTION_ALIASES[key].orEmpty()
                .flatMap { alias ->
                    listOf(alias, alias.replaceFirstChar { it.uppercase() }, alias.uppercase(), alias.lowercase())
                }
                .firstNotNullOfOrNull { alias -> link.queryParameter(alias) }
                ?.let { putAmneziaOption(key, it) }
        }
        link.fragment?.let {
            name = it
        }
    }
}

fun parseWireGuardConfig(conf: String): List<WireGuardBean> {
    val beans = mutableListOf<WireGuardBean>()
    val ini = try {
        INI.fromString(conf)
    } catch (_: Exception) {
        return beans
    }
    val iface = ini.sectionOr("Interface").getOrNull() ?: return beans
    val wgBean = WireGuardBean().apply {
        fun readOption(name: String): String? {
            return AMNEZIA_OPTION_ALIASES[name].orEmpty()
                .flatMap { alias ->
                    listOf(alias, alias.replaceFirstChar { it.uppercase() }, alias.uppercase(), alias.lowercase())
                }
                .firstNotNullOfOrNull { alias -> iface.getOr(alias).getOrNull() }
        }
        localAddress = iface.getAllOr("Address").getOrNull()
            ?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: return beans
        privateKey = iface.getOr("PrivateKey").getOrNull() ?: return beans
        mtu = iface.getOr("MTU").getOrNull()?.toInt()?.takeIf { it > 0 } ?: 1420
        AMNEZIA_OPTION_KEYS.forEach {
            putAmneziaOption(it, readOption(it))
        }
    }
    val peers = ini.allSectionsOr("Peer").getOrNull() ?: return beans
    for (peer in peers) {
        val endpoint = peer.getOr("Endpoint").getOrNull()
        if (endpoint.isNullOrEmpty() || !endpoint.contains(":")) {
            continue
        }
        beans.add(wgBean.applyDefaultValues().clone().apply {
            serverAddress = endpoint.substringBeforeLast(":").removePrefix("[").removeSuffix("]").ifEmpty { error("empty host") }
            serverPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
            peerPublicKey = peer.getOr("PublicKey").getOrNull() ?: continue
            peerPreSharedKey = peer.getOr("PreSharedKey").getOrNull()
            keepaliveInterval = peer.getOr("PersistentKeepalive").getOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        })
    }
    return beans
}

fun WireGuardBean.toConf(): String {
    val ini = INI.create()
    val iface = ini.create("Interface")
    iface.put("Address", localAddress.listByLineOrComma())
    if (mtu > 0) {
        iface.put("MTU", mtu)
    }
    iface.put("PrivateKey", privateKey.ifEmpty { error("empty private key") })
    val peer = ini.create("Peer")
    peer.put("Endpoint", joinHostPort(serverAddress.ifEmpty { error("empty server address") }, serverPort))
    peer.put("PublicKey", peerPublicKey.ifEmpty { error("empty peer public key") })
    if (peerPreSharedKey.isNotEmpty()) {
        peer.put("PreSharedKey", peerPreSharedKey)
    }
    if (keepaliveInterval > 0) {
        peer.put("PersistentKeepalive", keepaliveInterval)
    }
    fun putAmnezia(name: String, value: Int?) {
        value?.takeIf { it > 0 }?.let { iface.put(name.replaceFirstChar { c -> c.uppercase() }, it) }
    }
    fun putAmnezia(name: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let { iface.put(name.replaceFirstChar { c -> c.uppercase() }, it) }
    }
    putAmnezia("jc", amneziaJc)
    putAmnezia("jmin", amneziaJmin)
    putAmnezia("jmax", amneziaJmax)
    putAmnezia("s1", amneziaS1)
    putAmnezia("s2", amneziaS2)
    putAmnezia("s3", amneziaS3)
    putAmnezia("s4", amneziaS4)
    putAmnezia("h1", amneziaH1)
    putAmnezia("h2", amneziaH2)
    putAmnezia("h3", amneziaH3)
    putAmnezia("h4", amneziaH4)
    putAmnezia("i1", amneziaI1)
    putAmnezia("i2", amneziaI2)
    putAmnezia("i3", amneziaI3)
    putAmnezia("i4", amneziaI4)
    putAmnezia("i5", amneziaI5)
    val conf = StringWriter()
    INIWriter.Builder()
        .withIndent(0)
        .withStringQuoteMode(INIWriter.StringQuoteMode.NEVER)
        .build()
        .write(ini, conf)
    return conf.toString()
}
