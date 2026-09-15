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
 * Domain-list store for ByeByeDPI `{list:Name}` expansion and the proxy     *
 * test. Ported from romanvht/ByeByeDPI (GPLv3) DomainListUtils.              *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.byebyedpi

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale

data class ByeDpiDomainList(
    val id: String,
    val name: String,
    val domains: List<String>,
    val isActive: Boolean = false,
    val isBuiltIn: Boolean = false,
    val isDeleted: Boolean = false,
    val isModified: Boolean = false,
)

object ByeDpiDomainLists {
    private const val DOMAIN_LISTS_FILE = "byedpi_domain_lists.json"

    private val gson = Gson()

    private fun defaultActiveIds(): Set<String> = when (Locale.getDefault().language) {
        "tr" -> setOf("türkiye", "discord")
        else -> setOf("youtube", "googlevideo")
    }

    fun syncLists(context: Context) {
        val currentLists = getAllLists(context).toMutableList()
        val builtInMap = currentLists.filter { it.isBuiltIn }.associateBy { it.id }
        val assetFiles = context.assets.list("")?.filter {
            it.startsWith("proxytest_") && it.endsWith(".sites")
        } ?: emptyList()
        val newBuiltInIds = mutableSetOf<String>()

        for (assetFile in assetFiles) {
            val id = assetFile.removePrefix("proxytest_").removeSuffix(".sites")
            newBuiltInIds.add(id)
            val domains = context.assets.open(assetFile)
                .bufferedReader()
                .useLines { it.toList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val existing = builtInMap[id]
            when {
                existing == null -> {
                    currentLists.add(
                        ByeDpiDomainList(
                            id = id,
                            name = id.replaceFirstChar { it.uppercase() },
                            domains = domains,
                            isActive = id in defaultActiveIds(),
                            isBuiltIn = true,
                        )
                    )
                }
                existing.isDeleted || existing.isModified -> continue
                else -> {
                    val index = currentLists.indexOfFirst { it.id == id }
                    currentLists[index] = existing.copy(domains = domains)
                }
            }
        }

        currentLists.removeAll { it.isBuiltIn && it.id !in newBuiltInIds }
        saveLists(context, currentLists)
    }

    fun getAllLists(context: Context): MutableList<ByeDpiDomainList> {
        val listsFile = File(context.filesDir, DOMAIN_LISTS_FILE)
        if (!listsFile.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<List<ByeDpiDomainList>>() {}.type
            val lists: List<ByeDpiDomainList> =
                gson.fromJson(listsFile.readText(), type) ?: emptyList()
            lists.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun getActiveDomains(context: Context): List<String> {
        return getAllLists(context)
            .filter { !it.isDeleted && it.isActive }
            .flatMap { it.domains }
            .distinct()
    }

    fun resolve(context: Context, name: String): String? {
        val list = getAllLists(context)
            .find { !it.isDeleted && it.name.equals(name, ignoreCase = true) }
        return list?.domains?.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    fun bundledStrategies(context: Context): List<String> {
        return try {
            context.assets.open("proxytest_strategies.list")
                .bufferedReader()
                .useLines { it.toList() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.replace("{sni}", "google.com") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLists(context: Context, lists: List<ByeDpiDomainList>) {
        File(context.filesDir, DOMAIN_LISTS_FILE).writeText(gson.toJson(lists))
    }
}
