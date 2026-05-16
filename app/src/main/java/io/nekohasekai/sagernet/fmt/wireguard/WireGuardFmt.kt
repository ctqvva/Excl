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
import java.util.Base64
import java.util.zip.Inflater
import kotlin.jvm.optionals.getOrNull

private const val AMNEZIA_WG_RESERVED_PREFIX = "EXAWG1\u0000"
private val AMNEZIA_OPTION_KEYS = listOf(
    "jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5"
)

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
    val normalized = name.lowercase()
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

private fun JsonObject.stringValue(vararg keys: String): String? {
    keys.forEach { key ->
        get(key)?.takeUnless { it.isJsonNull }?.let {
            if (it.isJsonPrimitive) return it.asJsonPrimitive.asString
        }
    }
    for ((actualKey, value) in entrySet()) {
        if (keys.any { it.equals(actualKey, ignoreCase = true) } && value.isJsonPrimitive) {
            return value.asJsonPrimitive.asString
        }
    }
    return null
}

private fun JsonObject.intValue(vararg keys: String): Int? {
    return stringValue(*keys)?.toIntOrNull()
}

private fun parseAmneziaWireGuardObject(config: JsonObject, fallbackName: String?, fallbackHost: String?): WireGuardBean? {
    val bean = config.stringValue("config")
        ?.takeIf { it.contains("[Interface]", ignoreCase = true) && it.contains("[Peer]", ignoreCase = true) }
        ?.let { parseWireGuardConfig(it).firstOrNull() }
        ?: WireGuardBean().applyDefaultValues()

    config.stringValue("client_ip", "Address", "address")?.let { bean.localAddress = it.replace(", ", "\n") }
    config.stringValue("client_priv_key", "PrivateKey", "private_key", "privateKey")?.let { bean.privateKey = it }
    config.stringValue("server_pub_key", "PublicKey", "peer_public_key", "peerPublicKey", "public_key")?.let { bean.peerPublicKey = it }
    config.stringValue("psk_key", "PresharedKey", "PreSharedKey", "pre_shared_key", "preshared_key")?.let { bean.peerPreSharedKey = it }
    config.intValue("mtu", "MTU")?.takeIf { it > 0 }?.let { bean.mtu = it }
    config.intValue("persistent_keep_alive", "PersistentKeepalive", "PersistentKeepAlive", "keepalive")?.let { bean.keepaliveInterval = it }

    config.stringValue("hostName", "hostname", "host", "server")?.let { bean.serverAddress = it }
    config.intValue("port", "serverPort")?.takeIf { it > 0 }?.let { bean.serverPort = it }
    if (bean.serverAddress.isBlank()) {
        fallbackHost?.let { bean.serverAddress = it }
    }

    AMNEZIA_OPTION_KEYS.forEach { key ->
        config.stringValue(key, key.replaceFirstChar { it.uppercase() }, key.uppercase())?.let {
            bean.putAmneziaOption(key, it)
        }
    }

    if (bean.name.isBlank()) {
        bean.name = fallbackName.orEmpty()
    }
    bean.initializeDefaultValues()
    return bean.takeIf {
        it.privateKey.isNotBlank() &&
            it.peerPublicKey.isNotBlank() &&
            it.serverAddress.isNotBlank() &&
            it.serverPort > 0
    }
}

private fun parseAmneziaContainer(root: JsonObject, container: JsonObject): List<WireGuardBean> {
    val fallbackName = root.stringValue("description", "name")
    val fallbackHost = root.stringValue("hostName", "hostname", "host")
    val protocolConfig = listOf("awg", "wireguard", "amnezia-awg", "amnezia-awg2")
        .firstNotNullOfOrNull { key -> container.get(key)?.takeIf { it.isJsonObject }?.asJsonObject }
        ?: return emptyList()
    val lastConfig = protocolConfig.stringValue("last_config", "lastConfig", "config")
    val beans = when {
        lastConfig.isNullOrBlank() -> listOfNotNull(parseAmneziaWireGuardObject(protocolConfig, fallbackName, fallbackHost))
        lastConfig.contains("[Interface]", ignoreCase = true) && lastConfig.contains("[Peer]", ignoreCase = true) -> {
            parseWireGuardConfig(lastConfig).onEach {
                if (it.name.isBlank()) it.name = fallbackName.orEmpty()
                if (it.serverAddress.isBlank()) fallbackHost?.let { host -> it.serverAddress = host }
            }
        }
        else -> {
            val lastObject = runCatching { parseJson(lastConfig, lenient = true).asJsonObject }.getOrNull()
            listOfNotNull(lastObject?.let { parseAmneziaWireGuardObject(it, fallbackName, fallbackHost) })
        }
    }
    return beans
}

fun parseAmneziaVpn(server: String): List<WireGuardBean> {
    val config = decodeAmneziaVpnPayload(server).trim()
    if (config.contains("[Interface]", ignoreCase = true) && config.contains("[Peer]", ignoreCase = true)) {
        return parseWireGuardConfig(config)
    }

    val root = parseJson(config, lenient = true).asJsonObject
    root.getArray("containers", ignoreCase = true)?.let { containers ->
        val preferred = root.stringValue("defaultContainer")
        val ordered = if (preferred == null) {
            containers
        } else {
            containers.sortedBy { container ->
                if (container.stringValue("container") == preferred) 0 else 1
            }
        }
        return ordered.flatMap { parseAmneziaContainer(root, it) }
    }

    if (root.stringValue("api_endpoint") != null && root.stringValue("api_key") != null) {
        error("Amnezia API vpn:// links need online key provisioning; import an exported AmneziaWG config or native container vpn:// key.")
    }

    return listOfNotNull(parseAmneziaWireGuardObject(root, root.stringValue("description", "name"), root.stringValue("hostName")))
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
        listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5").forEach { key ->
            (link.queryParameter(key)
                ?: link.queryParameter(key.replaceFirstChar { it.uppercase() })
                ?: link.queryParameter(key.uppercase()))?.let { putAmneziaOption(key, it) }
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
        fun readOption(name: String) = iface.getOr(name.replaceFirstChar { it.uppercase() }).getOrNull()
            ?: iface.getOr(name).getOrNull()
            ?: iface.getOr(name.uppercase()).getOrNull()
            ?: iface.getOr(name.lowercase()).getOrNull()
        localAddress = iface.getAllOr("Address").getOrNull()
            ?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: return beans
        privateKey = iface.getOr("PrivateKey").getOrNull() ?: return beans
        mtu = iface.getOr("MTU").getOrNull()?.toInt()?.takeIf { it > 0 } ?: 1420
        listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5").forEach {
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
