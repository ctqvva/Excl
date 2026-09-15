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
 * Command-line history for the ByeByeDPI Tools editor. Ported from           *
 * romanvht/ByeByeDPI (GPLv3) HistoryUtils; persistence is provided by the    *
 * caller (DataStore tool keys).                                              *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.byedpi

import io.nekohasekai.sagernet.fmt.gson.gson

data class ByeDpiCommand(
    val text: String,
    val pinned: Boolean = false,
)

object ByeDpiCommandHistory {
    const val MAX_HISTORY_SIZE = 40

    fun load(json: String): MutableList<ByeDpiCommand> {
        if (json.isBlank()) return mutableListOf()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<ByeDpiCommand>>() {}.type
            (gson.fromJson<List<ByeDpiCommand>>(json, type) ?: emptyList()).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(history: List<ByeDpiCommand>): String = gson.toJson(history)

    fun addCommand(history: MutableList<ByeDpiCommand>, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        history.removeAll { it.text == trimmed }
        history.add(0, ByeDpiCommand(trimmed))
        // Stable sort: pinned first, recency preserved within each group.
        history.sortByDescending { it.pinned }
        val pinnedCount = history.count { it.pinned }
        if (history.size > MAX_HISTORY_SIZE) {
            val keepPinned = history.filter { it.pinned }
            val keepUnpinned = history.filter { !it.pinned }
                .take((MAX_HISTORY_SIZE - pinnedCount).coerceAtLeast(0))
            history.clear()
            history.addAll(keepPinned + keepUnpinned)
        }
    }

    fun togglePin(history: MutableList<ByeDpiCommand>, text: String) {
        val index = history.indexOfFirst { it.text == text }
        if (index != -1) {
            val item = history[index]
            history[index] = item.copy(pinned = !item.pinned)
            history.sortByDescending { it.pinned }
        }
    }

    fun delete(history: MutableList<ByeDpiCommand>, text: String) {
        history.removeAll { it.text == text }
    }

    fun clearUnpinned(history: MutableList<ByeDpiCommand>) {
        history.removeAll { !it.pinned }
    }
}
