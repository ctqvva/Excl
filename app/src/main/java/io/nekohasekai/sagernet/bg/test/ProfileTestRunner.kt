package io.nekohasekai.sagernet.bg.test

import android.net.Network
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.TunImplementation
import io.nekohasekai.sagernet.bg.GuardedProcessPool
import io.nekohasekai.sagernet.bg.LocalResolver
import io.nekohasekai.sagernet.bg.proto.V2RayInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.buildV2RayConfig
import io.nekohasekai.sagernet.fmt.internal.BalancerBean
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import kotlinx.coroutines.*
import libexclavecore.Libexclavecore
import java.util.concurrent.atomic.AtomicReference

object ProfileTestRunner {
    enum class Phase { URL, COUNTRY, DOWNLOAD, UPLOAD }

    fun usesBrowserForwarder(profile: ProxyEntity, visited: MutableSet<Long> = HashSet()): Boolean {
        if (!visited.add(profile.id)) return false
        return when (val bean = profile.requireBean()) {
            is StandardV2RayBean -> (bean.type == "ws" && bean.wsUseBrowserForwarder) ||
                (bean.type == "splithttp" && bean.shUseBrowserForwarder)
            is ChainBean -> SagerDatabase.proxyDao.getEntities(bean.proxies).any { usesBrowserForwarder(it, visited) }
            is BalancerBean -> SagerDatabase.proxyDao.getEntities(bean.proxies).any { usesBrowserForwarder(it, visited) }
            else -> false
        }
    }

    suspend fun run(
        profile: ProxyEntity, speed: Boolean,
        onPhase: suspend (Phase) -> Unit,
        onResult: suspend (ProfileTestResults.Result) -> Unit,
        onError: suspend (Phase, Exception) -> Unit,
        onLatency: suspend (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val instance = TestInstance(profile)
        val pluginError = AtomicReference<Exception?>()
        val probe = AtomicReference<libexclavecore.ProfileProbe?>()
        instance.processes = GuardedProcessPool { error ->
            pluginError.set(error)
            probe.get()?.close()
        }
        try {
            instance.init()
            ensureActive()
            if (instance.config.requireWs || instance.config.requireSh) {
                throw UnsupportedOperationException(SagerNet.application.getString(R.string.profile_test_browser_unsupported))
            }
            instance.launch()
            if (instance.pluginPath.isNotEmpty() || profile.type == ProxyEntity.TYPE_BYEBYEDPI) delay(500)
            pluginError.get()?.let { throw it }
            val native = Libexclavecore.newProfileProbe(instance.v2rayPoint)
            probe.set(native)
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { native.close() }
            }
            try {
                var result = ProfileTestResults.get(profile)
                    ?: ProfileTestResults.Result(ProfileTestResults.fingerprint(profile))
                runProfileMeasurements(
                    probe = object : ProfileProbeMeasurements {
                        override fun urlTest() = native.urlTest(DataStore.connectionTestURL, 5000)
                        override fun country() = native.country().let { it.ip to it.countryCode }
                        override fun download() = native.download()
                        override fun upload() = native.upload()
                    },
                    speed = speed,
                    onPhase = onPhase,
                    onAvailable = { ping ->
                        onLatency(ping)
                        result = result.copy(checkedAt = System.currentTimeMillis(), ip = "", country = "",
                            download = if (speed) null else result.download,
                            upload = if (speed) null else result.upload)
                        onResult(result)
                    },
                    onCountry = { country ->
                        result = result.copy(ip = country.first, country = country.second)
                        onResult(result)
                    },
                    onDownload = { download ->
                        result = result.copy(download = download)
                        onResult(result)
                    },
                    onUpload = { upload -> onResult(result.copy(upload = upload)) },
                    onError = { phase, error ->
                        pluginError.get()?.let { throw it }
                        onError(phase, error)
                    },
                )
                pluginError.get()?.let { throw it }
            } finally {
                cancellation.cancelAndJoin()
            }
        } finally {
            probe.get()?.close()
            withContext(NonCancellable + Dispatchers.IO) { instance.close() }
        }
    }

    private class TestInstance(profile: ProxyEntity) : V2RayInstance(profile), LocalResolver {
        @Volatile override var underlyingNetwork: Network? = null

        override fun buildConfig() {
            config = buildV2RayConfig(profile, forTest = true)
            if (profile.type == ProxyEntity.TYPE_CONFIG && profile.configBean?.type == "v2ray") {
                // Full custom configs retain their inbounds in the shared builder.
                // Diagnostics need only outbound dispatch, never production listeners.
                val json = JsonParser.parseString(config.config).asJsonObject
                json.remove("inbounds")
                json.remove("api")
                json.remove("metrics")
                config.config = json.toString()
            }
        }

        override suspend fun init() {
            super.init()
            v2rayPoint.withLocalResolver(this)
            val protectPath = if (DataStore.tunImplementation == TunImplementation.SYSTEM &&
                DataStore.serviceMode == Key.MODE_VPN && SagerNet.started && DataStore.startedProfile > 0
            ) SagerNet.deviceStorage.noBackupFilesDir.toString() + "/protect_path" else ""
            v2rayPoint.withAlternativeSystemDialer(protectPath)
            DefaultNetworkListener.start(this) { underlyingNetwork = it }
        }

        override fun close() {
            runOnDefaultDispatcher { DefaultNetworkListener.stop(this@TestInstance) }
            super.close()
        }
    }
}
