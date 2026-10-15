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

    @Test
    fun aSendRefusedForTrustOrForAMissingSubKeyIsTyped() {
        assertEquals(
            zekke.android.feature.sharing.SendOutcome.NotTrusted(TrustAlarm.AccountChanged),
            sendOutcomeOf(ConnectionNotTrustedException(ConnectionTrust.AccountChanged)),
        )
        assertEquals(
            zekke.android.feature.sharing.SendOutcome.NotTrusted(null),
            sendOutcomeOf(ConnectionNotTrustedException(ConnectionTrust.Unpinned("f"))),
        )
        assertEquals(
            zekke.android.feature.sharing.SendOutcome.NotShared,
            sendOutcomeOf(zekke.core.sharing.ScopeNotSharedException(zekke.core.scopes.Scope.NOTES)),
        )
        assertNull(sendOutcomeOf(IllegalStateException()))
    }

    @Test
    fun aChainThatDoesNotVerifyIsItsOwnFailureNotAGenericOne() {
        assertEquals(
            zekke.android.ui.components.ItemFailure.ChainUnverified,
            plainFailureOf(zekke.core.account.ChainNotVerifiedException(IllegalStateException())),
        )
        assertEquals(zekke.android.ui.components.ItemFailure.Offline, plainFailureOf(zekke.core.api.NetworkError("/x")))
        assertNull(plainFailureOf(IllegalStateException()))
    }

    @Test
    fun everyDeviceRemovalFailureIsTyped() {
        assertEquals(zekke.android.feature.settings.DeviceRemoval.PhraseMismatch, removalOutcomeOf(zekke.core.account.PhraseMismatchException()))
        assertEquals(zekke.android.feature.settings.DeviceRemoval.Offline, removalOutcomeOf(zekke.core.api.NetworkError("/x")))
        assertEquals(
            zekke.android.feature.settings.DeviceRemoval.ChainProblem,
            removalOutcomeOf(zekke.core.account.ChainNotVerifiedException(IllegalStateException())),
        )
        assertEquals(zekke.android.feature.settings.DeviceRemoval.Failed, removalOutcomeOf(IllegalStateException()))
    }

    @Test
    fun everyDevicePinFailureIsTyped() {
        assertEquals(zekke.android.feature.settings.DevicePinChange.Offline, devicePinOutcomeOf(zekke.core.api.NetworkError("/x")))
        assertEquals(zekke.android.feature.settings.DevicePinChange.Failed, devicePinOutcomeOf(IllegalStateException()))
    }

    @Test
    fun theAccountsPlanBecomesWhatTheScreenShows() {
        val account = zekke.core.users.AccountRecord("a", "name", "u", false, 7, "2026-01-01T00:00:00Z", null)
        val free = planInfoOf(account)
        assertEquals("free", free.code)
        assertEquals(7, free.retentionDays)
    }
}
