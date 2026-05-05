package io.nekohasekai.sagernet.privacy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import io.nekohasekai.sagernet.R

object AppDisguiseManager {

    data class LauncherAlias(
        val value: String,
        val componentClassName: String,
        @StringRes val labelResId: Int,
    )

    private const val DEFAULT_ALIAS_VALUE = "default"

    val aliases = listOf(
        LauncherAlias(
            DEFAULT_ALIAS_VALUE,
            "io.nekohasekai.sagernet.ui.launcher.DefaultLauncherAlias",
            R.string.privacy_launcher_alias_default,
        ),
        LauncherAlias(
            "camera",
            "io.nekohasekai.sagernet.ui.launcher.CameraLauncherAlias",
            R.string.privacy_launcher_alias_camera,
        ),
        LauncherAlias(
            "shadowsocks",
            "io.nekohasekai.sagernet.ui.launcher.ShadowsocksLauncherAlias",
            R.string.privacy_launcher_alias_shadowsocks,
        ),
    )

    fun currentAliasValue(context: Context): String {
        val packageManager = context.packageManager
        return aliases.firstOrNull { alias ->
            isAliasEnabled(
                packageManager,
                ComponentName(context.packageName, alias.componentClassName),
            )
        }?.value ?: DEFAULT_ALIAS_VALUE
    }

    fun currentAliasLabel(context: Context): String {
        return labelForValue(context, currentAliasValue(context))
    }

    fun labelForValue(context: Context, value: String): String {
        return context.getString(
            aliases.firstOrNull { it.value == value }?.labelResId
                ?: R.string.privacy_launcher_alias_default
        )
    }

    fun apply(context: Context, value: String) {
        val selected = aliases.firstOrNull { it.value == value } ?: aliases.first()
        val packageManager = context.packageManager
        aliases.forEach { alias ->
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, alias.componentClassName),
                if (alias == selected) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun isAliasEnabled(
        packageManager: PackageManager,
        componentName: ComponentName,
    ): Boolean {
        return when (packageManager.getComponentEnabledSetting(componentName)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> runCatching {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(
                    componentName,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                ).enabled
            }.getOrDefault(false)
        }
    }
}
