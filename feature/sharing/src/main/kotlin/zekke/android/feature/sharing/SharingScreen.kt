package zekke.android.feature.sharing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SegmentedControl

enum class SharingTab { Shared, Connections }

@Composable
fun SharingScreen(
    shared: SharedState,
    sharedModel: SharedViewModel,
    connections: ConnectionsState,
    connectionsModel: ConnectionsViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(SharingTab.Shared) }
    val shown = if (shared.organises) tab else SharingTab.Shared
    Column(modifier.fillMaxSize()) {
        if (shared.organises) {
            SegmentedControl(
                segments = listOf(
                    Segment(SharingTab.Shared, stringResource(R.string.sharing_tab_shared)),
                    Segment(SharingTab.Connections, stringResource(R.string.sharing_tab_connections)),
                ),
                selected = tab,
                onSelect = { tab = it },
                label = stringResource(R.string.sharing_tab_label),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (shown) {
                SharingTab.Shared -> SharedScreen(shared, sharedModel)
                SharingTab.Connections -> ConnectionsScreen(connections, connectionsModel)
            }
        }
    }
}
