/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
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

package io.nekohasekai.sagernet.fmt.vkturn

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.queryParameter
import libexclavecore.Libexclavecore

private fun String?.toBooleanCompat(default: Boolean = false): Boolean {
    return when (this?.lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }
}

fun parseVkTurn(server: String): VkTurnBean {
    val link = Libexclavecore.parseURL(server)
    return VkTurnBean().applyDefaultValues().apply {
        serverAddress = link.host.ifEmpty { error("empty host") }
        serverPort = link.port.takeIf { it > 0 } ?: 56000
        name = link.fragment
        mode = when (link.queryParameter("mode")?.lowercase()) {
            "vless", "tcp" -> VkTurnBean.MODE_VLESS
            else -> VkTurnBean.MODE_WIREGUARD
        }
        provider = when (link.queryParameter("provider")?.lowercase()) {
            "yandex", "ya" -> VkTurnBean.PROVIDER_YANDEX
            "wb", "wildberries" -> VkTurnBean.PROVIDER_WB
            else -> VkTurnBean.PROVIDER_VK
        }
        callLink = link.queryParameter("call") ?: link.queryParameter("link") ?: ""
        turnHost = link.queryParameter("turn") ?: link.queryParameter("turnHost") ?: turnHost
        turnPort = link.queryParameter("turnPort")?.toIntOrNull()?.takeIf { it > 0 } ?: turnPort
        streams = link.queryParameter("n")?.toIntOrNull()?.takeIf { it > 0 } ?: streams
        udp = link.queryParameter("udp").toBooleanCompat(udp)
        debug = link.queryParameter("debug").toBooleanCompat(debug)
        manualCaptcha = link.queryParameter("manualCaptcha").toBooleanCompat(manualCaptcha)
        dns = link.queryParameter("dns") ?: dns
        allocsPerStream =
            link.queryParameter("allocsPerStream")?.toIntOrNull()?.takeIf { it > 0 } ?: allocsPerStream
        noDtls = link.queryParameter("noDtls").toBooleanCompat(noDtls)
        sessionId = link.queryParameter("sessionId") ?: sessionId
        protocolV1 = link.queryParameter("v1").toBooleanCompat(protocolV1)

        localAddress = link.queryParameter("address") ?: localAddress
        privateKey = link.queryParameter("privateKey") ?: privateKey
        peerPublicKey = link.queryParameter("publicKey") ?: peerPublicKey
        peerPreSharedKey = link.queryParameter("preSharedKey") ?: peerPreSharedKey
        mtu = link.queryParameter("mtu")?.toIntOrNull()?.takeIf { it > 0 } ?: mtu
        reserved = link.queryParameter("reserved") ?: reserved
        keepaliveInterval =
            link.queryParameter("keepalive")?.toIntOrNull()?.takeIf { it > 0 } ?: keepaliveInterval
        uuid = link.queryParameter("uuid") ?: uuid
    }
}

fun VkTurnBean.toUri(): String {
    val builder = Libexclavecore.newURL("vkturn").apply {
        setHostPort(serverAddress.ifEmpty { error("empty server address") }, serverPort)
        if (name.isNotEmpty()) fragment = name
    }
    builder.addQueryParameter("mode", if (mode == VkTurnBean.MODE_VLESS) "vless" else "wireguard")
    builder.addQueryParameter("provider", when (provider) {
        VkTurnBean.PROVIDER_YANDEX -> "yandex"
        VkTurnBean.PROVIDER_WB -> "wb"
        else -> "vk"
    })
    if (callLink.isNotEmpty()) builder.addQueryParameter("call", callLink)
    if (turnHost.isNotEmpty()) builder.addQueryParameter("turn", turnHost)
    builder.addQueryParameter("turnPort", turnPort.toString())
    builder.addQueryParameter("n", streams.toString())
    if (udp) builder.addQueryParameter("udp", "1")
    if (debug) builder.addQueryParameter("debug", "1")
    if (manualCaptcha) builder.addQueryParameter("manualCaptcha", "1")
    if (dns.isNotEmpty()) builder.addQueryParameter("dns", dns)
    if (allocsPerStream > 0) builder.addQueryParameter("allocsPerStream", allocsPerStream.toString())
    if (noDtls) builder.addQueryParameter("noDtls", "1")
    if (sessionId.isNotEmpty()) builder.addQueryParameter("sessionId", sessionId)
    if (protocolV1) builder.addQueryParameter("v1", "1")

    if (localAddress.isNotEmpty()) builder.addQueryParameter("address", localAddress)
    if (privateKey.isNotEmpty()) builder.addQueryParameter("privateKey", privateKey)
    if (peerPublicKey.isNotEmpty()) builder.addQueryParameter("publicKey", peerPublicKey)
    if (peerPreSharedKey.isNotEmpty()) builder.addQueryParameter("preSharedKey", peerPreSharedKey)
    if (mtu > 0) builder.addQueryParameter("mtu", mtu.toString())
    if (reserved.isNotEmpty()) builder.addQueryParameter("reserved", reserved)
    if (keepaliveInterval > 0) builder.addQueryParameter("keepalive", keepaliveInterval.toString())
    if (uuid.isNotEmpty()) builder.addQueryParameter("uuid", uuid)
    return builder.string
}
