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
 * Command-line construction for the byedpi core. Logic ported from           *
 * romanvht/ByeByeDPI (GPLv3) ByeDpiProxyPreferences, adapted to per-profile  *
 * beans. The listener is always forced onto loopback: any --ip/-i option    *
 * in a custom command line is stripped and replaced.                         *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.byebyedpi

import io.nekohasekai.sagernet.fmt.LOCALHOST

import io.nekohasekai.sagernet.fmt.LOCALHOST

// Based on https://gist.github.com/raymyers/8077031
fun shellSplit(string: CharSequence): List<String> {
    val tokens: MutableList<String> = ArrayList()
    var quoteChar = ' '
    var escaping = false
    var quoting = false
    var lastCloseQuoteIndex = Int.MIN_VALUE
    var current = StringBuilder()

    for (i in string.indices) {
        val c = string[i]

        if (escaping) {
            current.append(c)
            escaping = false
        } else if (c == '\\' && quoting) {
            if (i + 1 < string.length && string[i + 1] == quoteChar) {
                escaping = true
            } else {
                current.append(c)
            }
        } else if (quoting && c == quoteChar) {
            quoting = false
            lastCloseQuoteIndex = i
        } else if (!quoting && (c == '\'' || c == '"')) {
            quoting = true
            quoteChar = c
        } else if (!quoting && Character.isWhitespace(c)) {
            if (current.isNotEmpty() || lastCloseQuoteIndex == i - 1) {
                tokens.add(current.toString())
                current = StringBuilder()
            }
        } else {
            current.append(c)
        }
    }

    if (current.isNotEmpty() || lastCloseQuoteIndex == string.length - 1) {
        tokens.add(current.toString())
    }

    return tokens
}

private val IP_KEYS = listOf("--ip", "-i")
private val PORT_KEYS = listOf("--port", "-p")
private val BLOCKED_FLAGS = setOf("--help", "--version", "-h", "-v")

private fun takesSeparateValue(token: String, keys: List<String>): Boolean {
    return keys.any { key ->
        if (key.startsWith("--")) token == key
        else token == key
    }
}

private fun stripListenOptions(tokens: List<String>): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < tokens.size) {
        val token = tokens[i]
        val isListenKey = (IP_KEYS + PORT_KEYS).any { key ->
            if (key.startsWith("--")) {
                token == key || token.startsWith("$key=")
            } else {
                token == key || (token.startsWith(key) && token.length > key.length)
            }
        }
        if (isListenKey) {
            // Skip a separate value too ("--ip 1.2.3.4" form).
            if ((token in IP_KEYS + PORT_KEYS) && i + 1 < tokens.size) i++
            i++
            continue
        }
        out.add(token)
        i++
    }
    return out
}

fun buildByeDpiArgs(
    bean: ByeByeDPIBean,
    listenPort: Int,
    listLookup: (String) -> String? = { null },
): Array<String> {
    return if (bean.useCommandLine) {
        buildCmdArgs(bean.commandLine, listenPort, listLookup)
    } else {
        buildUiArgs(bean, listenPort)
    }
}

private fun buildCmdArgs(
    commandLine: String,
    listenPort: Int,
    listLookup: (String) -> String?,
): Array<String> {
    val withLists = Regex("""\{list:([^}]+)\}""").replace(commandLine) { matchResult ->
        listLookup(matchResult.groupValues[1].trim()) ?: ""
    }
    val firstArgIndex = withLists.indexOf("-")
    val args = (if (firstArgIndex > 0) withLists.substring(firstArgIndex) else withLists).trim()
    val tokens = stripListenOptions(shellSplit(args)).filter { it !in BLOCKED_FLAGS }
    return arrayOf("ciadpi", "--ip", LOCALHOST, "--port", listenPort.toString()) + tokens
}

private fun buildUiArgs(bean: ByeByeDPIBean, listenPort: Int): Array<String> {
    val args = mutableListOf("ciadpi", "--ip", LOCALHOST, "--port", listenPort.toString())

    if (bean.maxConnections != 0) args.add("-c${bean.maxConnections}")
    if (bean.bufferSize != 0) args.add("-b${bean.bufferSize}")

    val protocols = buildList {
        if (bean.desyncHttps) add("t")
        if (bean.desyncHttp) add("h")
    }

    if (bean.hosts.isNotBlank()) {
        val hostStr = ":${bean.hosts.replace("\n", " ")}"
        when (bean.hostsMode) {
            ByeByeDPIBean.HOSTS_BLACKLIST -> {
                args.add("-H$hostStr")
                args.add("-An")
                if (protocols.isNotEmpty()) args.add("-K${protocols.joinToString(",")}")
            }
            ByeByeDPIBean.HOSTS_WHITELIST -> {
                if (protocols.isNotEmpty()) args.add("-K${protocols.joinToString(",")}")
                args.add("-H$hostStr")
            }
            else -> {}
        }
    } else {
        if (protocols.isNotEmpty()) args.add("-K${protocols.joinToString(",")}")
    }

    if (bean.defaultTtl != 0) args.add("-g${bean.defaultTtl}")
    if (bean.noDomain) args.add("-N")

    if (bean.splitPosition != 0) {
        val posArg = bean.splitPosition.toString() + if (bean.splitAtHost) "+h" else ""
        val option = when (bean.desyncMethod) {
            ByeByeDPIBean.DESYNC_SPLIT -> "-s"
            ByeByeDPIBean.DESYNC_DISORDER -> "-d"
            ByeByeDPIBean.DESYNC_OOB -> "-o"
            ByeByeDPIBean.DESYNC_DISOOB -> "-q"
            ByeByeDPIBean.DESYNC_FAKE -> "-f"
            else -> ""
        }
        if (option.isNotEmpty()) args.add("$option$posArg")
    }

    if (bean.desyncMethod == ByeByeDPIBean.DESYNC_FAKE) {
        if (bean.fakeTtl != 0) args.add("-t${bean.fakeTtl}")
        if (bean.fakeSni.isNotEmpty()) args.add("-n${bean.fakeSni}")
        if (bean.fakeOffset != 0) args.add("-O${bean.fakeOffset}")
    }

    if (bean.desyncMethod == ByeByeDPIBean.DESYNC_OOB ||
        bean.desyncMethod == ByeByeDPIBean.DESYNC_DISOOB
    ) {
        args.add("-e${bean.oobChar[0].code.toByte()}")
    }

    val modHttpFlags = buildList {
        if (bean.hostMixedCase) add("h")
        if (bean.domainMixedCase) add("d")
        if (bean.hostRemoveSpaces) add("r")
    }
    if (modHttpFlags.isNotEmpty()) args.add("-M${modHttpFlags.joinToString(",")}")

    if (bean.tlsRecordSplit && bean.tlsRecordSplitPosition != 0) {
        val tlsRecArg = bean.tlsRecordSplitPosition.toString() +
                if (bean.tlsRecordSplitAtSni) "+s" else ""
        args.add("-r$tlsRecArg")
    }

    if (bean.tcpFastOpen) args.add("-F")
    if (bean.dropSack) args.add("-Y")

    args.add("-An")

    if (bean.desyncUdp) {
        args.add("-Ku")
        if (bean.udpFakeCount != 0) args.add("-a${bean.udpFakeCount}")
        args.add("-An")
    }

    return args.toTypedArray()
}
