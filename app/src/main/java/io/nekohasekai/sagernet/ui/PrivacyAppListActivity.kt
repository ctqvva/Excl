/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.appcompat.widget.SearchView
import androidx.core.util.contains
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppListBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.privacy.PrivacyDefaults
import io.nekohasekai.sagernet.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class PrivacyAppListActivity : ThemedActivity() {

    override val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            searchView.onActionViewCollapsed()
            searchView.clearFocus()
        }
    }

    companion object {
        private const val SWITCH = "switch"

        private val cachedApps
            get() = PackageCache.installedPackages.toMutableMap().apply {
                remove(BuildConfig.APPLICATION_ID)
            }
    }

    private class ProtectedApp(
        private val pm: PackageManager,
        private val appInfo: ApplicationInfo,
        val packageName: String,
    ) {
        val name: CharSequence = appInfo.loadLabel(pm)
        val icon: Drawable get() = appInfo.loadIcon(pm)
        val uid get() = appInfo.uid
    }

    private inner class AppViewHolder(val binding: LayoutAppsItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        View.OnClickListener {
        private lateinit var item: ProtectedApp

        init {
            binding.root.setOnClickListener(this)
        }

        fun bind(app: ProtectedApp) {
            item = app
            binding.itemicon.setImageDrawable(app.icon)
            binding.title.text = app.name
            binding.desc.text = "${app.packageName} (${app.uid})"
            binding.itemcheck.isChecked = isProtectedApp(app)
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) binding.itemcheck.isChecked = isProtectedApp(item)
        }

        override fun onClick(v: View?) {
            if (isProtectedApp(item)) protectedUids.delete(item.uid) else protectedUids[item.uid] = true
            DataStore.privacyBadApps = apps.filter { isProtectedApp(it) }
                .joinToString("\n") { it.packageName }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            apps = cachedApps.map { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                ProtectedApp(packageManager, packageInfo.applicationInfo!!, packageName)
            }.sortedWith(compareBy({ !isProtectedApp(it) }, { it.name.toString() }))
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutAppsItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply {
                val filteredApps = if (constraint.isEmpty()) {
                    apps
                } else {
                    apps.filter {
                        it.name.contains(constraint, true) ||
                            it.packageName.contains(constraint, true) ||
                            it.uid.toString().contains(constraint)
                    }
                }
                count = filteredApps.size
                values = filteredApps
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                filteredApps = results.values as List<ProtectedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }
    }

    private val loading by lazy { findViewById<View>(R.id.loading) }

    private lateinit var binding: LayoutAppListBinding
    private val protectedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<ProtectedApp>()
    private val appsAdapter = AppsAdapter()
    private lateinit var searchView: SearchView

    private fun initProtectedUids(str: String = DataStore.privacyBadApps) {
        protectedUids.clear()
        PackageCache.awaitLoadSync()
        val apps = cachedApps
        for (line in str.lineSequence()) {
            protectedUids[(apps[line] ?: continue).applicationInfo!!.uid] = true
        }
    }

    private fun isProtectedApp(app: ProtectedApp) = protectedUids[app.uid]

    private fun applySuggestedSelections(): Int {
        val suggested = PrivacyDefaults.suggestedBadActorPackages(
            cachedApps,
            packageManager,
        )
        if (suggested.isEmpty()) return 0
        val current = DataStore.privacyBadApps.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val merged = PrivacyDefaults.mergeBadActorPackages(DataStore.privacyBadApps, suggested)
        DataStore.privacyBadApps = merged
        initProtectedUids(merged)
        return suggested.count { it !in current }
    }

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launch {
            loading.crossFadeFrom(binding.list)
            val adapter = binding.list.adapter as AppsAdapter
            withContext(Dispatchers.IO) { adapter.reload() }
            adapter.filter.filter("")
            binding.list.crossFadeFrom(loading)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.list)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.privacy_bad_apps_title)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }
        initProtectedUids()
        binding.list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = appsAdapter

        loadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_list_menu, menu)
        searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?) =
                true.also { appsAdapter.filter.filter(newText) }
        })
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            onBackPressedCallback.isEnabled = hasFocus
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_recommended_apps -> {
                runOnDefaultDispatcher {
                    val added = applySuggestedSelections()
                    apps = apps.sortedWith(compareBy({ !isProtectedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter("")
                        Snackbar.make(
                            binding.list,
                            if (added > 0) R.string.privacy_bad_apps_recommended_added
                            else R.string.privacy_bad_apps_no_recommended,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                return true
            }
            R.id.action_invert_selections -> {
                runOnDefaultDispatcher {
                    for (app in apps) {
                        if (protectedUids.contains(app.uid)) {
                            protectedUids.delete(app.uid)
                        } else {
                            protectedUids[app.uid] = true
                        }
                    }
                    DataStore.privacyBadApps = apps.filter { isProtectedApp(it) }
                        .joinToString("\n") { it.packageName }
                    apps = apps.sortedWith(compareBy({ !isProtectedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter("")
                    }
                }

                return true
            }
            R.id.action_clear_selections -> {
                runOnDefaultDispatcher {
                    protectedUids.clear()
                    DataStore.privacyBadApps = ""
                    apps = apps.sortedWith(compareBy({ !isProtectedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter("")
                    }
                }
            }
            R.id.action_export_clipboard -> {
                val success = SagerNet.trySetPrimaryClip("false\n${DataStore.privacyBadApps}")
                Snackbar.make(
                    binding.list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG
                ).show()
                return true
            }
            R.id.action_import_clipboard -> {
                val protectedAppString = SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!protectedAppString.isNullOrEmpty()) {
                    val i = protectedAppString.indexOf('\n')
                    try {
                        val apps = if (i < 0) "" else protectedAppString.substring(i + 1)
                        DataStore.privacyBadApps = apps
                        Snackbar.make(
                            binding.list,
                            R.string.action_import_msg,
                            Snackbar.LENGTH_LONG
                        ).show()
                        initProtectedUids(apps)
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                        return true
                    } catch (_: IllegalArgumentException) {
                    }
                }
                Snackbar.make(binding.list, R.string.action_import_err, Snackbar.LENGTH_LONG).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onDestroy() {
        loader?.cancel()
        super.onDestroy()
    }
}
