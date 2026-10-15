package zekke.android.feature.settings

import kotlinx.coroutines.CompletableDeferred

class FakeExtensionLinkServices(
    override var canLink: Boolean = true,
    override var canNameExtension: Boolean = true,
) : ExtensionLinkServices {
    var opened: PairingOpen = PairingOpen.Opened("pairing-1", "ABCD-2345", 300_000)
    var polls = mutableListOf<String>()
    var pollResults = ArrayDeque<PairingPoll>()
    var linked = mutableListOf<Pair<String, String>>()
    var cancelled = mutableListOf<String>()
    var linkResult: PairingWrite = PairingWrite.Done
    var cancelResult: PairingWrite = PairingWrite.Done
    var opens = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun open(): PairingOpen {
        opens++
        return opened
    }

    override suspend fun poll(id: String): PairingPoll {
        polls += id
        return pollResults.removeFirstOrNull() ?: PairingPoll.Waiting
    }

    override suspend fun link(id: String, name: String): PairingWrite {
        linked += id to name
        gate?.await()
        return linkResult
    }

    override suspend fun cancel(id: String): PairingWrite {
        cancelled += id
        return cancelResult
    }
}

