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

package io.nekohasekai.sagernet.bg.proto

import com.v2ray.core.app.observatory.OutboundStatus
import io.nekohasekai.sagernet.LogLevel
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.BalancerRuntimeState
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.BalancerDisplayResolver
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import libsagernetcore.ObservatoryStatusUpdateListener
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timerTask

class ProxyInstance(profile: ProxyEntity, val service: BaseService.Interface) : V2RayInstance(
    profile
),
    ObservatoryStatusUpdateListener {

    lateinit var observatoryJob: Job

    override suspend fun init() {
        super.init()

        if (DataStore.logLevel == LogLevel.DEBUG) {
            Logs.d(config.config)
            pluginConfigs.forEach { (_, plugin) ->
                val (_, content) = plugin
                Logs.d(content)
            }
        }
    }

    override fun launch() {
        super.launch()

        if (config.observerTag.isNotEmpty()) {
            v2rayPoint.setStatusUpdateListener(config.observerTag, this)
            observatoryJob = runOnDefaultDispatcher {
                sendInitStatuses()
            }
        }

        SagerNet.started = true
        if (profile.balancerBean != null) {
            BalancerDisplayResolver.buildHint(profile)?.let { hint ->
                val memberId = hint.preferredMemberId
                val memberName = hint.preferredMemberName
                if (memberId != null && memberName != null) {
                    BalancerRuntimeState.update(profile.id, memberId, memberName)
                }
            }
        }
    }

    fun sendInitStatuses() {
        val time = (System.currentTimeMillis() / 1000) - 300
        for (observatoryTag in config.observatoryTags) {
            val profileId = observatoryTag.substringAfter("global-")
            val id = profileId.toLongOrNull()
            if (id == null) {
                continue
            }
            val profile = when {
                id == profile.id -> profile
                statsOutbounds.containsKey(id) -> statsOutbounds[id]!!.proxyEntity
                else -> SagerDatabase.proxyDao.getById(id)
            } ?: continue

            if (profile.status > 0) v2rayPoint.updateStatus(
                config.observerTag,
                OutboundStatus.newBuilder()
                    .setOutboundTag(observatoryTag)
                    .setAlive(profile.status == 1)
                    .setDelay(profile.ping.toLong())
                    .setLastErrorReason(profile.error ?: "")
                    .setLastTryTime(time)
                    .setLastSeenTime(time)
                    .build()
                    .toByteArray()
            )
        }
    }

    val updateTimer = lazy { Timer("Observatory Timer") }
    val updateTasks by lazy { ConcurrentHashMap<Long, TimerTask>() }

    @Throws(Exception::class)
    override fun onUpdateObservatoryStatus(statusPb: ByteArray?) {
        if (statusPb == null || statusPb.isEmpty()) {
            return
        }
        val status = OutboundStatus.parseFrom(statusPb)
        val profileId = status.outboundTag.substringAfter("global-")
        val id = profileId.toLongOrNull()
        if (id != null) {
            val profile = when {
                id == profile.id -> profile
                statsOutbounds.containsKey(id) -> statsOutbounds[id]!!.proxyEntity
                else -> {
                    SagerDatabase.proxyDao.getById(id)
                }
            }

            if (profile != null) {
                val newStatus = if (status.alive) 1 else 3
                val newDelay = status.delay.toInt()
                val newErrorReason = status.lastErrorReason

                if (profile.status != newStatus || profile.ping != newDelay || profile.error != newErrorReason) {
                    profile.status = newStatus
                    profile.ping = newDelay
                    profile.error = newErrorReason

                    SagerDatabase.proxyDao.updateProxy(profile)

                    Logs.d("Send result for #$profileId ${profile.displayName()}")

                    val groupId = profile.groupId
                    val task = timerTask {
                        if (updateTasks[groupId] == this) {
                            runOnDefaultDispatcher {
                                service.data.binder.broadcast {
                                    it.observatoryResultsUpdated(groupId)
                                }
                            }
                            updateTasks.remove(groupId)
                        }
                    }
                    updateTimer.value.schedule(task, 2000L)
                    updateTasks.put(groupId, task)?.cancel()
                }
            } else {
                Logs.d("Profile with id #$profileId not found")
            }
        } else {
            Logs.d("Persist skipped on outbound ${status.outboundTag}")
        }
    }

    override fun close() {
        SagerNet.started = false
        if (profile.balancerBean != null) {
            BalancerRuntimeState.clear(profile.id)
        }

        persistStats()
        super.close()

        if (updateTimer.isInitialized()) updateTimer.value.cancel()
        if (::observatoryJob.isInitialized) observatoryJob.cancel()
    }

// ------------- stats -------------

    private fun queryStats(tag: String, direct: String): Long {
        return v2rayPoint.queryStats(tag, direct)
    }

    private val currentTags by lazy {
        mapOf(* config.outboundTagsCurrent.map {
            it to config.outboundTagsAll[it]
        }.toTypedArray())
    }

    private val statsTags by lazy {
        mapOf(*  config.outboundTags.toMutableList().apply {
            removeAll(config.outboundTagsCurrent)
        }.map {
            it to config.outboundTagsAll[it]
        }.toTypedArray())
    }

    private val interTags by lazy {
        config.outboundTagsAll.filterKeys { !config.outboundTags.contains(it) }
    }
    private val balancerTrackedMembers by lazy {
        config.balancerTrackedMembers[profile.id].orEmpty()
    }
    private val balancerMemberTotals = hashMapOf<Long, Long>()

    class OutboundStats(
        val proxyEntity: ProxyEntity, var uplinkTotal: Long = 0L, var downlinkTotal: Long = 0L
    )

    private val statsOutbounds = hashMapOf<Long, OutboundStats>()
    private fun registerStats(
        proxyEntity: ProxyEntity, uplink: Long? = null, downlink: Long? = null
    ) {
        if (proxyEntity.id == outboundStats.proxyEntity.id) return
        val stats = statsOutbounds.getOrPut(proxyEntity.id) {
            OutboundStats(proxyEntity)
        }
        if (uplink != null) {
            stats.uplinkTotal += uplink
        }
        if (downlink != null) {
            stats.downlinkTotal += downlink
        }
    }

    var uplinkProxy = 0L
    var downlinkProxy = 0L
    var uplinkTotalDirect = 0L
    var downlinkTotalDirect = 0L

    private val outboundStats = OutboundStats(profile)
    fun outboundStats(): Pair<OutboundStats, HashMap<Long, OutboundStats>> {
        if (!isInitialized()) return outboundStats to statsOutbounds
        uplinkProxy = 0L
        downlinkProxy = 0L

        val currentTraffic = currentTags.map { (tag, profile) ->
            val uplink = queryStats(tag, "uplink")
            val downlink = queryStats(tag, "downlink")
            profile?.also {
                registerStats(it, uplink = uplink)
                registerStats(it, downlink = downlink)
            }
            Triple(profile, uplink, downlink)
        }
        uplinkProxy += currentTraffic.fold(0L) { acc, (_, uplink, _) -> acc + uplink }
        downlinkProxy += currentTraffic.fold(0L) { acc, (_, _, downlink) -> acc + downlink }

        outboundStats.uplinkTotal += uplinkProxy
        outboundStats.downlinkTotal += downlinkProxy

        if (statsTags.isNotEmpty()) {
            uplinkProxy += statsTags.map { (tag, profile) ->
                queryStats(tag, "uplink").apply {
                    profile?.also {
                        registerStats(it, uplink = this)
                    }
                }
            }.fold(0L) { acc, l -> acc + l }
            downlinkProxy += statsTags.map { (tag, profile) ->
                queryStats(tag, "downlink").apply {
                    profile?.also {
                        registerStats(it, downlink = this)
                    }
                }
            }.fold(0L) { acc, l -> acc + l }
        }

        if (interTags.isNotEmpty()) {
            interTags.map { (tag, profile) ->
                queryStats(tag, "uplink").also { registerStats(profile, uplink = it) }
            }
            interTags.map { (tag, profile) ->
                queryStats(tag, "downlink").also {
                    registerStats(profile, downlink = it)
                }
            }
        }

        if (profile.balancerBean != null && balancerTrackedMembers.isNotEmpty()) {
            balancerTrackedMembers.values
                .map { member ->
                    val total = member.trafficProfileIds.fold(0L) { acc, trafficProfileId ->
                        acc + (statsOutbounds[trafficProfileId]?.let {
                            it.uplinkTotal + it.downlinkTotal
                        } ?: 0L)
                    }
                    val delta = total - (balancerMemberTotals[member.profileId] ?: 0L)
                    balancerMemberTotals[member.profileId] = total
                    Triple(member, total, delta)
                }
                .maxByOrNull { (_, total, delta) ->
                    if (delta > 0L) delta else total
                }
                ?.takeIf { (_, total, delta) -> delta > 0L || total > 0L }
                ?.first
                ?.also { member ->
                    BalancerRuntimeState.update(profile.id, member.profileId, member.profileName)
                }
        }

        return outboundStats to statsOutbounds
    }

    fun bypassStats(direct: String): Long {
        if (!isInitialized()) return 0L
        return queryStats(config.bypassTag, direct)
    }

    fun uplinkDirect() = bypassStats("uplink").also {
        uplinkTotalDirect += it
    }

    fun downlinkDirect() = bypassStats("downlink").also {
        downlinkTotalDirect += it
    }

    fun persistStats() {
        runBlocking {
            try {
                outboundStats()

                if (outboundStats.uplinkTotal + outboundStats.downlinkTotal != 0L) {
                    SagerDatabase.proxyDao.updateTraffic(
                        profile.id,
                        profile.tx + outboundStats.uplinkTotal,
                        profile.rx + outboundStats.downlinkTotal
                    )
                }

                statsOutbounds.values.forEach {
                    if (it.uplinkTotal + it.downlinkTotal != 0L) {
                        SagerDatabase.proxyDao.updateTraffic(
                            it.proxyEntity.id,
                            it.proxyEntity.tx + it.uplinkTotal,
                            it.proxyEntity.rx + it.downlinkTotal
                        )
                    }
                }
            } catch (e: IOException) {
                throw e
            }
        }
    }

}
