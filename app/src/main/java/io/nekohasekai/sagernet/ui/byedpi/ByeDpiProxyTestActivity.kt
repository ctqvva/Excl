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
 * Scores ByeByeDPI desync strategies against test domains by running a      *
 * local proxy per strategy. Adapted from romanvht/ByeByeDPI (GPLv3)          *
 * TestActivity, minus the service layer (JNI runs in-process here).          *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.byedpi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.core.ByeDpiProxy
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.byebyedpi.ByeByeDPIBean
import io.nekohasekai.sagernet.fmt.byebyedpi.ByeDpiDomainLists
import io.nekohasekai.sagernet.fmt.byebyedpi.buildByeDpiArgs
import io.nekohasekai.sagernet.ktx.mkPort
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class ByeDpiProxyTestActivity : ThemedActivity() {

    data class SiteResult(val site: String, val success: Int, val total: Int)

    data class StrategyResult(
        val command: String,
        var totalRequests: Int = 0,
        var currentProgress: Int = 0,
        var successCount: Int = 0,
        var siteResults: MutableList<SiteResult> = mutableListOf(),
        var isCompleted: Boolean = false,
        var expanded: Boolean = false,
    ) {
        val percent: Int
            get() = if (totalRequests > 0) successCount * 100 / totalRequests else 0
    }

    private inner class StrategyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val command: TextView = view.findViewById(R.id.command_text)
        private val progress: ProgressBar = view.findViewById(R.id.progress_bar)
        private val score: TextView = view.findViewById(R.id.score_text)
        private val detailsButton: Button = view.findViewById(R.id.action_details)
        private val details: TextView = view.findViewById(R.id.details_text)

        fun bind(item: StrategyResult) {
            command.text = item.command
            progress.max = item.totalRequests.coerceAtLeast(1)
            progress.progress = item.currentProgress
            score.text = "${item.successCount}/${item.totalRequests}"
            detailsButton.isVisible = item.isCompleted
            details.isVisible = item.isCompleted && item.expanded
            if (item.isCompleted) {
                details.text = item.siteResults.joinToString("\n") {
                    "${it.site}: ${it.success}/${it.total}"
                }
            }
            detailsButton.setOnClickListener {
                item.expanded = !item.expanded
                adapter.notifyItemChanged(bindingAdapterPosition)
            }
        }
    }

    private inner class StrategyAdapter : RecyclerView.Adapter<StrategyViewHolder>() {
        var items: List<StrategyResult> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrategyViewHolder {
            return StrategyViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_byedpi_strategy_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: StrategyViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private val adapter = StrategyAdapter()
    private val strategies = mutableListOf<StrategyResult>()
    private var testJob: Job? = null
    private var proxyJob: Job? = null
    private var proxy: ByeDpiProxy? = null

    private lateinit var startButton: Button
    private lateinit var statusText: TextView

    // Upstream proxy-test defaults.
    private val delayMs = 500L
    private val requestsCount = 1
    private val requestTimeout = 5L
    private val requestLimit = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_byedpi_proxy_test)

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
            setTitle(R.string.byebyedpi_proxy_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        startButton = findViewById(R.id.action_start)
        statusText = findViewById(R.id.status_text)
        startButton.setOnClickListener {
            if (testJob?.isActive == true) stopTesting(false) else startTesting()
        }

        list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        list.adapter = adapter

        ByeDpiDomainLists.syncLists(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        stopProxy()
        testJob?.cancel()
        super.onDestroy()
    }

    private fun loadStrategies(): List<String> {
        val bundled = ByeDpiDomainLists.bundledStrategies(this)
        val history = ByeDpiCommandHistory.load(DataStore.byedpiToolHistory).map { it.text }
        return (history + bundled).distinct().filter { it.isNotBlank() }
    }

    private fun startTesting() {
        val sites = ByeDpiDomainLists.getActiveDomains(this)
        if (sites.isEmpty()) {
            statusText.text = getString(R.string.byebyedpi_test_no_domains)
            return
        }
        val commands = loadStrategies()
        if (commands.isEmpty()) return

        strategies.clear()
        strategies.addAll(commands.map { StrategyResult(it) })
        adapter.items = strategies
        adapter.notifyDataSetChanged()

        testJob = lifecycleScope.launch(Dispatchers.IO) {
            onMainDispatcher {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startButton.text = getString(R.string.action_stop)
                statusText.text = ""
            }

            for ((strategyIndex, strategy) in strategies.withIndex()) {
                if (!isActive) break
                onMainDispatcher {
                    statusText.text = getString(
                        R.string.byebyedpi_test_running, strategyIndex + 1, strategies.size
                    )
                }

                val port = mkPort()
                val bean = ByeByeDPIBean().apply {
                    initializeDefaultValues()
                    useCommandLine = true
                    commandLine = strategy.command
                }
                val args = buildByeDpiArgs(bean, port) { name ->
                    ByeDpiDomainLists.resolve(this@ByeDpiProxyTestActivity, name)
                }
                if (!startProxy(port, args)) {
                    strategy.isCompleted = true
                    onMainDispatcher { adapter.notifyItemChanged(strategyIndex) }
                    continue
                }

                delay(delayMs)
                val totalRequests = sites.size * requestsCount
                strategy.totalRequests = totalRequests
                onMainDispatcher { adapter.notifyItemChanged(strategyIndex) }

                ByeDpiSiteCheck(LOCALHOST, port).checkSitesAsync(
                    sites = sites,
                    requestsCount = requestsCount,
                    requestTimeout = requestTimeout,
                    concurrentRequests = requestLimit,
                ) { site, success, total ->
                    launch(Dispatchers.Main) {
                        strategy.currentProgress += total
                        strategy.successCount += success
                        strategy.siteResults.add(SiteResult(site, success, total))
                        adapter.notifyItemChanged(strategyIndex)
                    }
                }

                strategy.isCompleted = true
                stopProxy()
                onMainDispatcher { adapter.notifyItemChanged(strategyIndex) }
                delay(delayMs)
            }

            strategies.sortWith(
                compareByDescending<StrategyResult> { it.isCompleted }
                    .thenByDescending { it.percent }
            )

            stopTesting(true)
        }
    }

    private fun stopTesting(completed: Boolean) {
        val job = testJob
        testJob = null
        job?.cancel()
        stopProxy()
        lifecycleScope.launch(Dispatchers.Main) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startButton.text = getString(R.string.start)
            statusText.text = getString(
                if (completed) R.string.byebyedpi_test_complete
                else R.string.byebyedpi_test_stopped
            )
            adapter.notifyDataSetChanged()
        }
    }

    private suspend fun startProxy(port: Int, args: Array<String>): Boolean {
        stopProxy()
        val instance = ByeDpiProxy()
        proxy = instance
        proxyJob = lifecycleScope.launch(Dispatchers.IO) {
            instance.startProxy(args)
        }
        // Wait for the listen socket (byedpi has no ready callback).
        repeat(30) {
            try {
                Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), 100) }
                return true
            } catch (_: Exception) {
            }
            delay(100)
        }
        stopProxy()
        return false
    }

    private fun stopProxy() {
        proxyJob?.let { job ->
            try {
                proxy?.stopProxy()
            } catch (_: Exception) {
            }
            job.cancel()
            try {
                proxy?.forceClose()
            } catch (_: Exception) {
            }
            proxyJob = null
            proxy = null
        }
    }
}
