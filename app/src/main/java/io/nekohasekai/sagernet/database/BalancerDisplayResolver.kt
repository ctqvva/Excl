package io.nekohasekai.sagernet.database

import io.nekohasekai.sagernet.fmt.internal.BalancerBean

object BalancerDisplayResolver {

    data class Hint(
        val preferredMemberId: Long?,
        val preferredMemberName: String?,
        val aliveMembers: Int,
        val totalMembers: Int,
    )

    fun buildHint(balancer: ProxyEntity): Hint? {
        val bean = balancer.balancerBean ?: return null
        val members = when (bean.type) {
            BalancerBean.TYPE_GROUP -> {
                val excludeRegex = runCatching {
                    bean.nameFilter.takeIf { it.isNotEmpty() }?.let(::Regex)
                }.getOrNull()
                val includeRegex = runCatching {
                    bean.nameFilter1.takeIf { it.isNotEmpty() }?.let(::Regex)
                }.getOrNull()
                SagerDatabase.proxyDao.getByGroup(bean.groupId)
                    .filter { entity ->
                        val name = entity.requireBean().name
                        (excludeRegex == null || !excludeRegex.containsMatchIn(name)) &&
                            (includeRegex == null || includeRegex.containsMatchIn(name))
                    }
            }
            else -> SagerDatabase.proxyDao.getEntities(bean.proxies)
        }.filter { it.id != balancer.id }

        if (members.isEmpty()) return Hint(null, null, 0, 0)

        val aliveMembers = members.filter { it.status == 1 }
        val preferred = when {
            aliveMembers.isEmpty() -> null
            aliveMembers.size == 1 -> aliveMembers.first()
            bean.strategy == "leastPing" || bean.strategy == "leastLoad" -> {
                aliveMembers.minByOrNull { member ->
                    member.ping.takeIf { it > 0 } ?: Int.MAX_VALUE
                }
            }
            else -> aliveMembers.first()
        }

        return Hint(
            preferred?.id,
            preferred?.displayName(),
            aliveMembers.size,
            members.size,
        )
    }
}
