package io.nekohasekai.sagernet.privacy

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.nekohasekai.sagernet.BuildConfig

object PrivacyDefaults {

    private val exactBadActorPackages = setOf(
        "com.cherepavel.vpndetector",
        "dev.loop.yourvpndead",
        "ru.xtclovver.rknhardering",
    )

    private val packageMarkers = listOf(
        "vpndetector",
        "vpn-detector",
        "yourvpndead",
        "vpndead",
        "rknhardering",
        "rknhardening",
        "roskomnadzor",
        "anti-vpn",
        "antivpn",
    )

    private val labelMarkers = listOf(
        "vpn detector",
        "your vpn dead",
        "rkn hardering",
        "rkn hardening",
        "anti vpn",
    )

    private val detectorPermissions = setOf(
        Manifest.permission.QUERY_ALL_PACKAGES,
        "android.permission.PACKAGE_USAGE_STATS",
    )

    fun suggestedBadActorPackages(
        packages: Map<String, PackageInfo>,
        packageManager: PackageManager,
    ): List<String> {
        return packages.values
            .filter { isSuggestedBadActor(it, packageManager) }
            .map { it.packageName }
            .sorted()
    }

    fun mergeBadActorPackages(existingRaw: String, additions: Collection<String>): String {
        val merged = linkedSetOf<String>()
        existingRaw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach(merged::add)
        additions.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach(merged::add)
        return merged.joinToString("\n")
    }

    private fun isSuggestedBadActor(
        info: PackageInfo,
        packageManager: PackageManager,
    ): Boolean {
        val packageName = info.packageName.lowercase()
        if (packageName == BuildConfig.APPLICATION_ID.lowercase()) return false
        if (packageName in exactBadActorPackages) return true
        if (packageMarkers.any(packageName::contains)) return true

        val label = info.applicationInfo?.loadLabel(packageManager)?.toString()
            ?.lowercase()
            .orEmpty()
        if (labelMarkers.any(label::contains)) return true

        val requestedPermissions = info.requestedPermissions?.toSet().orEmpty()
        return requestedPermissions.any(detectorPermissions::contains) &&
            ("detector" in packageName || "detector" in label)
    }
}
