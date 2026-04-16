package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.privacy.PrivacyHoneypot
import io.nekohasekai.sagernet.privacy.PrivacyHoneypotEvent
import io.nekohasekai.sagernet.utils.PackageCache

class PrivacyHoneypotLogActivity : ThemedActivity() {

    private inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(android.R.id.text1)
        private val subtitle = view.findViewById<TextView>(android.R.id.text2)

        fun bind(event: PrivacyHoneypotEvent) {
            val label = event.packageName?.let {
                PackageCache.installedPackages[it]?.applicationInfo?.loadLabel(packageManager)?.toString()
                    ?: it
            } ?: getString(androidx.preference.R.string.not_set)
            title.text = if (event.uid >= 0) "$label (${event.uid})" else label
            subtitle.text = buildString {
                append(event.transport.uppercase())
                append(' ')
                append(event.classification)
                append(" -> 127.0.0.1:")
                append(event.localPort)
                if (event.remotePort > 0) {
                    append(" from :")
                    append(event.remotePort)
                }
                append(" • ")
                append(
                    DateUtils.getRelativeTimeSpanString(
                        event.timestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    )
                )
            }
        }
    }

    private inner class EventsAdapter : RecyclerView.Adapter<EventViewHolder>() {
        var items: List<PrivacyHoneypotEvent> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
            return EventViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_privacy_honeypot_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private val adapter = EventsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_privacy_honeypot_log)

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
            setTitle(R.string.privacy_honeypot_log_title)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        list.adapter = adapter
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.privacy_honeypot_log_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                PrivacyHoneypot.clearLog()
                reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    private fun reload() {
        PackageCache.awaitLoadSync()
        adapter.items = PrivacyHoneypot.parseLog()
        adapter.notifyDataSetChanged()
    }
}
