package io.nekohasekai.sagernet.bg.test

import android.content.Context
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.KryoConverters
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest

/** Local test metadata, deliberately separate from the profile/backup schema. */
object ProfileTestResults {
    data class Result(
        val fingerprint: String,
        val checkedAt: Long = System.currentTimeMillis(),
        val ip: String = "",
        val country: String = "",
        val download: Double? = null,
        val upload: Double? = null,
    )

    private val preferences by lazy {
        SagerNet.application.getSharedPreferences("profile_test_results", Context.MODE_PRIVATE)
    }
    val changes = MutableStateFlow(0L)

    fun fingerprint(profile: ProxyEntity): String = MessageDigest.getInstance("SHA-256")
        .digest(KryoConverters.serialize(profile.requireBean()))
        .joinToString("") { "%02x".format(it) }

    fun get(profile: ProxyEntity): Result? = runCatching {
        val json = JSONObject(preferences.getString(profile.id.toString(), null) ?: return null)
        if (json.getString("fingerprint") != fingerprint(profile)) return null
        Result(
            json.getString("fingerprint"), json.getLong("checkedAt"),
            json.optString("ip"), json.optString("country"),
            if (json.has("download")) json.getDouble("download") else null,
            if (json.has("upload")) json.getDouble("upload") else null,
        )
    }.getOrNull()

    // Epochs prevent a finished request from restoring results after Clear or an edit.
    @Synchronized fun put(profileId: Long, result: Result, epoch: Long): Boolean {
        if (changes.value != epoch) return false
        val json = JSONObject().put("fingerprint", result.fingerprint)
            .put("checkedAt", result.checkedAt).put("ip", result.ip).put("country", result.country)
        result.download?.let { json.put("download", it) }
        result.upload?.let { json.put("upload", it) }
        preferences.edit().putString(profileId.toString(), json.toString()).apply()
        return true
    }

    @Synchronized fun invalidateAll() {
        preferences.edit().clear().apply()
        changes.value++
    }

    @Synchronized fun clear(profileIds: List<Long>) {
        preferences.edit().apply { profileIds.forEach { remove(it.toString()) } }.apply()
        changes.value++
    }
}
