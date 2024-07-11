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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import de.danielscholz.fileSync.common.Global


var uiReadDir1 by mutableStateOf<String?>(null)
var uiReadDir2 by mutableStateOf<String?>(null)
var uiCurrentOps by mutableStateOf<List<String>>(listOf())
var uiFinished by mutableStateOf(false)
var uiFailures by mutableStateOf(listOf<String>())

fun addCurrentOp(action: String) {
    uiCurrentOps = (uiCurrentOps + action).takeLast(10)
}


fun startUiBlocking() = application(false) {
    Window(
        onCloseRequest = { if (uiFinished) exitApplication() },
        title = "Status",
        state = WindowState(size = DpSize(2500.dp, 1000.dp)),
    ) {
        frame(::exitApplication)
    }
}

@Composable
@Preview
fun frame(exitApplication: () -> Unit) {
    val fontSize = 25.sp
    scrollableContent {
        Column {

            @Composable
            fun buttons() {
                if (uiFinished) {
                    Button(onClick = exitApplication) {
                        Text("OK", fontSize = fontSize)
                    }
                } else {
                    Button(onClick = { Global.cancel = true; exitApplication() }) {
                        Text("Abbrechen", fontSize = fontSize)
                    }
                }
            }

            buttons()

            Spacer(Modifier.height(15.dp))

            if (uiReadDir1 != null || uiReadDir2 != null) {
                Text("Reading:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                uiReadDir1?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
                uiReadDir2?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
            }

            if (uiCurrentOps.isNotEmpty()) {
                Text("Sync:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                uiCurrentOps.forEach { Text(it, Modifier.padding(all = 3.dp), fontSize = fontSize) }
            }

            if (uiFailures.isNotEmpty()) {
                Spacer(Modifier.height(15.dp))

                Text("Fehler:", fontSize = fontSize, fontWeight = Bold)
                uiFailures.forEach {
                    Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize, color = Color.Red)
                }
            }

            Spacer(Modifier.height(15.dp))

            buttons()
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