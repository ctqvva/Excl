/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.byebyedpi

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.queryParameter
import libexclavecore.Libexclavecore

private fun String?.toIntCompat(default: Int): Int {
    return this?.toIntOrNull() ?: default
}

private fun String?.toBooleanCompat(default: Boolean = false): Boolean {
    return when (this?.lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }
}

fun parseByeByeDPI(server: String): ByeByeDPIBean {
    val link = Libexclavecore.parseURL(server)
    return ByeByeDPIBean().applyDefaultValues().apply {
        name = link.fragment
        useCommandLine = link.queryParameter("cmd")?.toBooleanCompat(true) ?: true
        commandLine = link.queryParameter("args") ?: commandLine
        maxConnections = link.queryParameter("c")?.toIntCompat(maxConnections) ?: maxConnections
        bufferSize = link.queryParameter("b")?.toIntCompat(bufferSize) ?: bufferSize
        defaultTtl = link.queryParameter("ttl")?.toIntCompat(defaultTtl) ?: defaultTtl
        noDomain = link.queryParameter("noDomain")?.toBooleanCompat(noDomain) ?: noDomain
        desyncHttp = link.queryParameter("http")?.toBooleanCompat(desyncHttp) ?: desyncHttp
        desyncHttps = link.queryParameter("https")?.toBooleanCompat(desyncHttps) ?: desyncHttps
        desyncUdp = link.queryParameter("udp")?.toBooleanCompat(desyncUdp) ?: desyncUdp
        desyncMethod = when (link.queryParameter("method")?.lowercase()) {
            "none" -> ByeByeDPIBean.DESYNC_NONE
            "split" -> ByeByeDPIBean.DESYNC_SPLIT
            "disorder" -> ByeByeDPIBean.DESYNC_DISORDER
            "fake" -> ByeByeDPIBean.DESYNC_FAKE
            "disoob" -> ByeByeDPIBean.DESYNC_DISOOB
            "oob" -> ByeByeDPIBean.DESYNC_OOB
            else -> link.queryParameter("method")?.toIntOrNull() ?: desyncMethod
        }
        splitPosition = link.queryParameter("split")?.toIntCompat(splitPosition) ?: splitPosition
        splitAtHost = link.queryParameter("splitHost")?.toBooleanCompat(splitAtHost) ?: splitAtHost
        fakeTtl = link.queryParameter("fakeTtl")?.toIntCompat(fakeTtl) ?: fakeTtl
        fakeSni = link.queryParameter("fakeSni") ?: fakeSni
        oobChar = link.queryParameter("oob") ?: oobChar
        hostMixedCase = link.queryParameter("mixHost")?.toBooleanCompat(hostMixedCase) ?: hostMixedCase
        domainMixedCase =
            link.queryParameter("mixDomain")?.toBooleanCompat(domainMixedCase) ?: domainMixedCase
        hostRemoveSpaces =
            link.queryParameter("rmSpaces")?.toBooleanCompat(hostRemoveSpaces) ?: hostRemoveSpaces
        tlsRecordSplit =
            link.queryParameter("tlsrec")?.toBooleanCompat(tlsRecordSplit) ?: tlsRecordSplit
        tlsRecordSplitPosition =
            link.queryParameter("tlsrecPos")?.toIntCompat(tlsRecordSplitPosition)
                ?: tlsRecordSplitPosition
        tlsRecordSplitAtSni =
            link.queryParameter("tlsrecSni")?.toBooleanCompat(tlsRecordSplitAtSni)
                ?: tlsRecordSplitAtSni
        hostsMode = link.queryParameter("hostsMode")?.toIntOrNull() ?: hostsMode
        hosts = link.queryParameter("hosts") ?: hosts
        tcpFastOpen = link.queryParameter("tfo")?.toBooleanCompat(tcpFastOpen) ?: tcpFastOpen
        udpFakeCount =
            link.queryParameter("udpFake")?.toIntCompat(udpFakeCount) ?: udpFakeCount
        dropSack = link.queryParameter("dropSack")?.toBooleanCompat(dropSack) ?: dropSack
        fakeOffset = link.queryParameter("fakeOff")?.toIntCompat(fakeOffset) ?: fakeOffset
    }
}

fun ByeByeDPIBean.toUri(): String {
    val builder = Libexclavecore.newURL("byebyedpi").apply {
        if (name.isNotEmpty()) fragment = name
    }
    if (!useCommandLine) builder.addQueryParameter("cmd", "0")
    if (useCommandLine && commandLine.isNotEmpty()) builder.addQueryParameter("args", commandLine)
    builder.addQueryParameter("c", maxConnections.toString())
    builder.addQueryParameter("b", bufferSize.toString())
    if (defaultTtl != 0) builder.addQueryParameter("ttl", defaultTtl.toString())
    if (noDomain) builder.addQueryParameter("noDomain", "1")
    if (!desyncHttp) builder.addQueryParameter("http", "0")
    if (!desyncHttps) builder.addQueryParameter("https", "0")
    if (!desyncUdp) builder.addQueryParameter("udp", "0")
    builder.addQueryParameter("method", desyncMethod.toString())
    builder.addQueryParameter("split", splitPosition.toString())
    if (splitAtHost) builder.addQueryParameter("splitHost", "1")
    if (desyncMethod == ByeByeDPIBean.DESYNC_FAKE) {
        builder.addQueryParameter("fakeTtl", fakeTtl.toString())
        if (fakeSni.isNotEmpty()) builder.addQueryParameter("fakeSni", fakeSni)
        if (fakeOffset != 0) builder.addQueryParameter("fakeOff", fakeOffset.toString())
    }
    if (desyncMethod == ByeByeDPIBean.DESYNC_OOB || desyncMethod == ByeByeDPIBean.DESYNC_DISOOB) {
        if (oobChar.isNotEmpty()) builder.addQueryParameter("oob", oobChar)
    }
    if (hostMixedCase) builder.addQueryParameter("mixHost", "1")
    if (domainMixedCase) builder.addQueryParameter("mixDomain", "1")
    if (hostRemoveSpaces) builder.addQueryParameter("rmSpaces", "1")
    if (tlsRecordSplit) {
        builder.addQueryParameter("tlsrec", "1")
        builder.addQueryParameter("tlsrecPos", tlsRecordSplitPosition.toString())
        if (tlsRecordSplitAtSni) builder.addQueryParameter("tlsrecSni", "1")
    }
    if (hostsMode != ByeByeDPIBean.HOSTS_DISABLE) {
        builder.addQueryParameter("hostsMode", hostsMode.toString())
        if (hosts.isNotEmpty()) builder.addQueryParameter("hosts", hosts)
    }
    if (tcpFastOpen) builder.addQueryParameter("tfo", "1")
    if (desyncUdp && udpFakeCount != 1) builder.addQueryParameter("udpFake", udpFakeCount.toString())
    if (dropSack) builder.addQueryParameter("dropSack", "1")
    return builder.string
}
