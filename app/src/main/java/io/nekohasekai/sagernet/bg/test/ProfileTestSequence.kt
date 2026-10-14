package io.nekohasekai.sagernet.bg.test

import io.nekohasekai.sagernet.bg.test.ProfileTestRunner.Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Measurements share a fresh availability check; failed URL checks stop all later requests. */
internal interface ProfileProbeMeasurements {
    fun urlTest(): Int
    fun country(): Pair<String, String>
    fun download(): Double
    fun upload(): Double
}

internal suspend fun runProfileMeasurements(
    probe: ProfileProbeMeasurements,
    speed: Boolean,
    onPhase: suspend (Phase) -> Unit,
    onAvailable: suspend (Int) -> Unit,
    onCountry: suspend (Pair<String, String>) -> Unit,
    onDownload: suspend (Double) -> Unit,
    onUpload: suspend (Double) -> Unit,
    onError: suspend (Phase, Exception) -> Unit,
) {
    suspend fun <T> measure(phase: Phase, block: () -> T): T? {
        currentCoroutineContext().ensureActive()
        onPhase(phase)
        return try {
            block().also { currentCoroutineContext().ensureActive() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            onError(phase, e)
            null
        }
    }

    val ping = measure(Phase.URL) { probe.urlTest() } ?: return
    onAvailable(ping)
    measure(Phase.COUNTRY) { probe.country() }?.let { onCountry(it) }
    if (speed) {
        measure(Phase.DOWNLOAD) { probe.download() }?.let { onDownload(it) }
        measure(Phase.UPLOAD) { probe.upload() }?.let { onUpload(it) }
    }
}
