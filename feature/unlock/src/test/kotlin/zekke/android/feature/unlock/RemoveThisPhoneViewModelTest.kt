package zekke.android.feature.unlock

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoveThisPhoneViewModelTest {
    private val services = FakeThisPhoneServices()
    private lateinit var viewModel: RemoveThisPhoneViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = RemoveThisPhoneViewModel(services)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.state.value

    @Test
    fun requestingShowsTheConfirmationAndStayHidesIt() {
        viewModel.requestRemoval()
        assertTrue(state.confirming)
        viewModel.stay()
        assertFalse(state.confirming)
    }

    @Test
    fun confirmingWithoutRequestingDoesNothing() {
        viewModel.confirmRemoval()
        assertEquals(0, services.calls)
        assertFalse(state.busy)
    }

    @Test
    fun confirmingCallsTheServiceOnceAndIsBusyMeanwhile() {
        val pending = CompletableDeferred<RemovalOutcome>()
        services.pending = pending
        viewModel.requestRemoval()
        viewModel.confirmRemoval()
        viewModel.confirmRemoval()
        assertEquals(1, services.calls)
        assertTrue(state.busy)
        viewModel.stay()
        viewModel.requestRemoval()
        assertTrue(state.confirming)
        pending.complete(RemovalOutcome.Removed)
        assertFalse(state.busy)
    }

    @Test
    fun aRemovalLeavesNoFailure() {
        for (outcome in listOf(RemovalOutcome.Removed, RemovalOutcome.AlreadyRemoved)) {
            services.nextOutcome = outcome
            viewModel.requestRemoval()
            viewModel.confirmRemoval()
            assertEquals(RemovePhoneState(), state)
        }
        assertEquals(2, services.calls)
    }

    @Test
    fun everyFailureIsShownAndTheConfirmationCloses() {
        val failures = listOf(
            RemovalOutcome.Offline,
            RemovalOutcome.RateLimited(30),
            RemovalOutcome.Refused,
            RemovalOutcome.ChainProblem,
            RemovalOutcome.PhoneLocked,
            RemovalOutcome.Failed,
        )
        for (failure in failures) {
            services.nextOutcome = failure
            viewModel.requestRemoval()
            viewModel.confirmRemoval()
            assertEquals(failure, state.failure)
            assertFalse(state.confirming)
            assertFalse(state.busy)
            viewModel.dismissFailure()
        }
    }

    @Test
    fun anUnexpectedExceptionIsFailed() {
        val pending = CompletableDeferred<RemovalOutcome>()
        services.pending = pending
        viewModel.requestRemoval()
        viewModel.confirmRemoval()
        pending.completeExceptionally(IllegalStateException("boom"))
        assertEquals(RemovalOutcome.Failed, state.failure)
        assertFalse(state.busy)
    }

    @Test
    fun aThrownExceptionIsFailed() {
        services.error = IllegalStateException("boom")
        viewModel.requestRemoval()
        viewModel.confirmRemoval()
        assertEquals(RemovalOutcome.Failed, state.failure)
    }

    @Test
    fun dismissingTheFailureClearsIt() {
        services.nextOutcome = RemovalOutcome.Offline
        viewModel.requestRemoval()
        viewModel.confirmRemoval()
        viewModel.dismissFailure()
        assertNull(state.failure)
    }

    @Test
    fun requestingAgainClearsTheFailure() {
        services.nextOutcome = RemovalOutcome.Offline
        viewModel.requestRemoval()
        viewModel.confirmRemoval()
        viewModel.requestRemoval()
        assertNull(state.failure)
        assertTrue(state.confirming)
    }

    @Test
    fun aLateResultAfterTheViewModelEndsChangesNothing() {
        val pending = CompletableDeferred<RemovalOutcome>()
        services.pending = pending
        val store = ViewModelStore()
        val owned = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { RemoveThisPhoneViewModel(services) } },
        )[RemoveThisPhoneViewModel::class.java]
        owned.requestRemoval()
        owned.confirmRemoval()
        store.clear()
        pending.complete(RemovalOutcome.Offline)
        assertNull(owned.state.value.failure)
        assertTrue(owned.state.value.busy)
    }
}
