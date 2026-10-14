package io.nekohasekai.sagernet.bg.test

import io.nekohasekai.sagernet.bg.test.ProfileTestRunner.Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ProfileTestSequenceTest {
    private class Probe(var urlFails: Boolean = false, val countryFails: Boolean = false) : ProfileProbeMeasurements {
        val calls = ArrayList<Phase>()
        override fun urlTest(): Int { calls += Phase.URL; if (urlFails) throw IOException("offline"); return 42 }
        override fun country(): Pair<String, String> { calls += Phase.COUNTRY; if (countryFails) throw IOException("lookup failed"); return "1.2.3.4" to "DE" }
        override fun download(): Double { calls += Phase.DOWNLOAD; return 1000000.0 }
        override fun upload(): Double { calls += Phase.UPLOAD; return 500000.0 }
    }

    @Test fun failedURLMakesNoCountryOrTransferRequests() = runBlocking {
        for (speed in listOf(false, true)) {
            val probe = Probe(urlFails = true)
            val committed = ArrayList<Any>()
            val errors = ArrayList<Phase>()
            runProfileMeasurements(probe, speed, {}, { committed += it }, { committed += it },
                { committed += it }, { committed += it }, { phase, _ -> errors += phase })
            assertEquals(listOf(Phase.URL), probe.calls)
            assertEquals(listOf(Phase.URL), errors)
            assertTrue("Failed availability must not commit successful metadata", committed.isEmpty())
        }
    }

    @Test fun recoveringServerCanBeCheckedAgain() = runBlocking {
        val probe = Probe(urlFails = true)
        val countries = ArrayList<String>()
        repeat(2) {
            runProfileMeasurements(probe, false, {}, {}, { countries += it.second }, {}, {}, { _, _ -> })
            probe.urlFails = false
        }
        assertEquals(listOf(Phase.URL, Phase.URL, Phase.COUNTRY), probe.calls)
        assertEquals(listOf("DE"), countries)
    }

    @Test fun speedTestRechecksAvailabilityBeforeHeavyRequests() = runBlocking {
        val probe = Probe()
        val commits = ArrayList<Phase>()
        runProfileMeasurements(probe, true, {}, { commits += Phase.URL }, { commits += Phase.COUNTRY },
            { commits += Phase.DOWNLOAD }, { commits += Phase.UPLOAD }, { _, _ -> fail("unexpected error") })
        val expected = listOf(Phase.URL, Phase.COUNTRY, Phase.DOWNLOAD, Phase.UPLOAD)
        assertEquals(expected, probe.calls)
        assertEquals(expected, commits)
    }

    @Test fun countryFailureDoesNotReclassifySuccessfulURL() = runBlocking {
        val probe = Probe(countryFails = true)
        val latency = ArrayList<Int>()
        val errors = ArrayList<Phase>()
        runProfileMeasurements(probe, false, {}, { latency += it }, { fail("failed country committed") },
            {}, {}, { phase, _ -> errors += phase })
        assertEquals(listOf(42), latency)
        assertEquals(listOf(Phase.COUNTRY), errors)
    }

    @Test fun cancellationBeforeCountryStopsRemainingRequests() = runBlocking {
        val probe = Probe()
        try {
            runProfileMeasurements(probe, true, {}, { throw CancellationException("cancel") }, {}, {}, {},
                { _, _ -> fail("Cancellation must not become a server error") })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf(Phase.URL), probe.calls)
        }
    }

    @Test fun lateCancelledWorkerCannotClearNewCardState() {
        val id = 123L
        val oldRun = ProfileTestState.nextRunId()
        val newRun = ProfileTestState.nextRunId()
        try {
            ProfileTestState.begin(id, oldRun)
            ProfileTestState.begin(id, newRun)
            ProfileTestState.phase(id, newRun, Phase.DOWNLOAD)
            ProfileTestState.finish(id, oldRun, Phase.COUNTRY)
            assertEquals(ProfileTestState.State(newRun, Phase.DOWNLOAD), ProfileTestState.get(id))
        } finally { ProfileTestState.finish(id, newRun) }
    }

    @Test fun clearDoesNotAllowLateProgressToReappear() {
        val id = 124L
        val run = ProfileTestState.nextRunId()
        ProfileTestState.begin(id, run)
        ProfileTestState.clear()
        ProfileTestState.phase(id, run, Phase.COUNTRY)
        ProfileTestState.finish(id, run, Phase.COUNTRY)
        assertNull(ProfileTestState.get(id))
    }
}
