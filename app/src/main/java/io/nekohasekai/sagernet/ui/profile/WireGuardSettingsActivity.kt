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

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.wireguard.WireGuardBean
import io.nekohasekai.sagernet.ktx.unwrapIDN

class WireGuardSettingsActivity : ProfileSettingsActivity<WireGuardBean>() {

    companion object {
        const val EXTRA_AMNEZIAWG_PROFILE = "amneziawg_profile"
    }

    override fun createEntity() = WireGuardBean().apply {
        if (intent.getBooleanExtra(EXTRA_AMNEZIAWG_PROFILE, false)) {
            name = getString(R.string.action_amneziawg)
        }
    }

    override fun WireGuardBean.init() {
        DataStore.profileName = name

        DataStore.serverLocalAddress = localAddress
        DataStore.serverPrivateKey = privateKey

        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort

        DataStore.serverCertificates = peerPublicKey
        DataStore.serverPassword = peerPreSharedKey

        DataStore.serverMTU = mtu
        DataStore.serverWireGuardReserved = reserved
        DataStore.serverWireGuardKeepaliveInterval = keepaliveInterval
        DataStore.serverAmneziaWgJc = amneziaJc
        DataStore.serverAmneziaWgJmin = amneziaJmin
        DataStore.serverAmneziaWgJmax = amneziaJmax
        DataStore.serverAmneziaWgS1 = amneziaS1
        DataStore.serverAmneziaWgS2 = amneziaS2
        DataStore.serverAmneziaWgS3 = amneziaS3
        DataStore.serverAmneziaWgS4 = amneziaS4
        DataStore.serverAmneziaWgH1 = amneziaH1
        DataStore.serverAmneziaWgH2 = amneziaH2
        DataStore.serverAmneziaWgH3 = amneziaH3
        DataStore.serverAmneziaWgH4 = amneziaH4
        DataStore.serverAmneziaWgI1 = amneziaI1
        DataStore.serverAmneziaWgI2 = amneziaI2
        DataStore.serverAmneziaWgI3 = amneziaI3
        DataStore.serverAmneziaWgI4 = amneziaI4
        DataStore.serverAmneziaWgI5 = amneziaI5
    }

    override fun WireGuardBean.serialize() {
        name = DataStore.profileName

        localAddress = DataStore.serverLocalAddress
        privateKey = DataStore.serverPrivateKey

        serverAddress = DataStore.serverAddress.unwrapIDN()
        serverPort = DataStore.serverPort

        peerPublicKey = DataStore.serverCertificates
        peerPreSharedKey = DataStore.serverPassword

        mtu = DataStore.serverMTU
        reserved = DataStore.serverWireGuardReserved
        keepaliveInterval = DataStore.serverWireGuardKeepaliveInterval
        amneziaJc = DataStore.serverAmneziaWgJc
        amneziaJmin = DataStore.serverAmneziaWgJmin
        amneziaJmax = DataStore.serverAmneziaWgJmax
        amneziaS1 = DataStore.serverAmneziaWgS1
        amneziaS2 = DataStore.serverAmneziaWgS2
        amneziaS3 = DataStore.serverAmneziaWgS3
        amneziaS4 = DataStore.serverAmneziaWgS4
        amneziaH1 = DataStore.serverAmneziaWgH1
        amneziaH2 = DataStore.serverAmneziaWgH2
        amneziaH3 = DataStore.serverAmneziaWgH3
        amneziaH4 = DataStore.serverAmneziaWgH4
        amneziaI1 = DataStore.serverAmneziaWgI1
        amneziaI2 = DataStore.serverAmneziaWgI2
        amneziaI3 = DataStore.serverAmneziaWgI3
        amneziaI4 = DataStore.serverAmneziaWgI4
        amneziaI5 = DataStore.serverAmneziaWgI5
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.wireguard_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_PRIVATE_KEY)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_MTU)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_WIREGUARD_KEEPALIVE_INTERVAL)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        listOf(
            Key.SERVER_AMNEZIAWG_JC,
            Key.SERVER_AMNEZIAWG_JMIN,
            Key.SERVER_AMNEZIAWG_JMAX,
            Key.SERVER_AMNEZIAWG_S1,
            Key.SERVER_AMNEZIAWG_S2,
            Key.SERVER_AMNEZIAWG_S3,
            Key.SERVER_AMNEZIAWG_S4,
        ).forEach {
            findPreference<EditTextPreference>(it)!!.apply {
                setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
            }
        }
    }

}
