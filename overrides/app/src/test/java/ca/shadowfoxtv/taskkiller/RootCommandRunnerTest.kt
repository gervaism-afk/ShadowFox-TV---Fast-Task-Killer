package ca.shadowfoxtv.taskkiller

import org.junit.Assert.*
import org.junit.Test

class RootCommandRunnerTest {
    @Test fun failedCommandsExecuteOnceWithoutRepeatingDiscovery() {
        val commands = mutableListOf<String>()
        val runner = RootCommandRunner(listOf("su", "/system/xbin/su"), { args, _, _ ->
            commands.add(args.last())
            if (args.last() == "id") RootProcess.Result(true, "uid=0(root)")
            else RootProcess.Result(false, "timeout")
        })
        assertFalse(runner.exec("cleanup", 1000).success)
        assertFalse(runner.exec("verify", 1000).success)
        assertEquals(listOf("id", "cleanup", "verify"), commands)
        assertTrue(runner.isAvailable())
    }

    @Test fun remembersLegacyArgumentModeForRealCommands() {
        val calls = mutableListOf<List<String>>()
        val runner = RootCommandRunner(listOf("su"), { args, _, _ ->
            calls.add(args)
            if (args.getOrNull(1) == "0") RootProcess.Result(true, "uid=0(root)")
            else RootProcess.Result(false, "unsupported arguments")
        })
        assertTrue(runner.exec("cleanup", 1000).success)
        assertEquals(listOf("su", "0", "sh", "-c", "cleanup"), calls.last())
        assertEquals(3, calls.size)
    }

    @Test fun interactiveShellClosesInputAndCachesItsMode() {
        val inputs = mutableListOf<String?>()
        val runner = RootCommandRunner(listOf("su"), { args, input, _ ->
            inputs.add(input)
            RootProcess.Result(args.size == 1, "uid=0(root)")
        })
        assertTrue(runner.exec("cleanup", 1000).success)
        assertEquals("id\nexit\n", inputs[3])
        assertEquals("cleanup\nexit\n", inputs.last())
        assertEquals(5, inputs.size)
    }

    @Test fun hungDiscoverySharesOneFiveSecondBudgetAndCachesDenial() {
        var now = 0L
        var attempts = 0
        val runner = RootCommandRunner(List(8) { "su-$it" }, { _, _, timeout ->
            now += timeout * 1_000_000
            attempts++
            RootProcess.Result(false, "timeout")
        }, { now })
        assertFalse(runner.isAvailable())
        assertFalse(runner.exec("cleanup", 1000).success)
        assertEquals(5_000_000_000L, now)
        assertEquals(5, attempts)
    }

    @Test fun successfulNonRootIdentityDoesNotAuthorizeCommands() {
        var cleanupRan = false
        val runner = RootCommandRunner(listOf("su"), { args, _, _ ->
            if (args.last() == "cleanup") cleanupRan = true
            RootProcess.Result(true, "uid=2000(shell)")
        })
        assertFalse(runner.exec("cleanup", 1000).success)
        assertFalse(cleanupRan)
    }
}
