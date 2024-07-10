package de.danielscholz.fileSync.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import de.danielscholz.fileSync.common.Global


var uiReadDir1 by mutableStateOf<String?>(null)
var uiReadDir2 by mutableStateOf<String?>(null)
var uiFinished by mutableStateOf(false)
var uiFailures by mutableStateOf(listOf<String>())


fun startUiBlocking() = application(false) {
    Window(
        onCloseRequest = { if (uiFinished) exitApplication() },
        title = "Status",
        state = WindowState(size = DpSize(800.dp, 800.dp)),
    ) {
        frame(::exitApplication)
    }
}

@Composable
@Preview
fun frame(exitApplication: () -> Unit) {
    scrollableContent {
        Column {
            uiReadDir1?.let { Text(it, Modifier.padding(all = 5.dp)) }
            uiReadDir2?.let { Text(it, Modifier.padding(all = 5.dp)) }

            if (uiFailures.isNotEmpty()) {
                Text("Fehler:")
                uiFailures.forEach {
                    Text(it, modifier = Modifier.padding(all = 5.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiFinished) {
                Button(onClick = exitApplication) {
                    Text("OK")
                }
            } else {
                Button(onClick = { Global.cancel = true; exitApplication() }) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

@Composable
fun scrollableContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(10.dp)
    ) {
        val stateVertical = rememberScrollState(0)
        val stateHorizontal = rememberScrollState(0)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(stateVertical)
                .horizontalScroll(stateHorizontal)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            content()
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(stateVertical)
        )
        HorizontalScrollbar(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
            adapter = rememberScrollbarAdapter(stateHorizontal)
        )
    }
}