package io.nekohasekai.sagernet.bg.test

import android.net.Network
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
    enum class Phase { COUNTRY, DOWNLOAD, UPLOAD }

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
            instance.launch()
            if (instance.pluginPath.isNotEmpty() || profile.type == ProxyEntity.TYPE_BYEBYEDPI) delay(500)
            val native = Libexclavecore.newProfileProbe(instance.v2rayPoint)
            probe.set(native)
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { native.close() }
            }
            try {
                var result = ProfileTestResults.Result(ProfileTestResults.fingerprint(profile))
                onPhase(Phase.COUNTRY)
                val country = native.country()
                pluginError.get()?.let { throw it }
                ensureActive()
                result = result.copy(ip = country.ip, country = country.countryCode)
                onResult(result)
                if (speed) {
                    onPhase(Phase.DOWNLOAD)
                    val download = native.download()
                    ensureActive()
                    result = result.copy(download = download)
                    onResult(result)
                    onPhase(Phase.UPLOAD)
                    val upload = native.upload()
                    ensureActive()
                    onResult(result.copy(upload = upload))
                }
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

        override fun buildConfig() { config = buildV2RayConfig(profile, forTest = true) }

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
