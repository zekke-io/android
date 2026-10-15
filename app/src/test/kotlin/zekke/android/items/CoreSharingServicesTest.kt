package zekke.android.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.feature.sharing.InviteOutcome
import zekke.android.feature.sharing.TrustAlarm
import zekke.core.sharing.AlreadyConnectedException
import zekke.core.sharing.ConnectionNotTrustedException
import zekke.core.sharing.ConnectionTrust
import zekke.core.sharing.UnknownRecipientException
import zekke.core.users.MalformedUsernameException

class CoreSharingServicesTest {
    @Test
    fun everyRefusedInvitationHasItsOwnOutcome() {
        assertEquals(InviteOutcome.UnknownUsername, inviteOutcomeOf(UnknownRecipientException("bob")))
        assertEquals(InviteOutcome.UnknownUsername, inviteOutcomeOf(MalformedUsernameException("!!")))
        assertEquals(InviteOutcome.AlreadyConnected, inviteOutcomeOf(AlreadyConnectedException("bob")))
        assertEquals(
            InviteOutcome.NotTrusted(TrustAlarm.RootChanged),
            inviteOutcomeOf(ConnectionNotTrustedException(ConnectionTrust.RootChanged("a", "b"))),
        )
        assertEquals(InviteOutcome.NotTrusted(null), inviteOutcomeOf(ConnectionNotTrustedException(ConnectionTrust.Unpinned("a"))))
    }

    @Test
    fun anythingElseIsLeftToTheGenericFailure() {
        assertNull(inviteOutcomeOf(IllegalStateException()))
    }

    @Test
    fun everyKeptChangingConflictIsOneConflict() {
        assertTrue(isKeptChanging(zekke.core.folders.FoldersKeptChangingException()))
        assertTrue(isKeptChanging(zekke.core.sharing.AddressBookKeptChangingException()))
        assertTrue(isKeptChanging(zekke.core.sharing.SharedFoldersKeptChangingException()))
        assertFalse(isKeptChanging(IllegalStateException()))
    }
}
