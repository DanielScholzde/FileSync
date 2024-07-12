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
import de.danielscholz.fileSync.common.formatAsFileSize


object UI {
    class Dir {
        var currentReadDir by mutableStateOf<String?>(null)
        var freeSpaceDir by mutableStateOf<Long?>(null)
        var spaceNeededDir by mutableStateOf<Long?>(null)
    }

    val sourceDir = Dir()
    val targetDir = Dir()

    var currentOperations by mutableStateOf<List<String>>(listOf())
    var syncFinished by mutableStateOf(false)
    var failures by mutableStateOf(listOf<String>())

    fun addCurrentOperation(operation: String) {
        currentOperations = (currentOperations + operation).takeLast(10)
    }
}


fun startUiBlocking() = application(false) {
    Window(
        onCloseRequest = { if (UI.syncFinished) exitApplication() },
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
                if (UI.syncFinished) {
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

            if (UI.sourceDir.currentReadDir != null || UI.targetDir.currentReadDir != null) {
                Text("Reading:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                UI.sourceDir.currentReadDir?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
                UI.targetDir.currentReadDir?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
            }

            UI.sourceDir.freeSpaceDir?.let {
                Text("Free Space (source): " + it.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
            }
            UI.targetDir.freeSpaceDir?.let {
                Text("Free Space (target): " + it.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
            }

            if (UI.currentOperations.isNotEmpty()) {
                Text("Sync:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                UI.currentOperations.forEach { Text(it, Modifier.padding(all = 3.dp), fontSize = fontSize) }
            }

            if (UI.failures.isNotEmpty()) {
                Spacer(Modifier.height(15.dp))

                Text("Fehler:", fontSize = fontSize, fontWeight = Bold)
                UI.failures.forEach {
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