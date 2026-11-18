package ca.shadowfoxtv.taskkiller

import org.junit.Assert.*
import org.junit.Test

class ProcessSnapshotTest {
    @Test fun wideProcessNamesMatchMainAndRemoteProcessesExactly() {
        val snapshot = RootProcessSnapshot.fromPs("NAME\ninit\nexample.player:remote\nexample.player2\n")!!
        assertTrue(snapshot.isRunning("example.player"))
        assertTrue(snapshot.isRunning("example.player2"))
        assertFalse(snapshot.isRunning("example.play"))
        assertFalse(snapshot.isRunning("example.missing"))
    }

    @Test fun activityRegistryIgnoresPackagesMentionedOutsideLiveProcessRecords() {
        val snapshot = RootProcessSnapshot.fromActivity(
            "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n" +
                "*APP* ProcessRecord{abcd 123:example.player:remote/u0a123}\n" +
                "mPackageList={example.missing}\n" +
                "ProcessRecord{abcd 0:example.starting/u0a124}\n"
        )!!
        assertTrue(snapshot.isRunning("example.player"))
        assertFalse(snapshot.isRunning("example.missing"))
        assertFalse(snapshot.isRunning("example.starting"))
    }

    @Test fun malformedAndEmptyReportsCannotVerifyAnAppStop() {
        assertNull(RootProcessSnapshot.fromPs("permission denied"))
        assertNull(RootProcessSnapshot.fromPs("NAME\n"))
        assertNull(RootProcessSnapshot.fromActivity("timeout"))
        assertNull(RootProcessSnapshot.fromActivity("ACTIVITY MANAGER RUNNING PROCESSES\n"))
    }
}
