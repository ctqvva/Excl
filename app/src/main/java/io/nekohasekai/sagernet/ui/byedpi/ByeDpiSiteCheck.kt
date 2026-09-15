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
 * SOCKS connectivity checker for the ByeByeDPI proxy test. Ported from       *
 * romanvht/ByeByeDPI (GPLv3) SiteCheckUtils.                                 *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.byedpi

import android.os.Build
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class ByeDpiSiteCheck(
    private val proxyIp: String,
    private val proxyPort: Int,
) {

    suspend fun checkSitesAsync(
        sites: List<String>,
        requestsCount: Int,
        requestTimeout: Long,
        concurrentRequests: Int = 20,
        onSiteChecked: ((String, Int, Int) -> Unit)? = null,
    ): List<Pair<String, Int>> {
        val semaphore = Semaphore(concurrentRequests)
        return withContext(Dispatchers.IO) {
            sites.map { site ->
                async {
                    semaphore.withPermit {
                        val successCount = checkSiteAccess(site, requestsCount, requestTimeout)
                        onSiteChecked?.invoke(site, successCount, requestsCount)
                        site to successCount
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSiteAccess(
        site: String,
        requestsCount: Int,
        timeout: Long,
    ): Int = withContext(Dispatchers.IO) {
        var responseCount = 0

        val formattedUrl =
            if (site.startsWith("http://") || site.startsWith("https://")) site
            else "https://$site"
        val url = try {
            URL(formattedUrl)
        } catch (_: Exception) {
            Logs.e("Invalid proxy test URL: $formattedUrl")
            return@withContext 0
        }

        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))

        repeat(requestsCount) {
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = (timeout * 1000).toInt()
                connection.readTimeout = (timeout * 1000).toInt()
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Connection", "close")

                val responseCode = connection.responseCode
                val declaredLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }

                var actualLength = 0L
                try {
                    val inputStream =
                        if (responseCode in 200..299) connection.inputStream
                        else connection.errorStream
                    if (inputStream != null) {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        val limit = if (declaredLength > 0) declaredLength else 1024L * 1024
                        while (actualLength < limit) {
                            val remaining = limit - actualLength
                            val toRead =
                                if (remaining > buffer.size) buffer.size else remaining.toInt()
                            bytesRead = inputStream.read(buffer, 0, toRead)
                            if (bytesRead == -1) break
                            actualLength += bytesRead
                        }
                    }
                } catch (_: IOException) {
                    // Stream reading failed
                }

                if (declaredLength <= 0L || actualLength >= declaredLength) {
                    responseCount++
                } else {
                    Logs.w("Proxy test block detected for $site")
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
        }

        responseCount
    }
}
