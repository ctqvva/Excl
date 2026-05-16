/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.vkturn.VkTurnBean
import io.nekohasekai.sagernet.ktx.unwrapIDN
import io.nekohasekai.sagernet.widget.SimpleMenuPreference

class VkTurnSettingsActivity : ProfileSettingsActivity<VkTurnBean>() {

    override fun createEntity() = VkTurnBean()

    override fun VkTurnBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverVkTurnMode = mode
        DataStore.serverVkTurnProvider = provider
        DataStore.serverVkTurnCallLink = callLink
        DataStore.serverVkTurnTurnHost = turnHost
        DataStore.serverVkTurnTurnPort = turnPort
        DataStore.serverVkTurnStreams = streams
        DataStore.serverVkTurnUdp = udp
        DataStore.serverVkTurnDebug = debug
        DataStore.serverVkTurnManualCaptcha = manualCaptcha
        DataStore.serverVkTurnDns = dns
        DataStore.serverVkTurnAllocsPerStream = allocsPerStream
        DataStore.serverVkTurnNoDtls = noDtls
        DataStore.serverVkTurnSessionId = sessionId
        DataStore.serverVkTurnV1 = protocolV1

        DataStore.serverLocalAddress = localAddress
        DataStore.serverPrivateKey = privateKey
        DataStore.serverCertificates = peerPublicKey
        DataStore.serverPassword = peerPreSharedKey
        DataStore.serverMTU = mtu
        DataStore.serverWireGuardReserved = reserved
        DataStore.serverWireGuardKeepaliveInterval = keepaliveInterval
        DataStore.serverUserId = uuid
    }

    override fun VkTurnBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress.unwrapIDN()
        serverPort = DataStore.serverPort
        mode = DataStore.serverVkTurnMode
        provider = DataStore.serverVkTurnProvider
        callLink = DataStore.serverVkTurnCallLink
        turnHost = DataStore.serverVkTurnTurnHost.unwrapIDN()
        turnPort = DataStore.serverVkTurnTurnPort
        streams = DataStore.serverVkTurnStreams
        udp = DataStore.serverVkTurnUdp
        debug = DataStore.serverVkTurnDebug
        manualCaptcha = DataStore.serverVkTurnManualCaptcha
        dns = DataStore.serverVkTurnDns
        allocsPerStream = DataStore.serverVkTurnAllocsPerStream
        noDtls = DataStore.serverVkTurnNoDtls
        sessionId = DataStore.serverVkTurnSessionId
        protocolV1 = DataStore.serverVkTurnV1

        localAddress = DataStore.serverLocalAddress
        privateKey = DataStore.serverPrivateKey
        peerPublicKey = DataStore.serverCertificates
        peerPreSharedKey = DataStore.serverPassword
        mtu = DataStore.serverMTU
        reserved = DataStore.serverWireGuardReserved
        keepaliveInterval = DataStore.serverWireGuardKeepaliveInterval
        uuid = DataStore.serverUserId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setTitle(R.string.vk_turn_settings)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.vkturn_preferences)

        val mode = findPreference<SimpleMenuPreference>(Key.SERVER_VKTURN_MODE)!!
        val provider = findPreference<SimpleMenuPreference>(Key.SERVER_VKTURN_PROVIDER)!!
        val callLink = findPreference<EditTextPreference>(Key.SERVER_VKTURN_CALL_LINK)!!
        val wireGuardCategory = findPreference<Preference>("vkTurnWireGuardCategory")!!
        val vlessCategory = findPreference<Preference>("vkTurnVlessCategory")!!

        fun updateMode(value: Int) {
            wireGuardCategory.isVisible = value == VkTurnBean.MODE_WIREGUARD
            vlessCategory.isVisible = value == VkTurnBean.MODE_VLESS
        }
        fun updateProvider(value: Int) {
            callLink.isVisible = value != VkTurnBean.PROVIDER_WB
        }

        updateMode(DataStore.serverVkTurnMode)
        updateProvider(DataStore.serverVkTurnProvider)
        mode.setOnPreferenceChangeListener { _, newValue ->
            updateMode((newValue as String).toIntOrNull() ?: VkTurnBean.MODE_WIREGUARD)
            true
        }
        provider.setOnPreferenceChangeListener { _, newValue ->
            updateProvider((newValue as String).toIntOrNull() ?: VkTurnBean.PROVIDER_VK)
            true
        }

        listOf(
            Key.SERVER_PORT,
            Key.SERVER_VKTURN_TURN_PORT,
            Key.SERVER_VKTURN_STREAMS,
            Key.SERVER_MTU,
            Key.SERVER_WIREGUARD_KEEPALIVE_INTERVAL,
            Key.SERVER_VKTURN_ALLOCS_PER_STREAM,
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        listOf(
            Key.SERVER_VKTURN_CALL_LINK,
            Key.SERVER_PRIVATE_KEY,
            Key.SERVER_PASSWORD,
            Key.SERVER_USER_ID,
            Key.SERVER_VKTURN_SESSION_ID,
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.summaryProvider = PasswordSummaryProvider
        }
    }
}
