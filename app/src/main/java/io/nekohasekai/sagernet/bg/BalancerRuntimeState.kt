package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import java.util.concurrent.ConcurrentHashMap

object BalancerRuntimeState {

    data class Snapshot(
        val balancerId: Long,
        val activeProfileId: Long,
        val activeProfileName: String,
        val updatedAt: Long,
    )

    private val snapshots = ConcurrentHashMap<Long, Snapshot>()
    private val listeners = ConcurrentHashMap<Any, (Long, Snapshot?) -> Unit>()

    fun get(balancerId: Long): Snapshot? = snapshots[balancerId]

    fun registerListener(
        key: Any,
        listener: (Long, Snapshot?) -> Unit,
    ) {
        listeners[key] = listener
    }

    fun unregisterListener(key: Any) {
        listeners.remove(key)
    }

    fun update(
        balancerId: Long,
        activeProfileId: Long,
        activeProfileName: String,
    ) {
        val previous = snapshots[balancerId]
        if (
            previous?.activeProfileId == activeProfileId &&
            previous.activeProfileName == activeProfileName
        ) {
            return
        }
        val snapshot = Snapshot(
            balancerId,
            activeProfileId,
            activeProfileName,
            System.currentTimeMillis(),
        )
        snapshots[balancerId] = snapshot
        publish(balancerId, snapshot)
    }

    fun clear(balancerId: Long) {
        if (snapshots.remove(balancerId) == null) return
        publish(balancerId, null)
    }

    private fun publish(balancerId: Long, snapshot: Snapshot?) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(balancerId)
        }
        runOnMainDispatcher {
            listeners.values.forEach { listener ->
                listener(balancerId, snapshot)
            }
        }
    }
}
