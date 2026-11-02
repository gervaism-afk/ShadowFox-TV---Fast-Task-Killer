package ca.shadowfoxtv.taskkiller

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RootAccessPolicyTest {
    @Test fun onlyGoogleTvStreamerBypassesRoot() {
        assertTrue(isGoogleTvStreamer("Google", "Google TV Streamer"))
        assertTrue(isGoogleTvStreamer("google", "Google TV Streamer (4K)"))
        listOf(
            "Google" to "Chromecast",
            "Google" to "Pixel 9",
            "Google" to "Pixel Tablet",
            "Amazon" to "AFTMM",
            "NVIDIA" to "SHIELD Android TV",
            "H96" to "H96 Max",
            "Other" to "TV Streamer",
            "" to ""
        ).forEach { (manufacturer, model) ->
            assertFalse("$manufacturer $model", isGoogleTvStreamer(manufacturer, model))
        }
    }

    @Test fun cachesBothGrantedAndDeniedRootForTheSession() {
        listOf(true, false).forEach { granted ->
            var probes = 0
            val cache = RootAvailabilityCache { probes++; granted }
            repeat(5) { assertEquals(granted, cache.isAvailable()) }
            assertEquals(1, probes)
        }
    }

    @Test fun concurrentCallersShareOneProbe() {
        val probes = AtomicInteger()
        val cache = RootAvailabilityCache { probes.incrementAndGet(); Thread.sleep(30); false }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = (1..8).map { pool.submit<Boolean> { start.await(); cache.isAvailable() } }
            start.countDown()
            results.forEach { assertFalse(it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, probes.get())
        } finally { pool.shutdownNow() }
    }
}
