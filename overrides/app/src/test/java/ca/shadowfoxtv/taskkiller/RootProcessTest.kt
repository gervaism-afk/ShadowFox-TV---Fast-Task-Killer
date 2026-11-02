package ca.shadowfoxtv.taskkiller

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootProcessTest {
    private fun fixture(mode: String): List<String> = listOf(
        File(System.getProperty("java.home"), "bin/java").absolutePath,
        "-cp", File(ShellFixture::class.java.protectionDomain.codeSource.location.toURI()).path,
        ShellFixture::class.java.name, mode
    )

    @Test fun drainsOutputLargerThanTheProcessPipe() {
        val result = RootProcess.run(fixture("large"), null, 10)
        assertTrue(result.output.takeLast(100), result.success)
        assertEquals(262144, result.output.length)
    }

    @Test fun hungProcessTimesOut() {
        val start = System.nanoTime()
        val result = RootProcess.run(fixture("hang"), null, 1)
        assertFalse(result.success)
        assertEquals("timeout", result.output)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000)
    }

    @Test fun blockedStdinAlsoTimesOut() {
        val start = System.nanoTime()
        val result = RootProcess.run(fixture("hang"), "x".repeat(262144), 1)
        assertFalse(result.success)
        assertEquals("timeout", result.output)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000)
    }

    @Test fun retainsFailedCommandOutput() {
        val result = RootProcess.run(fixture("fail"), null, 10)
        assertFalse(result.success)
        assertEquals("denied", result.output)
    }

    @Test fun suppliesInputAndClosesStdin() {
        val result = RootProcess.run(fixture("input"), "id\nexit\n", 10)
        assertTrue(result.success)
        assertEquals("id\nexit\n", result.output)
    }
}
