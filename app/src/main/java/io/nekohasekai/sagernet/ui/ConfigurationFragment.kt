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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.*
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.bg.BalancerRuntimeState
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.test.ProfileTestRunner
import io.nekohasekai.sagernet.bg.test.ProfileTestState
import io.nekohasekai.sagernet.bg.test.ProfileTestResults
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.databinding.LayoutProfileListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.exportBackup
import io.nekohasekai.sagernet.fmt.wireguard.toConf
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.Protocols
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.profile.*
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipInputStream
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.utils.FormatFileSizeCompat

@android.annotation.SuppressLint("ClickableViewAccessibility")
fun View.suppressDragWhilePressed(setPressed: (Boolean) -> Unit) {
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setPressed(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressed(false)
        }
        false
    }
}

class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false, val selectedItem: ProxyEntity? = null, val titleRes: Int = 0
) : ToolbarFragment(R.layout.layout_group_list),
    PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener {

    val onBackPressedCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            searchView?.onActionViewCollapsed()
            searchView?.clearFocus()
        }
    }

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2
    var searchView: SearchView? = null
    val selectedGroup get() = if (tabLayout.isGone && adapter.groupList.size > 0) adapter.groupList[0] else (if (adapter.groupList.size > 0 && tabLayout.selectedTabPosition > -1) adapter.groupList[tabLayout.selectedTabPosition] else ProxyGroup())
    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }

    val updateSelectedCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int, positionOffset: Float, positionOffsetPixels: Int
        ) {
            if (adapter.groupList.size > position) {
                DataStore.selectedGroup = adapter.groupList[position].id
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.setOnMenuItemClickListener(this)
        } else {
            toolbar.setTitle(titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.group_tab)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        searchView = toolbar.findViewById(R.id.action_search)
        searchView?.maxWidth = Int.MAX_VALUE
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(query: String?): Boolean {
                query?.let {
                    try {
                        val fragment = (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)
                        fragment?.adapter?.filter(query)
                    } catch (_: Exception) {}
                }
                return false
            }
        })
        searchView?.setOnQueryTextFocusChangeListener { _, hasFocus ->
            onBackPressedCallback.isEnabled = hasFocus
        }
        searchView?.let {
            // override onBackPressedCallback of MainActivity
            (requireActivity() as? MainActivity)?.onBackPressedDispatcher?.addCallback(this, onBackPressedCallback)
        }

        groupPager = view.findViewById(R.id.group_pager)
        tabLayout = view.findViewById(R.id.group_tab)
        adapter = GroupPagerAdapter()
        ProfileManager.addListener(adapter)
        GroupManager.addListener(adapter)

        groupPager.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            ProfileTestResults.changes.collect { epoch ->
                if (profileTestEpoch != null && profileTestEpoch != epoch) profileTestJob?.cancel()
                adapter.groupFragments.values.forEach { it.refreshTestCard(-1L) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ProfileTestState.updates.collect { id ->
                adapter.groupFragments.values.forEach { it.refreshTestCard(id) }
            }
        }
        updateTestMenu()
        groupPager.offscreenPageLimit = 2

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                searchView?.onActionViewCollapsed()
                searchView?.clearFocus()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                val fragment = (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)
                fragment?.adapter?.filter("")
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}

        })

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            if (adapter.groupList.size > position) {
                tab.text = adapter.groupList[position].displayName()
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        toolbar.setOnClickListener {

            val fragment = (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)

            if (fragment != null) {
                val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                val selectedProfileIndex = fragment.adapter.configurationIdList.indexOf(
                    selectedProxy
                )
                if (selectedProfileIndex != -1) {
                    val layoutManager = fragment.layoutManager
                    val first = layoutManager.findFirstVisibleItemPosition()
                    val last = layoutManager.findLastVisibleItemPosition()

                    if (selectedProfileIndex !in first..last) {
                        fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                        return@setOnClickListener
                    }

                }

                fragment.configurationListView.scrollTo(0)
            }

        }

        toolbar.setOnLongClickListener {
            val selectedProxy = selectedItem
                ?: SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                ?: return@setOnLongClickListener true
            val groupIndex = adapter.groupList.indexOfFirst {
                it.id == selectedProxy.groupId
            }
            if (groupIndex < 0) return@setOnLongClickListener true
            DataStore.selectedGroup = selectedProxy.groupId
            groupPager.currentItem = groupIndex

            val fragment = (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)
            if (fragment != null) {
                val selectedProfileIndex = fragment.adapter.configurationIdList.indexOfFirst {
                    it == selectedProxy.id
                }
                if (selectedProfileIndex > 0) {
                    fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                }
            }

            true
        }

        (requireActivity() as? MainActivity)?.onBackPressedCallback?.isEnabled = false
    }

    override fun onDestroy() {
        if (::adapter.isInitialized) {
            GroupManager.removeListener(adapter)
            ProfileManager.removeListener(adapter)
        }

        super.onDestroy()
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        val fragment = (childFragmentManager.findFragmentByTag("f" + selectedGroup.id) as GroupFragment?)
        fragment?.configurationListView?.apply {
            if (!hasFocus()) requestFocus()
        }
        return super.onKeyDown(ketCode, event)
    }

    val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        var fileText = ""
        if (file != null) runOnDefaultDispatcher {
            try {
                val fileName = requireContext().contentResolver.query(file, null, null, null, null)
                    ?.use { cursor ->
                        cursor.moveToFirst()
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                            .let(cursor::getString)
                    }

                val proxies = mutableListOf<AbstractBean>()
                if (fileName != null && fileName.endsWith(".zip")) {
                    // try parse wireguard zip
                    val zip = ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        fileText = zip.bufferedReader().readText()
                        RawUpdater.parseRaw(fileText)?.let { pl -> proxies.addAll(pl) }
                        zip.closeEntry()
                    }
                    runCatching {
                        zip.close()
                    }
                } else {
                    fileText = requireContext().contentResolver.openInputStream(file)!!.use {
                        it.bufferedReader().readText()
                    }
                    RawUpdater.parseRaw(fileText)?.let { pl -> proxies.addAll(pl) }
                }

                if (proxies.isEmpty()) {
                    if (!fileText.contains("\n") && !fileText.contains("\r") && isHTTPorHTTPSURL(fileText)) {
                        (requireActivity() as? MainActivity)?.importSubscription(fileText)
                    } else {
                        onMainDispatcher {
                            snackbar(getString(R.string.no_proxies_found_in_file)).show()
                        }
                    }
                } else import(proxies)
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    snackbar(e.readableMessage).show()
                }
            }
        }
    }

    val importBackupFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) runOnDefaultDispatcher {
            try {
                val text = requireContext().contentResolver.openInputStream(file)!!.use {
                    it.bufferedReader().readText()
                }
                val proxies = parseBackupLines(text)
                if (proxies.isNotEmpty()) {
                    import(proxies)
                } else onMainDispatcher {
                    snackbar(getString(R.string.no_proxies_found_in_file)).show()
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    snackbar(e.readableMessage).show()
                }
            }
        }
    }

    suspend fun import(proxies: List<AbstractBean>) {
        if (proxies.size <= 32) {
            finishImport(proxies)
        } else {
            val name = SagerDatabase.groupDao.getById(DataStore.selectedGroupForImport())!!.displayName()
            onMainDispatcher {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.profile_import)
                    .setMessage(resources.getQuantityString(R.plurals.profile_multi_import_message, proxies.size, proxies.size, name))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        runOnDefaultDispatcher {
                            finishImport(proxies)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private suspend fun finishImport(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        val targetIndex = adapter.groupList.indexOfFirst { it.id == targetId }

        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            if (adapter.groupList.isEmpty() || selectedGroup.id != targetId) {
                if (targetIndex != -1) {
                    tabLayout.getTabAt(targetIndex)?.select()
                } else {
                    DataStore.selectedGroup = targetId
                    adapter.reload()
                }
            }

            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()

            val group = SagerDatabase.groupDao.getById(targetId)!!
            GroupManager.updateGroup(group, reconfigureUpdater = false)
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }
            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else {
                    runOnDefaultDispatcher {
                        try {
                            val proxies = RawUpdater.parseRaw(text)
                            if (proxies.isNullOrEmpty()) {
                                if (!text.contains("\n") && !text.contains("\r") && isHTTPorHTTPSURL(text)) {
                                    (requireActivity() as? MainActivity)?.importSubscription(text)
                                } else onMainDispatcher {
                                    snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                                }
                            } else {
                                import(proxies)
                            }
                        } catch (e: Exception) {
                            Logs.w(e)
                            onMainDispatcher {
                                snackbar(e.readableMessage).show()
                            }
                        }
                    }
                }
            }
            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }
            R.id.action_import_backup_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else {
                    runOnDefaultDispatcher {
                        try {
                            val proxies = parseBackupLines(text)
                            if (proxies.isNotEmpty()) {
                                import(proxies)
                            } else onMainDispatcher {
                                snackbar(getString(R.string.no_proxies_found_in_file)).show()
                            }
                        } catch (e: Exception) {
                            Logs.w(e)
                            onMainDispatcher {
                                snackbar(e.readableMessage).show()
                            }
                        }
                    }
                }
            }
            R.id.action_import_backup_file -> {
                startFilesForResult(importBackupFile, "*/*")
            }
            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }
            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }
            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }
            R.id.action_new_ssr -> {
                startActivity(Intent(requireActivity(), ShadowsocksRSettingsActivity::class.java))
            }
            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }
            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VLESSSettingsActivity::class.java))
            }
            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }
            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }
            R.id.action_new_hysteria2 -> {
                startActivity(Intent(requireActivity(), Hysteria2SettingsActivity::class.java))
            }
            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }
            R.id.action_new_tuic5 -> {
                startActivity(Intent(requireActivity(), Tuic5SettingsActivity::class.java))
            }
            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }
            R.id.action_new_wg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }
            R.id.action_new_awg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java).apply {
                    putExtra(WireGuardSettingsActivity.EXTRA_AMNEZIAWG_PROFILE, true)
                })
            }
            R.id.action_new_vk_turn -> {
                startActivity(Intent(requireActivity(), VkTurnSettingsActivity::class.java))
            }
            R.id.action_new_byebyedpi -> {
                startActivity(Intent(requireActivity(), ByeByeDPISettingsActivity::class.java))
            }
            R.id.action_new_juicity -> {
                startActivity(Intent(requireActivity(), JuicitySettingsActivity::class.java))
            }
            R.id.action_new_http3 -> {
                startActivity(Intent(requireActivity(), Http3SettingsActivity::class.java))
            }
            R.id.action_new_anytls -> {
                startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }
            R.id.action_new_shadowquic -> {
                startActivity(Intent(requireActivity(), ShadowQUICSettingsActivity::class.java))
            }
            R.id.action_new_trusttunnel -> {
                startActivity(Intent(requireActivity(), TrustTunnelSettingsActivity::class.java))
            }
            R.id.action_new_snell -> {
                startActivity(Intent(requireActivity(), SnellSettingsActivity::class.java))
            }
            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingsActivity::class.java))
            }
            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }
            R.id.action_new_balancer -> {
                startActivity(Intent(requireActivity(), BalancerSettingsActivity::class.java))
            }
            R.id.action_clear_traffic_statistics -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }
            R.id.action_connection_test_clear_results -> {
                val groupId = selectedGroup.id
                profileTestJob?.cancel()
                runOnDefaultDispatcher {
                    ProfileTestResults.clear(SagerDatabase.proxyDao.getIdsByGroup(groupId))
                    SagerDatabase.proxyDao.clearTestResults(groupId)
                    GroupManager.postReload(groupId)
                }
            }
            R.id.action_remove_duplicate -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
                    for (p in profiles) {
                        val proxy = Protocols.Deduplication(p.requireBean(), p.displayType())
                        if (!uniqueProxies.add(proxy)) {
                            toClear += p
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(
                                    getString(R.string.delete_multi_confirm_prompt) + "\n" +
                                            toClear.mapIndexedNotNull { index, proxyEntity ->
                                                if (index < 20) {
                                                    proxyEntity.displayName()
                                                } else if (index == 20) {
                                                    "......"
                                                } else {
                                                    null
                                                }
                                            }.joinToString("\n")
                                )
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                            val index = configurationIdList.indexOf(profile.id)
                                            if (index >= 0) {
                                                configurationIdList.removeAt(index)
                                                configurationList.remove(profile.id)
                                                notifyItemRemoved(index)
                                            }
                                        }
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }
            }
            R.id.action_connection_test_delete_unavailable -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != -1 && profile.status != 0 && profile.status != 1) {
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(
                                    getString(R.string.delete_multi_confirm_prompt) + "\n" +
                                            toClear.mapIndexedNotNull { index, proxyEntity ->
                                                if (index < 20) {
                                                    proxyEntity.displayName()
                                                } else if (index == 20) {
                                                    "......"
                                                } else {
                                                    null
                                                }
                                            }.joinToString("\n")
                                )
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                            val index = configurationIdList.indexOf(profile.id)
                                            if (index >= 0) {
                                                configurationIdList.removeAt(index)
                                                configurationList.remove(profile.id)
                                                notifyItemRemoved(index)
                                            }
                                        }
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }
            }
            R.id.action_connection_url_test -> {
                urlTest()
            }
            R.id.action_profile_speed_test -> {
                urlTest(speed = true)
            }
            R.id.action_profile_test_cancel -> {
                profileTestJob?.cancel()
                updateTestMenu()
            }
            R.id.action_update_subscription -> {
                runOnDefaultDispatcher {
                    val currentGroup = DataStore.currentGroup()
                    if (currentGroup.type == GroupType.SUBSCRIPTION) {
                        if (currentGroup.id !in GroupUpdater.updating) {
                            GroupUpdater.startUpdate(currentGroup, true)
                        }
                    } else {
                        snackbar(R.string.group_not_a_subscription).show()
                    }
                }
            }
        }
        return true
    }

    private var profileTestJob: Job? = null
    private var profileTestEpoch: Long? = null
    private val testsRunning get() = profileTestJob?.isCompleted == false

    private fun updateTestMenu() {
        toolbar.menu.findItem(R.id.action_connection_url_test)?.isEnabled = !testsRunning
        toolbar.menu.findItem(R.id.action_profile_speed_test)?.apply {
            isEnabled = !testsRunning
            title = getString(R.string.profile_test_speed) + " (10 MiB)"
        }
        toolbar.menu.findItem(R.id.action_profile_test_cancel)?.apply {
            isVisible = testsRunning
            isEnabled = profileTestJob?.isActive == true
        }
    }

    /** Test phases and results appear on the original cards; no modal test UI. */
    fun urlTest(profileId: Long? = null, speed: Boolean = false) {
        if (testsRunning) return
        val groupId = selectedGroup.id
        val label = getString(if (speed) R.string.profile_test_speed else R.string.connection_test_url_test)
        val previousSubtitle = toolbar.subtitle
        val runId = ProfileTestState.nextRunId()
        val epoch = ProfileTestResults.changes.value
        profileTestEpoch = epoch

        val batch = viewLifecycleOwner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                updateTestMenu()
                val profiles = withContext(Dispatchers.IO) {
                    val candidates = if (profileId != null) listOfNotNull(ProfileManager.getProfile(profileId))
                    else SagerDatabase.proxyDao.getByGroup(groupId)
                    if (speed) candidates.filter { it.status == 1 } else candidates
                }
                if (profiles.isEmpty()) {
                    snackbar(if (speed) R.string.profile_test_need_available else R.string.profile_test_no_profiles).show()
                    return@launch
                }
                val pending = ConcurrentLinkedQueue(profiles)
                var finished = 0
                toolbar.subtitle = "$label · ${getString(R.string.profile_test_progress, 0, profiles.size)}"

                suspend fun saveLatency(profile: ProxyEntity): Boolean = withContext(Dispatchers.IO) {
                    ensureActive()
                    val updated = ProfileTestResults.commitIfCurrent(epoch) {
                        val current = ProfileManager.getProfile(profile.id)
                        if (current == null || current.groupId != profile.groupId ||
                            ProfileTestResults.fingerprint(current) != ProfileTestResults.fingerprint(profile)
                        ) false
                        else SagerDatabase.proxyDao.updateTestResult(profile.id, profile.status, profile.ping, profile.error) > 0
                    }
                    if (updated) ProfileManager.postUpdate(profile.id)
                    updated
                }

                coroutineScope {
                    List(if (speed) 1 else 6) {
                        launch {
                            while (isActive) {
                                val profile = pending.poll() ?: break
                                var failure: ProfileTestRunner.Phase? = null
                                try {
                                    val current = withContext(Dispatchers.IO) { ProfileManager.getProfile(profile.id) }
                                    if (current == null || current.groupId != profile.groupId ||
                                        ProfileTestResults.fingerprint(current) != ProfileTestResults.fingerprint(profile) ||
                                        (speed && current.status != 1) ||
                                        adapter.groupFragments[profile.groupId]?.isPendingRemoval(profile.id) == true
                                    ) continue
                                    if (ProfileTestResults.changes.value != epoch) throw CancellationException("Test results invalidated")
                                    ProfileTestState.begin(profile.id, runId)
                                    val supported = withContext(Dispatchers.IO) { !ProfileTestRunner.usesBrowserForwarder(profile) }
                                    if (!supported) throw UnsupportedOperationException(getString(R.string.profile_test_browser_unsupported))
                                    ProfileTestRunner.run(profile, speed,
                                        onPhase = { phase -> withContext(Dispatchers.Main) {
                                            ProfileTestState.phase(profile.id, runId, phase)
                                        } },
                                        onResult = { result ->
                                            ensureActive()
                                            if (!ProfileTestResults.put(profile.id, result, epoch)) {
                                                throw CancellationException("Test results invalidated")
                                            }
                                            ProfileManager.postUpdate(profile.id)
                                        },
                                        onLatency = { ping -> withContext(Dispatchers.Main) {
                                            profile.status = 1
                                            profile.ping = ping
                                            profile.error = null
                                            if (!saveLatency(profile)) throw CancellationException("Profile changed")
                                        } },
                                        onError = { phase, error -> withContext(Dispatchers.Main) {
                                            failure = phase
                                            if (phase == ProfileTestRunner.Phase.URL) {
                                                profile.status = 3
                                                profile.error = error.readableMessage
                                                saveLatency(profile)
                                            }
                                        } },
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    profile.status = if (e is PluginManager.PluginNotFoundException || e is UnsupportedOperationException) -1 else 3
                                    profile.error = e.readableMessage
                                    saveLatency(profile)
                                } finally {
                                    ProfileTestState.finish(profile.id, runId,
                                        failure?.takeUnless { it == ProfileTestRunner.Phase.URL })
                                    finished++
                                    if (isActive) toolbar.subtitle = "$label · ${getString(R.string.profile_test_progress, finished, profiles.size)}"
                                }
                            }
                        }
                    }.joinAll()
                }
            } finally {
                // coroutineScope drains cancelled native workers before restoring the controls.
                if (profileTestEpoch == epoch) profileTestEpoch = null
                profileTestJob = null
                if (view != null) {
                    toolbar.subtitle = previousSubtitle
                    updateTestMenu()
                }
            }
        }
        profileTestJob = batch
        batch.start()
    }

    override fun onDestroyView() {
        profileTestJob?.cancel()
        super.onDestroyView()
    }

    inner class GroupPagerAdapter : FragmentStateAdapter(this),
        ProfileManager.Listener,
        GroupManager.Listener {

        var selectedGroupIndex = 0
        var groupList: ArrayList<ProxyGroup> = ArrayList()
        var groupFragments: HashMap<Long, GroupFragment> = HashMap()

        fun reload(now: Boolean = false) {

            if (!select) {
                groupPager.unregisterOnPageChangeCallback(updateSelectedCallback)
            }

            runOnDefaultDispatcher {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (newGroupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                newGroupList.find { it.ungrouped }?.let {
                    if (SagerDatabase.proxyDao.countByGroup(it.id) == 0L) {
                        newGroupList.remove(it)
                    }
                }

                var selectedGroup = selectedItem?.groupId ?: DataStore.currentGroupId()
                var set = false
                if (selectedGroup > 0L) {
                    selectedGroupIndex = newGroupList.indexOfFirst { it.id == selectedGroup }
                    set = true
                } else if (groupList.size == 1) {
                    selectedGroup = groupList[0].id
                    if (DataStore.selectedGroup != selectedGroup) {
                        DataStore.selectedGroup = selectedGroup
                    }
                }

                try {
                    requireActivity()
                } catch (e: Exception) {
                    Logs.w(e)
                    return@runOnDefaultDispatcher
                }
                val runFunc = if (now) requireActivity()::runOnUiThread else groupPager::post
                runFunc {
                    groupList = newGroupList
                    notifyDataSetChanged()
                    if (set) groupPager.setCurrentItem(selectedGroupIndex, false)
                    val hideTab = groupList.size < 2
                    tabLayout.isGone = hideTab
                    toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                    if (!select) {
                        groupPager.registerOnPageChangeCallback(updateSelectedCallback)
                    }
                }
            }
        }

        init {
            reload(true)
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun createFragment(position: Int): Fragment {
            return GroupFragment().apply {
                proxyGroup = groupList[position]
                groupFragments[proxyGroup.id] = this
                if (position == selectedGroupIndex) {
                    selected = true
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return groupList.any { it.id == itemId }
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            tabLayout.post {
                groupList.add(group)

                if (groupList.any { !it.ungrouped }) tabLayout.post {
                    tabLayout.visibility = View.VISIBLE
                }

                notifyItemInserted(groupList.size - 1)
                tabLayout.getTabAt(groupList.size - 1)?.select()
            }
        }

        override suspend fun groupRemoved(groupId: Long) {
            tabLayout.post {
                val index = groupList.indexOfFirst { it.id == groupId }
                if (index == -1) return@post
                groupList.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) return

            tabLayout.post {
                tabLayout.getTabAt(index)?.text = group.displayName()
            }
        }

        override suspend fun groupUpdated(groupId: Long) = Unit

        override suspend fun onAdd(profile: ProxyEntity) {
            if (groupList.find { it.id == profile.groupId } == null) {
                DataStore.selectedGroup = profile.groupId
                reload()
            }
        }

        override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) = Unit

        override suspend fun onUpdated(profile: ProxyEntity) = Unit

        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            val group = groupList.find { it.id == groupId } ?: return
            if (group.ungrouped && SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
                reload()
            }
        }
    }

    class GroupFragment : Fragment() {

        lateinit var proxyGroup: ProxyGroup
        var selected = false
        var scrolled = false
        val showBackup = DataStore.experimentalFlagsProperties.getBooleanProperty("enableProfileBackup")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return LayoutProfileListBinding.inflate(inflater).root
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        lateinit var adapter: ConfigurationAdapter

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)

            if (::proxyGroup.isInitialized) {
                outState.putParcelable("proxyGroup", proxyGroup)
            }
            if (::layoutManager.isInitialized) {
                outState.putInt("scrollPosition", layoutManager.findFirstVisibleItemPosition())
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            savedInstanceState?.getParcelable<ProxyGroup>("proxyGroup")?.also {
                proxyGroup = it
                onViewCreated(requireView(), null)
            }
            savedInstanceState?.getInt("scrollPosition", -1)?.takeIf { it >= 0 }?.let { pos ->
                scrolled = true
                configurationListView.scrollToPosition(pos)
            }
        }

        private val isEnabled: Boolean
            get() {
                return ((activity as? MainActivity)
                    ?: return false).state.let { it.canStop || it == BaseService.State.Stopped }
            }

        fun refreshTestCard(profileId: Long) {
            if (!::adapter.isInitialized) return
            if (profileId < 0) adapter.notifyDataSetChanged()
            else {
                val index = adapter.configurationIdList.indexOf(profileId)
                if (index >= 0) adapter.notifyItemChanged(index)
            }
        }

        fun isPendingRemoval(profileId: Long) = ::adapter.isInitialized && adapter.pendingDeletedIds.contains(profileId)

        private var actionButtonPressed = false
        private var dragHelper: ItemTouchHelper? = null

        private fun isProfileEditable(id: Long): Boolean {
            return ((activity as? MainActivity)
                ?: return false).state == BaseService.State.Stopped || id != DataStore.selectedProxy
        }

        lateinit var layoutManager: LinearLayoutManager
        lateinit var configurationListView: RecyclerView

        val parent get() = parentFragment as? ConfigurationFragment
        fun requirePrent() = requireParentFragment() as ConfigurationFragment

        override fun onResume() {
            super.onResume()

            if (::adapter.isInitialized) {
                if (adapter.itemCount == 0) {
                    runOnDefaultDispatcher { adapter.reloadProfiles() }
                }
            } else {
                onViewCreated(requireView(), null)
            }
            checkOrderMenu()

            if (showBackup) {
                (parentFragment as? ToolbarFragment)
                    ?.toolbar?.menu?.findItem(R.id.action_import_backup)?.isVisible = true
            }

            if (::configurationListView.isInitialized) {
                configurationListView.requestFocus()
            }
        }

        fun checkOrderMenu() {
            if (parent?.select == true) return

            val pf = parentFragment as? ToolbarFragment ?: return
            val menu = pf.toolbar.menu
            val origin = menu.findItem(R.id.action_order_origin)
            val byName = menu.findItem(R.id.action_order_by_name)
            val byDelay = menu.findItem(R.id.action_order_by_delay)
            when (proxyGroup.order) {
                GroupOrder.ORIGIN -> {
                    origin.isChecked = true
                }
                GroupOrder.BY_NAME -> {
                    byName.isChecked = true
                }
                GroupOrder.BY_DELAY -> {
                    byDelay.isChecked = true
                }
            }

            fun updateTo(order: Int) {
                if (proxyGroup.order == order) return
                runOnDefaultDispatcher {
                    proxyGroup.order = order
                    GroupManager.updateGroup(proxyGroup, reconfigureUpdater = false)
                }
            }

            origin.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.ORIGIN)
                true
            }
            byName.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_NAME)
                true
            }
            byDelay.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_DELAY)
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            val parent = parent ?: return
            if (!::proxyGroup.isInitialized) return

            configurationListView = view.findViewById(R.id.configuration_list)
            ViewCompat.setOnApplyWindowInsetsListener(configurationListView) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.updatePadding(
                    left = bars.left + dp2px(4),
                    right = bars.right + dp2px(4),
                    bottom = bars.bottom + dp2px(64),
                )
                insets
            }
            layoutManager = FixedLinearLayoutManager(configurationListView)
            configurationListView.layoutManager = layoutManager
            adapter = ConfigurationAdapter()
            ProfileManager.addListener(adapter)
            GroupManager.addListener(adapter)
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            if (!parent.select) {
                undoManager = UndoSnackbarManager(activity as MainActivity, adapter)
            }

            if (!parent.select && proxyGroup.type == GroupType.BASIC) {
                dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
                ) {
                    override fun isLongPressDragEnabled() = false

                    override fun getSwipeDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ): Int {
                        return 0
                    }

                    override fun getDragDirs(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) = if (isEnabled && !actionButtonPressed) super.getDragDirs(
                        recyclerView, viewHolder
                    ) else 0

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    }

                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                    ): Boolean {
                        adapter.move(
                            viewHolder.adapterPosition, target.adapterPosition
                        )
                        return true
                    }

                    override fun clearView(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                    ) {
                        super.clearView(recyclerView, viewHolder)
                        adapter.commitMove()
                    }
                })
                dragHelper?.attachToRecyclerView(configurationListView)

            }

        }

        override fun onDestroy() {
            if (::adapter.isInitialized) {
                ProfileManager.removeListener(adapter)
                GroupManager.removeListener(adapter)
            }

            super.onDestroy()

            if (!::undoManager.isInitialized) return
            undoManager.flush()
        }

        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener,
            GroupManager.Listener,
            UndoSnackbarManager.Interface<ProxyEntity> {

            init {
                setHasStableIds(true)
            }

            var configurationIdList: MutableList<Long> = mutableListOf()
            val configurationList = HashMap<Long, ProxyEntity>()
            val pendingDeletedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

            private fun getItem(profileId: Long): ProxyEntity? {
                var profile = configurationList[profileId]
                if (profile == null) {
                    profile = ProfileManager.getProfile(profileId)
                    if (profile != null) {
                        configurationList[profileId] = profile
                    }
                }
                return profile
            }

            private fun getItemAt(index: Int) = getItem(configurationIdList[index])

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_profile, parent, false)
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                try {
                    getItemAt(position)?.let {
                        holder.bind(it)
                    }
                } catch (ignored: NullPointerException) { // when group deleted
                }
            }

            override fun getItemCount(): Int {
                return configurationIdList.size
            }
            var activeSelectionId: Long = -1

            fun refreshSelection() {
                val newId = DataStore.selectedProxy
                if (activeSelectionId == newId) return
                val oldId = activeSelectionId
                activeSelectionId = newId

                listOf(oldId, newId).forEach { id ->
                    val index = configurationIdList.indexOf(id)
                    if (index != -1) notifyItemChanged(index, "PAYLOAD_SELECTION_CHANGE")
                }
            }

            override fun onBindViewHolder(
                holder: ConfigurationHolder,
                position: Int,
                payloads: MutableList<Any>
            ) {
                if (payloads.contains("PAYLOAD_SELECTION_CHANGE")) {
                    val entityId = configurationIdList[position]

                    val isSelected = (entityId == activeSelectionId)
                    val isStarted = isSelected && SagerNet.started && DataStore.startedProfile == entityId

                    holder.selectedView.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                    holder.deleteButton.isEnabled = !isStarted
                } else {
                    super.onBindViewHolder(holder, position, payloads)
                }
            }

            private val updated = HashSet<ProxyEntity>()

            fun filter(name: String) {
                if (name.isEmpty()) {
                    reloadProfiles()
                    return
                }
                configurationIdList.clear()
                val lower = name.lowercase()
                configurationIdList.addAll(configurationList.filter {
                    it.value.displayName().lowercase().contains(lower) ||
                            it.value.displayType().lowercase().contains(lower) ||
                            it.value.displayAddress().lowercase().contains(lower)
                }.keys)
                notifyDataSetChanged()
            }

            fun move(from: Int, to: Int) {
                val first = getItemAt(from) ?: return
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                    -1, to + 1 downTo from
                )
                for (i in range) {
                    val next = getItemAt(i + step) ?: return
                    val order = next.userOrder
                    next.userOrder = previousOrder
                    previousOrder = order
                    configurationIdList[i] = next.id
                    updated.add(next)
                }
                first.userOrder = previousOrder
                configurationIdList[to] = first.id
                updated.add(first)
                notifyItemMoved(from, to)
            }

            fun commitMove() = runOnDefaultDispatcher {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                if (pos < 0) return
                configurationIdList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    pendingDeletedIds.remove(item.id)
                    configurationListView.post {
                        if (!configurationIdList.contains(item.id)) {
                            configurationList[item.id] = item
                            val safeIndex = index.coerceIn(0, configurationIdList.size)
                            configurationIdList.add(safeIndex, item.id)
                            notifyItemInserted(safeIndex)
                        }
                    }
                }
            }

            override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.map { it.second }
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                }
            }

            override suspend fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return

                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    val pos = itemCount
                    configurationList[profile.id] = profile
                    configurationIdList.add(profile.id)
                    notifyItemInserted(pos)
                }
            }

            override suspend fun onUpdated(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profile.id)
                if (index < 0) return
                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    configurationList[profile.id] = profile
                    notifyItemChanged(index)
                }
            }

            override suspend fun onUpdated(profileId: Long, trafficStats: TrafficStats) {
                val index = configurationIdList.indexOf(profileId)
                if (index != -1) {
                    val profile = configurationList[profileId]
                    if (profile != null) {
                        profile.stats = trafficStats
                        onMainDispatcher {
                            val holder = configurationListView.findViewHolderForAdapterPosition(index) as? ConfigurationHolder
                            if (holder != null && holder.entity.id == profileId) {
                                holder.updateTraffic(profile)
                            }
                        }
                    }
                }
            }

            override suspend fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return

                pendingDeletedIds.remove(profileId)
                configurationListView.post {
                    val index = configurationIdList.indexOf(profileId)
                    if (index >= 0) {
                        configurationIdList.removeAt(index)
                        configurationList.remove(profileId)
                        notifyItemRemoved(index)
                    }
                }
            }

            override suspend fun groupAdd(group: ProxyGroup) = Unit
            override suspend fun groupRemoved(groupId: Long) = Unit

            override suspend fun groupUpdated(group: ProxyGroup) {
                if (group.id != proxyGroup.id) return
                proxyGroup = group
                reloadProfiles()
            }

            override suspend fun groupUpdated(groupId: Long) {
                if (groupId != proxyGroup.id) return
                proxyGroup = SagerDatabase.groupDao.getById(groupId)!!
                reloadProfiles()
            }

            fun reloadProfiles() {
                val selectedItem = try {
                    requirePrent().selectedItem
                } catch (ignored: IllegalStateException) {
                    return
                }

                activeSelectionId = selectedItem?.id ?: DataStore.selectedProxy

                var newProfiles = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                newProfiles = newProfiles.filter { it.id !in pendingDeletedIds }
                when (proxyGroup.order) {
                    GroupOrder.BY_NAME -> {
                        newProfiles = newProfiles.sortedBy { it.displayName() }

                    }
                    GroupOrder.BY_DELAY -> {
                        newProfiles = newProfiles.sortedBy { if (it.status == 1) it.ping else 114514 }
                    }
                }

                configurationList.clear()
                configurationList.putAll(newProfiles.associateBy { it.id })
                val newProfileIds = newProfiles.map { it.id }

                var selectedProfileIndex = -1

                if (selected) {
                    val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                    selectedProfileIndex = newProfileIds.indexOf(selectedProxy)
                }

                configurationListView.post {
                    configurationIdList.clear()
                    configurationIdList.addAll(newProfileIds)
                    notifyDataSetChanged()

                    if (selectedProfileIndex != -1 && !scrolled) {
                        layoutManager.scrollToPositionWithOffset(selectedProfileIndex, 0)
                        scrolled = true
                    } else if (newProfiles.isNotEmpty() && !scrolled) {
                        layoutManager.scrollToPositionWithOffset(0, 0)
                        scrolled = true
                    }

                }
            }

        }

        val profileAccess = Mutex()
        val reloadAccess = Mutex()

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
            PopupMenu.OnMenuItemClickListener {

            lateinit var entity: ProxyEntity

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val profileStatus: TextView = view.findViewById(R.id.profile_status)

            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: ImageView = view.findViewById(R.id.edit)
            val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
            val shareLayout: LinearLayout = view.findViewById(R.id.share)
            val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
            val shareButton: ImageView = view.findViewById(R.id.shareIcon)
            val deleteButton: ImageView = view.findViewById(R.id.deleteIcon)

            fun bind(proxyEntity: ProxyEntity) {
                val parent = parent ?: return

                entity = proxyEntity

                if (parent.select) {
                    view.setOnClickListener {
                        (requireActivity() as SelectCallback).returnProfile(proxyEntity.id)
                    }
                } else {
                    val pa = activity as MainActivity

                    view.setOnClickListener {
                        runOnDefaultDispatcher {
                            var update: Boolean
                            profileAccess.withLock {
                                update = DataStore.selectedProxy != proxyEntity.id
                                DataStore.selectedProxy = proxyEntity.id
                            }

                            if (update) {
                                onMainDispatcher {
                                    adapter.refreshSelection()
                                }

                                if (pa.state.canStop && reloadAccess.tryLock()) {
                                    SagerNet.reloadService()
                                    reloadAccess.unlock()
                                }
                            } else if (SagerNet.isTv) {
                                if (SagerNet.started) {
                                    SagerNet.stopService()
                                } else {
                                    SagerNet.startService()
                                }
                            }
                        }

                    }
                }

                view.setOnLongClickListener(if (parent.select) null else View.OnLongClickListener {
                    PopupMenu(requireContext(), view).apply {
                        menuInflater.inflate(R.menu.profile_test_menu, menu)
                        menu.findItem(R.id.action_connection_url_test).isEnabled = !parent.testsRunning
                        menu.findItem(R.id.action_profile_speed_test).apply {
                            isEnabled = !parent.testsRunning && proxyEntity.status == 1
                            title = getString(R.string.profile_test_speed) + " (10 MiB)"
                        }
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_connection_url_test -> parent.urlTest(proxyEntity.id)
                                R.id.action_profile_speed_test -> parent.urlTest(proxyEntity.id, speed = true)
                            }
                            true
                        }
                        show()
                    }
                    true
                })
                dragHandle.isVisible = !parent.select && proxyGroup.type == GroupType.BASIC
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && isEnabled) {
                        dragHelper?.startDrag(this@ConfigurationHolder)
                    }
                    true
                }

                profileName.text = proxyEntity.displayName()
                profileType.text = proxyEntity.displayType()
                var rx = proxyEntity.rx
                var tx = proxyEntity.tx

                val stats = proxyEntity.stats
                if (stats != null) {
                    rx += stats.rxTotal
                    tx += stats.txTotal
                }

                val showTraffic = rx + tx != 0L
                trafficText.isVisible = showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(
                        R.string.traffic,
                        FormatFileSizeCompat.formatFileSize(view.context, tx, DataStore.useIECUnit),
                        FormatFileSizeCompat.formatFileSize(view.context, rx, DataStore.useIECUnit)
                    )
                }

                var address = proxyEntity.displayAddress()

                if (proxyEntity.requireBean().name.isEmpty() || !parent.alwaysShowAddress) {
                    address = ""
                }

                val balancerSnapshot = if (
                    proxyEntity.type == ProxyEntity.TYPE_BALANCER &&
                    SagerNet.started
                ) {
                    BalancerRuntimeState.get(proxyEntity.id)
                } else {
                    null
                }
                val balancerHint = if (proxyEntity.type == ProxyEntity.TYPE_BALANCER) {
                    BalancerDisplayResolver.buildHint(proxyEntity)
                } else {
                    null
                }

                if (proxyEntity.type == ProxyEntity.TYPE_BALANCER) {
                    val currentMemberName = balancerSnapshot?.activeProfileName
                        ?: balancerHint?.preferredMemberName
                    address = buildList {
                        add(
                            getString(
                                R.string.balancer_current_member_value,
                                currentMemberName
                                    ?: getString(R.string.balancer_current_member_pending)
                            )
                        )
                        if (
                            balancerSnapshot != null &&
                            balancerHint?.preferredMemberName != null &&
                            balancerHint.preferredMemberName != balancerSnapshot.activeProfileName
                        ) {
                            val it = balancerHint.preferredMemberName
                            add(getString(R.string.balancer_preferred_member_value, it))
                        }
                        if (balancerHint != null && balancerHint.totalMembers > 0) {
                            add(
                                getString(
                                    R.string.balancer_alive_members,
                                    balancerHint.aliveMembers,
                                    balancerHint.totalMembers
                                )
                            )
                        }
                        if (isEmpty() && address.isNotEmpty()) {
                            add(address)
                        }
                    }.joinToString("\n")
                }

                profileAddress.text = address
                (trafficText.parent as View).isGone = (!showTraffic || proxyEntity.status <= 0) && address.isEmpty()

                if (balancerSnapshot != null) {
                    profileStatus.text = getString(
                        R.string.balancer_connected_via,
                        balancerSnapshot.activeProfileName,
                    )
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
                } else if (proxyEntity.type == ProxyEntity.TYPE_BALANCER && balancerHint?.preferredMemberName != null) {
                    profileStatus.text = getString(
                        R.string.balancer_connected_via,
                        balancerHint.preferredMemberName,
                    )
                    profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                } else if (proxyEntity.type == ProxyEntity.TYPE_BALANCER) {
                    if (balancerHint != null && balancerHint.totalMembers > 0) {
                        profileStatus.text = getString(
                            R.string.balancer_alive_members_short,
                            balancerHint.aliveMembers,
                            balancerHint.totalMembers
                        )
                    } else {
                        profileStatus.setText(R.string.balancer_waiting_for_traffic)
                    }
                    profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                } else if (proxyEntity.status <= 0) {
                    if (showTraffic) {
                        profileStatus.text = trafficText.text
                        profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                        trafficText.text = ""
                    } else {
                        profileStatus.text = ""
                    }
                } else if (proxyEntity.status == 1) {
                    profileStatus.text = getString(R.string.available, proxyEntity.ping)
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
                } else {
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                    if (proxyEntity.status == 2) {
                        profileStatus.text = proxyEntity.error
                    }
                }

                if (proxyEntity.status == 3 && proxyEntity.type != ProxyEntity.TYPE_BALANCER) {
                    profileStatus.setText(R.string.unavailable)
                    profileStatus.setOnClickListener {
                        alert(proxyEntity.error ?: "<?>").show()
                    }
                } else {
                    profileStatus.setOnClickListener(null)
                }

                profileStatus.text = ProfileTestResults.statusText(
                    view.context, proxyEntity, profileStatus.text.toString()
                )
                profileStatus.gravity = Gravity.END
                if (ProfileTestState.get(proxyEntity.id)?.phase != null) {
                    profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                }

                editButton.setOnClickListener {
                    proxyEntity.settingIntent(it.context, proxyGroup.type == GroupType.SUBSCRIPTION)?.let {
                        editProfileLauncher.launch(it)
                    }
                }

                deleteButton.setOnClickListener { view ->
                    view.post {
                        adapter.let {
                            val index = it.configurationIdList.indexOf(proxyEntity.id)
                            if (index >= 0) {
                                it.remove(index)
                                it.pendingDeletedIds.add(proxyEntity.id)
                                undoManager.remove(index to proxyEntity)
                            }
                        }
                    }
                }

                // suppress ItemTouchHelper drag while a row button is held, to avoid conflict with parent item's long-press
                deleteButton.suppressDragWhilePressed { actionButtonPressed = it }
                editButton.suppressDragWhilePressed { actionButtonPressed = it }
                shareLayout.suppressDragWhilePressed { actionButtonPressed = it }

                editButton.isGone = parent.select
                deleteButton.isGone = parent.select
                shareButton.isGone = parent.select

                runOnDefaultDispatcher {
                    val selected = (parent.selectedItem?.id
                        ?: DataStore.selectedProxy) == proxyEntity.id
                    val started = selected && SagerNet.started && DataStore.startedProfile == proxyEntity.id
                    onMainDispatcher {
                        deleteButton.isEnabled = !started
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    }

                    fun showShare(anchor: View) {
                        val popup = PopupMenu(requireContext(), anchor)
                        popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                        if (!proxyEntity.hasShareLink() && proxyEntity.wgBean == null) {
                            popup.menu.removeItem(R.id.action_qr)
                            popup.menu.removeItem(R.id.action_clipboard)
                        }
                        if (showBackup && proxyEntity.canExportBackup()) {
                            popup.menu.findItem(R.id.action_export_backup).isVisible = true
                        }

                        popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                        popup.show()
                    }

                    if (!parent.select) {
                        val isInsecure = DataStore.profileSecurityAdvisory && proxyEntity.requireBean().isInsecure
                        onMainDispatcher {
                            if (isInsecure) {
                                shareLayer.setBackgroundColor(Color.RED)
                                shareButton.setImageResource(R.drawable.ic_baseline_warning_24)
                                shareButton.setColorFilter(Color.WHITE)
                            } else {
                                shareLayer.setBackgroundColor(Color.TRANSPARENT)
                                shareButton.setImageResource(R.drawable.ic_social_share)
                                shareButton.setColorFilter(Color.GRAY)

                            }
                            shareButton.isVisible = true
                            if (isInsecure) {
                                shareLayout.setOnClickListener {
                                    MaterialAlertDialogBuilder(requireContext())
                                        .setTitle(R.string.insecure_warn)
                                        .setMessage(R.string.insecure_warning_detail)
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            showShare(it)
                                        }
                                        .show()
                                }
                            } else {
                                shareLayout.setOnClickListener {
                                    showShare(it)
                                }
                            }

                        }
                    }
                }

            }

            fun updateTraffic(proxyEntity: ProxyEntity) {
                val parent = parent ?: return
                if (!::entity.isInitialized) return

                entity.stats = proxyEntity.stats

                var rx = proxyEntity.rx
                var tx = proxyEntity.tx

                val stats = proxyEntity.stats
                if (stats != null) {
                    rx += stats.rxTotal
                    tx += stats.txTotal
                }

                val showTraffic = rx + tx != 0L
                trafficText.isVisible = showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(
                        R.string.traffic,
                        FormatFileSizeCompat.formatFileSize(view.context, tx, DataStore.useIECUnit),
                        FormatFileSizeCompat.formatFileSize(view.context, rx, DataStore.useIECUnit)
                    )
                }

                var address = proxyEntity.displayAddress()
                if (proxyEntity.requireBean().name.isEmpty() || !parent.alwaysShowAddress) {
                    address = ""
                }

                (trafficText.parent as View).isGone = (!showTraffic || proxyEntity.status <= 0) && address.isEmpty()

                if (proxyEntity.status <= 0) {
                    if (showTraffic) {
                        profileStatus.text = ProfileTestResults.statusText(view.context, entity, trafficText.text.toString())
                        profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                        trafficText.text = ""
                    } else {
                        profileStatus.text = ProfileTestResults.statusText(view.context, entity, "")
                    }
                }
            }

            fun showCode(link: String) {
                QRCodeDialog(link).showAllowingStateLoss(parentFragmentManager)
            }

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                    .show()
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                try {
                    when (item.itemId) {
                        R.id.action_qr -> {
                            if (entity.wgBean != null) {
                                entity.wgBean?.toConf()?.let { showCode(it) }
                            } else {
                                entity.toLink()?.let { showCode(it) }
                            }
                        }
                        R.id.action_clipboard -> {
                            if (entity.wgBean != null) {
                                entity.wgBean?.toConf()?.let { export(it) }
                            } else {
                                entity.toLink()?.let { export(it) }
                            }
                        }
                        R.id.action_export_config_clipboard -> export(entity.exportConfig().first)
                        R.id.action_export_config_file -> {
                            val cfg = entity.exportConfig()
                            DataStore.serverConfig = cfg.first
                            startFilesForResult(
                                (parentFragment as ConfigurationFragment).exportConfig, cfg.second
                            )
                        }
                        R.id.action_export_backup_clipboard -> export(entity.requireBean().exportBackup())
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    (activity as MainActivity).snackbar(e.readableMessage).show()
                    return true
                }
                return true
            }
        }

        private val editProfileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    if (DataStore.currentProfile == DataStore.editingId) {
                        needReload()
                    }
                }
            }

    }

    private val exportConfig = registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
        if (data != null) {
            runOnDefaultDispatcher {
                try {
                    (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                        .bufferedWriter()
                        .use {
                            it.write(DataStore.serverConfig)
                        }
                    onMainDispatcher {
                        snackbar(getString(R.string.action_export_msg)).show()
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }

            }
        }
    }
}