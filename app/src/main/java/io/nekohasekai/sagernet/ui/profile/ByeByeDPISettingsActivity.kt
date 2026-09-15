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
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.byebyedpi.ByeByeDPIBean
import io.nekohasekai.sagernet.widget.SimpleMenuPreference

class ByeByeDPISettingsActivity : ProfileSettingsActivity<ByeByeDPIBean>() {

    override fun createEntity() = ByeByeDPIBean()

    override fun ByeByeDPIBean.init() {
        DataStore.profileName = name
        DataStore.serverByeByeDPIUseCommandLine = useCommandLine
        DataStore.serverByeByeDPICommandLine = commandLine
        DataStore.serverByeByeDPIMaxConnections = maxConnections
        DataStore.serverByeByeDPIBufferSize = bufferSize
        DataStore.serverByeByeDPIDefaultTtl = defaultTtl
        DataStore.serverByeByeDPINoDomain = noDomain
        DataStore.serverByeByeDPIDesyncHttp = desyncHttp
        DataStore.serverByeByeDPIDesyncHttps = desyncHttps
        DataStore.serverByeByeDPIDesyncUdp = desyncUdp
        DataStore.serverByeByeDPIDesyncMethod = desyncMethod
        DataStore.serverByeByeDPISplitPosition = splitPosition
        DataStore.serverByeByeDPISplitAtHost = splitAtHost
        DataStore.serverByeByeDPIFakeTtl = fakeTtl
        DataStore.serverByeByeDPIFakeSni = fakeSni
        DataStore.serverByeByeDPIOobChar = oobChar
        DataStore.serverByeByeDPIHostMixedCase = hostMixedCase
        DataStore.serverByeByeDPIDomainMixedCase = domainMixedCase
        DataStore.serverByeByeDPIHostRemoveSpaces = hostRemoveSpaces
        DataStore.serverByeByeDPITlsrecEnabled = tlsRecordSplit
        DataStore.serverByeByeDPITlsrecPosition = tlsRecordSplitPosition
        DataStore.serverByeByeDPITlsrecAtSni = tlsRecordSplitAtSni
        DataStore.serverByeByeDPIHostsMode = hostsMode
        DataStore.serverByeByeDPIHosts = hosts
        DataStore.serverByeByeDPITcpFastOpen = tcpFastOpen
        DataStore.serverByeByeDPIUdpFakeCount = udpFakeCount
        DataStore.serverByeByeDPIDropSack = dropSack
        DataStore.serverByeByeDPIFakeOffset = fakeOffset
    }

    override fun ByeByeDPIBean.serialize() {
        name = DataStore.profileName
        useCommandLine = DataStore.serverByeByeDPIUseCommandLine
        commandLine = DataStore.serverByeByeDPICommandLine
        maxConnections = DataStore.serverByeByeDPIMaxConnections
        bufferSize = DataStore.serverByeByeDPIBufferSize
        defaultTtl = DataStore.serverByeByeDPIDefaultTtl
        noDomain = DataStore.serverByeByeDPINoDomain
        desyncHttp = DataStore.serverByeByeDPIDesyncHttp
        desyncHttps = DataStore.serverByeByeDPIDesyncHttps
        desyncUdp = DataStore.serverByeByeDPIDesyncUdp
        desyncMethod = DataStore.serverByeByeDPIDesyncMethod
        splitPosition = DataStore.serverByeByeDPISplitPosition
        splitAtHost = DataStore.serverByeByeDPISplitAtHost
        fakeTtl = DataStore.serverByeByeDPIFakeTtl
        fakeSni = DataStore.serverByeByeDPIFakeSni
        oobChar = DataStore.serverByeByeDPIOobChar
        hostMixedCase = DataStore.serverByeByeDPIHostMixedCase
        domainMixedCase = DataStore.serverByeByeDPIDomainMixedCase
        hostRemoveSpaces = DataStore.serverByeByeDPIHostRemoveSpaces
        tlsRecordSplit = DataStore.serverByeByeDPITlsrecEnabled
        tlsRecordSplitPosition = DataStore.serverByeByeDPITlsrecPosition
        tlsRecordSplitAtSni = DataStore.serverByeByeDPITlsrecAtSni
        hostsMode = DataStore.serverByeByeDPIHostsMode
        hosts = DataStore.serverByeByeDPIHosts
        tcpFastOpen = DataStore.serverByeByeDPITcpFastOpen
        udpFakeCount = DataStore.serverByeByeDPIUdpFakeCount
        dropSack = DataStore.serverByeByeDPIDropSack
        fakeOffset = DataStore.serverByeByeDPIFakeOffset
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setTitle(R.string.byebyedpi_settings)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.byebyedpi_preferences)

        val useCommandLine =
            findPreference<SwitchPreferenceCompat>(Key.SERVER_BYEBYEDPI_USE_COMMAND_LINE)!!
        val commandLineCategory = findPreference<Preference>("byeByeDPICmdCategory")!!
        val uiCategory = findPreference<Preference>("byeByeDPIUiCategory")!!
        val desyncMethod = findPreference<SimpleMenuPreference>(Key.SERVER_BYEBYEDPI_DESYNC_METHOD)!!
        val fakeCategory = findPreference<Preference>("byeByeDPIFakeCategory")!!
        val oobPref = findPreference<EditTextPreference>(Key.SERVER_BYEBYEDPI_OOB_CHAR)!!
        val tlsrecEnabled =
            findPreference<SwitchPreferenceCompat>(Key.SERVER_BYEBYEDPI_TLSREC_ENABLED)!!
        val tlsrecPosition =
            findPreference<EditTextPreference>(Key.SERVER_BYEBYEDPI_TLSREC_POSITION)!!
        val tlsrecAtSni =
            findPreference<SwitchPreferenceCompat>(Key.SERVER_BYEBYEDPI_TLSREC_AT_SNI)!!
        val hostsMode = findPreference<SimpleMenuPreference>(Key.SERVER_BYEBYEDPI_HOSTS_MODE)!!
        val hostsPref = findPreference<EditTextPreference>(Key.SERVER_BYEBYEDPI_HOSTS)!!

        fun updateMode(useCmd: Boolean) {
            commandLineCategory.isVisible = useCmd
            uiCategory.isVisible = !useCmd
        }

        fun updateMethod(method: Int) {
            fakeCategory.isVisible =
                !useCommandLine.isChecked && method == ByeByeDPIBean.DESYNC_FAKE
            oobPref.isVisible =
                !useCommandLine.isChecked && (method == ByeByeDPIBean.DESYNC_OOB ||
                        method == ByeByeDPIBean.DESYNC_DISOOB)
        }

        fun updateTlsrec(enabled: Boolean) {
            val show = !useCommandLine.isChecked && enabled
            tlsrecPosition.isVisible = show
            tlsrecAtSni.isVisible = show
        }

        fun updateHosts(mode: Int) {
            hostsPref.isVisible =
                !useCommandLine.isChecked && mode != ByeByeDPIBean.HOSTS_DISABLE
        }

        updateMode(DataStore.serverByeByeDPIUseCommandLine)
        updateMethod(DataStore.serverByeByeDPIDesyncMethod)
        updateTlsrec(DataStore.serverByeByeDPITlsrecEnabled)
        updateHosts(DataStore.serverByeByeDPIHostsMode)

        useCommandLine.setOnPreferenceChangeListener { _, newValue ->
            val useCmd = newValue as Boolean
            updateMode(useCmd)
            updateMethod(desyncMethod.value?.toIntOrNull() ?: ByeByeDPIBean.DESYNC_OOB)
            updateTlsrec(tlsrecEnabled.isChecked)
            updateHosts(hostsMode.value?.toIntOrNull() ?: ByeByeDPIBean.HOSTS_DISABLE)
            true
        }
        desyncMethod.setOnPreferenceChangeListener { _, newValue ->
            updateMethod((newValue as String).toIntOrNull() ?: ByeByeDPIBean.DESYNC_OOB)
            true
        }
        tlsrecEnabled.setOnPreferenceChangeListener { _, newValue ->
            updateTlsrec(newValue as Boolean)
            true
        }
        hostsMode.setOnPreferenceChangeListener { _, newValue ->
            updateHosts((newValue as String).toIntOrNull() ?: ByeByeDPIBean.HOSTS_DISABLE)
            true
        }

        listOf(
            Key.SERVER_BYEBYEDPI_MAX_CONNECTIONS,
            Key.SERVER_BYEBYEDPI_BUFFER_SIZE,
            Key.SERVER_BYEBYEDPI_DEFAULT_TTL,
            Key.SERVER_BYEBYEDPI_SPLIT_POSITION,
            Key.SERVER_BYEBYEDPI_FAKE_TTL,
            Key.SERVER_BYEBYEDPI_FAKE_OFFSET,
            Key.SERVER_BYEBYEDPI_TLSREC_POSITION,
            Key.SERVER_BYEBYEDPI_UDP_FAKE_COUNT,
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }
}
