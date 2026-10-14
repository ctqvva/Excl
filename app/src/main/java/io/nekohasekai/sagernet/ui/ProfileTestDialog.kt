package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.test.ProfileTestResults
import io.nekohasekai.sagernet.bg.test.ProfileTestRunner
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutProfileTestResultBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileTestsBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.readableMessage
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

class ProfileTestDialog : BottomSheetDialogFragment() {
    private var binding: LayoutProfileTestsBinding? = null
    private val rows = ArrayList<Row>()
    private val adapter = ResultAdapter()
    private var job: Job? = null
    private var speed = false

    private data class Row(
        val profile: ProxyEntity,
        var stage: String,
        var result: ProfileTestResults.Result? = null,
        var failed: Boolean = false,
    )

    override fun getTheme() = R.style.Theme_Excl_ProfileTests

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        // Inflate in the sheet's full M3 Expressive theme, independently of the legacy host.
        val themedInflater = LayoutInflater.from(requireDialog().context)
        return LayoutProfileTestsBinding.inflate(themedInflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = binding!!
        speed = savedInstanceState?.getBoolean("speed") ?: requireArguments().getBoolean("speed")
        ui.testList.layoutManager = LinearLayoutManager(requireContext())
        ui.testList.adapter = adapter
        ui.testModes.check(if (speed) R.id.test_speed else R.id.test_country)
        updateMode()
        ui.testModes.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                speed = id == R.id.test_speed
                updateMode()
                adapter.notifyDataSetChanged()
            }
        }
        ui.testRun.isEnabled = false
        ui.testRun.setOnClickListener {
            if (job?.isActive == true) {
                job?.cancel()
                ui.testRun.isEnabled = false
            } else startTests()
        }
        ui.testClose.setOnClickListener { dismiss() }
        viewLifecycleOwner.lifecycleScope.launch {
            val (scope, profiles) = withContext(Dispatchers.IO) {
                val profileId = requireArguments().getLong("profileId")
                if (profileId > 0) {
                    val profile = ProfileManager.getProfile(profileId)
                    (profile?.displayName().orEmpty()) to listOfNotNull(profile)
                } else {
                    val groupId = requireArguments().getLong("groupId")
                    val group = SagerDatabase.groupDao.getById(groupId)
                    (group?.displayName().orEmpty()) to SagerDatabase.proxyDao.getByGroup(groupId)
                }
            }
            ui.testScope.text = scope
            rows.addAll(profiles.map { Row(it, getString(R.string.profile_test_ready), ProfileTestResults.get(it)) })
            adapter.notifyDataSetChanged()
            ui.testProgressText.text = getString(
                if (rows.isEmpty()) R.string.profile_test_no_profiles else R.string.profile_test_ready
            )
            ui.testRun.isEnabled = rows.isNotEmpty()
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as BottomSheetDialog).behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        binding?.root?.layoutParams = binding?.root?.layoutParams?.apply {
            height = (resources.displayMetrics.heightPixels * 0.85).toInt()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("speed", speed)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        job?.cancel()
        binding = null
        rows.clear()
        super.onDestroyView()
    }

    private fun updateMode() {
        binding?.apply {
            testTitle.setText(if (speed) R.string.profile_test_speed else R.string.profile_test_country)
            testNote.setText(if (speed) R.string.profile_test_speed_note else R.string.profile_test_country_note)
        }
    }

    private fun setRunning(running: Boolean) {
        binding?.apply {
            testCountry.isEnabled = !running
            testSpeed.isEnabled = !running
            testRun.isEnabled = rows.isNotEmpty()
            testRun.setText(if (running) R.string.profile_test_stop else R.string.profile_test_start)
        }
        isCancelable = !running
    }

    private fun startTests() {
        job = viewLifecycleOwner.lifecycleScope.launch {
            setRunning(true)
            val epoch = ProfileTestResults.changes.value
            var finished = 0
            rows.forEach { it.stage = getString(R.string.profile_test_ready); it.failed = false }
            adapter.notifyDataSetChanged()
            binding?.testProgress?.setProgressCompat(0, false)
            try {
                for ((index, row) in rows.withIndex()) {
                    ensureActive()
                    try {
                        val browser = withContext(Dispatchers.IO) { ProfileTestRunner.usesBrowserForwarder(row.profile) }
                        if (browser) error(getString(R.string.profile_test_browser_unsupported))
                        ProfileTestRunner.run(row.profile, speed,
                            onPhase = { phase ->
                                withContext(Dispatchers.Main) {
                                    row.stage = getString(when (phase) {
                                        ProfileTestRunner.Phase.COUNTRY -> R.string.profile_test_checking_country
                                        ProfileTestRunner.Phase.DOWNLOAD -> R.string.profile_test_downloading
                                        ProfileTestRunner.Phase.UPLOAD -> R.string.profile_test_uploading
                                    })
                                    adapter.notifyItemChanged(index)
                                    binding?.testProgressText?.text = "${row.profile.displayName()} · ${row.stage}"
                                    binding?.testList?.smoothScrollToPosition(index)
                                }
                            },
                            onResult = { result ->
                                ensureActive()
                                if (!ProfileTestResults.put(row.profile.id, result, epoch)) {
                                    error(getString(R.string.profile_test_results_changed))
                                }
                                ProfileManager.postUpdate(row.profile.id)
                                withContext(Dispatchers.Main) { row.result = result; adapter.notifyItemChanged(index) }
                            },
                        )
                        row.stage = getString(R.string.profile_test_complete)
                    } catch (e: CancellationException) {
                        row.stage = getString(R.string.profile_test_stopped)
                        throw e
                    } catch (e: Exception) {
                        row.stage = e.readableMessage
                        row.failed = true
                    }
                    finished++
                    adapter.notifyItemChanged(index)
                    binding?.apply {
                        testProgress.setProgressCompat(finished * 100 / rows.size, true)
                        testProgressText.text = getString(R.string.profile_test_progress, finished, rows.size)
                    }
                }
            } finally {
                if (!isActive) {
                    binding?.testProgressText?.setText(R.string.profile_test_stopped)
                    adapter.notifyDataSetChanged()
                }
                setRunning(false)
            }
        }
    }

    private inner class ResultAdapter : RecyclerView.Adapter<ResultHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ResultHolder(
            LayoutProfileTestResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: ResultHolder, position: Int) = holder.bind(rows[position])
    }

    private inner class ResultHolder(val ui: LayoutProfileTestResultBinding) : RecyclerView.ViewHolder(ui.root) {
        fun bind(row: Row) {
            ui.testProfileName.text = row.profile.displayName()
            ui.testProfileStage.text = row.stage
            ui.testProfileStage.setTextColor(ui.root.context.getColorAttr(
                if (row.failed) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant
            ))
            val result = row.result
            ui.testProfileCountry.isVisible = result?.country?.isNotEmpty() == true
            ui.testProfileCountry.text = result?.let { "${countryLabel(it.country)}\n${it.ip}" }
            ui.testProfileRates.isVisible = speed || result?.download != null || result?.upload != null
            ui.testProfileDownload.text = formatRate(result?.download)
            ui.testProfileUpload.text = formatRate(result?.upload)
        }
    }

    private fun formatRate(rate: Double?): String = rate?.let {
        getString(R.string.profile_test_mbps, NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
        }.format(it / 1_000_000))
    } ?: "—"

    companion object {
        fun create(groupId: Long = 0, profileId: Long = 0, speed: Boolean = false) = ProfileTestDialog().apply {
            arguments = bundleOf("groupId" to groupId, "profileId" to profileId, "speed" to speed)
        }

        fun countryLabel(code: String): String {
            if (code.length != 2 || code.any { it !in 'A'..'Z' }) return code
            val flag = code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
            return "$flag ${Locale("", code).displayCountry}"
        }

        fun summary(context: android.content.Context, result: ProfileTestResults.Result): String = buildList {
            if (result.country.isNotEmpty()) add(countryLabel(result.country))
            val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
            result.download?.let { add("↓ ${context.getString(R.string.profile_test_mbps, formatter.format(it / 1_000_000))}") }
            result.upload?.let { add("↑ ${context.getString(R.string.profile_test_mbps, formatter.format(it / 1_000_000))}") }
        }.joinToString(" · ")

        fun lastChecked(context: android.content.Context, result: ProfileTestResults.Result): String = context.getString(
            R.string.profile_test_last_checked,
            "${DateFormat.getDateFormat(context).format(Date(result.checkedAt))} ${DateFormat.getTimeFormat(context).format(Date(result.checkedAt))}"
        )
    }
}
