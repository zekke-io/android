package zekke.android.feature.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.core.sharing.ConnectionDirection
import zekke.core.sharing.ConnectionRecord
import zekke.core.sharing.ConnectionStatus
import zekke.core.sharing.ConnectionTrust

class SharingPolicyTest {
    @Test
    fun everyTrustStateIsAnAlarmOrNone() {
        assertNull(alarmOf(ConnectionTrust.Trusted("f")))
        assertNull(alarmOf(ConnectionTrust.Unpinned("f")))
        assertEquals(TrustAlarm.RootChanged, alarmOf(ConnectionTrust.RootChanged("a", "b")))
        assertEquals(TrustAlarm.ProofInvalid, alarmOf(ConnectionTrust.ProofInvalid("f")))
        assertEquals(TrustAlarm.AccountChanged, alarmOf(ConnectionTrust.AccountChanged))
        assertEquals(TrustAlarm.Unresolvable, alarmOf(ConnectionTrust.Unresolvable))
    }

    @Test
    fun theThreeDangerStatesSayDoNotSendAndTheUncheckableOneOnlyWarns() {
        assertTrue(TrustAlarm.RootChanged.danger)
        assertTrue(TrustAlarm.ProofInvalid.danger)
        assertTrue(TrustAlarm.AccountChanged.danger)
        assertFalse(TrustAlarm.Unresolvable.danger)
    }

    private fun record(direction: String, status: String) =
        ConnectionRecord("id", direction, "bob", "addr", status)

    @Test
    fun aConnectionStandsWhereTheInvitationIs() {
        assertEquals(ConnectionStanding.Accepted, standingOf(record(ConnectionDirection.INBOUND, ConnectionStatus.ACCEPTED)))
        assertEquals(ConnectionStanding.Accepted, standingOf(record(ConnectionDirection.OUTBOUND, ConnectionStatus.ACCEPTED)))
        assertEquals(ConnectionStanding.WaitingForMe, standingOf(record(ConnectionDirection.INBOUND, ConnectionStatus.PENDING)))
        assertEquals(ConnectionStanding.WaitingForThem, standingOf(record(ConnectionDirection.OUTBOUND, ConnectionStatus.PENDING)))
    }

    @Test
    fun connectionsAreGroupedAndOnlyInvitationsAwaitingMeAreNotChecked() {
        val a = connection("1", "ann")
        val b = connection("2", "bob", ConnectionStanding.WaitingForMe)
        val c = connection("3", "cat", ConnectionStanding.WaitingForThem)
        val groups = groupConnections(listOf(a, b, c))
        assertEquals(listOf(a), groups.accepted)
        assertEquals(listOf(b), groups.awaitingMe)
        assertEquals(listOf(c), groups.awaitingThem)
        assertTrue(a.shouldBeChecked)
        assertFalse(b.shouldBeChecked)
        assertTrue(c.shouldBeChecked)
    }

    @Test
    fun aNicknameLabelsAConnectionAndTheUsernameStandsWithoutOne() {
        assertEquals("Ann", connection("1", "ann", nickname = "Ann").label)
        assertEquals("ann", connection("1", "ann").label)
    }

    @Test
    fun theirFingerprintIsKnownWheneverTheAccountStillResolves() {
        assertEquals("f", fingerprintOf(ConnectionTrust.Trusted("f")))
        assertEquals("f", fingerprintOf(ConnectionTrust.Unpinned("f")))
        assertEquals("a", fingerprintOf(ConnectionTrust.RootChanged("a", "b")))
        assertEquals("f", fingerprintOf(ConnectionTrust.ProofInvalid("f")))
        assertNull(fingerprintOf(ConnectionTrust.AccountChanged))
        assertNull(fingerprintOf(ConnectionTrust.Unresolvable))
    }
}
