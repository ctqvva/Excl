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
 * Workbench for ByeByeDPI desync command lines: edit, pin and reuse          *
 * strategies. Adapted from romanvht/ByeByeDPI (GPLv3) command editor.        *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.byedpi

import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.byebyedpi.ByeByeDPIBean
import io.nekohasekai.sagernet.ui.ThemedActivity

class ByeDpiCommandEditorActivity : ThemedActivity() {

    private lateinit var input: TextInputEditText
    private val history = mutableListOf<ByeDpiCommand>()
    private val adapter = HistoryAdapter()

    private inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.command_text)
        private val apply: Button = view.findViewById(R.id.action_item_apply)
        private val pin: Button = view.findViewById(R.id.action_item_pin)
        private val delete: Button = view.findViewById(R.id.action_item_delete)

        fun bind(item: ByeDpiCommand) {
            text.text = item.text
            pin.text =
                getString(if (item.pinned) R.string.action_unpin else R.string.action_pin)
            apply.setOnClickListener {
                input.setText(item.text)
                save()
            }
            pin.setOnClickListener {
                ByeDpiCommandHistory.togglePin(history, item.text)
                save()
            }
            delete.setOnClickListener {
                ByeDpiCommandHistory.delete(history, item.text)
                save()
            }
        }
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            return HistoryViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_byedpi_command_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            holder.bind(history[position])
        }

        override fun getItemCount(): Int = history.size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_byedpi_command_editor)

        val list = findViewById<RecyclerView>(R.id.list)
        ViewCompat.setOnApplyWindowInsetsListener(list) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.byebyedpi_command_editor)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        input = findViewById(R.id.command_input)
        input.setText(DataStore.byedpiToolCmd.ifEmpty { ByeByeDPIBean.DEFAULT_COMMAND_LINE })

        findViewById<View>(R.id.action_clear).setOnClickListener {
            input.setText("")
        }
        findViewById<View>(R.id.action_paste).setOnClickListener {
            val clip = getSystemService<ClipboardManager>()?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                input.setText(clip.getItemAt(0).coerceToText(this).toString())
            }
        }
        findViewById<View>(R.id.action_apply).setOnClickListener {
            val text = input.text.toString()
            ByeDpiCommandHistory.addCommand(history, text)
            DataStore.byedpiToolCmd = text.trim()
            save()
        }

        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        list.adapter = adapter

        history.addAll(ByeDpiCommandHistory.load(DataStore.byedpiToolHistory))
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    private fun save() {
        DataStore.byedpiToolHistory = ByeDpiCommandHistory.save(history)
        adapter.notifyDataSetChanged()
    }
}
