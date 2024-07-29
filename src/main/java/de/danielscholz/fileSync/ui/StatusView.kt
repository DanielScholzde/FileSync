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
import de.danielscholz.fileSync.actions.sync.ChangesWithDetails
import de.danielscholz.fileSync.common.Global
import de.danielscholz.fileSync.common.fileSize
import de.danielscholz.fileSync.common.formatAsFileSize
import de.danielscholz.fileSync.common.supply


private val isWindows = supply {
    val os = System.getProperty("os.name", "generic").lowercase()
    when {
        "mac" in os || "darwin" in os -> false
        "win" in os -> true
        "nux" in os -> false
        else -> false
    }
}

private val fontSize = if (isWindows) 15.sp else 25.sp


object UI {
    class Dir {
        var currentReadDir by mutableStateOf<String?>(null)
        var changes by mutableStateOf<ChangesWithDetails?>(null)
        var filesHashCalculatedCount by mutableStateOf(0)
        var filesHashCalculatedSize by mutableStateOf(0L)
    }

    val sourceDir = Dir()
    val targetDir = Dir()

    var totalBytesToCopy by mutableStateOf(0L)
    var currentBytesCopied by mutableStateOf(0L)
    var currentOperations by mutableStateOf<List<String>>(listOf())
        private set
    var syncFinished by mutableStateOf(false)
    var failures by mutableStateOf(listOf<String>())
    var warnings by mutableStateOf(listOf<String>())
    var conflicts by mutableStateOf(listOf<Triple<String, (() -> Unit)?, (() -> Unit)?>>())

    fun addCurrentOperation(operation: String) {
        currentOperations = (currentOperations + operation).takeLast(10)
    }

    fun clearCurrentOperations() {
        currentOperations = listOf()
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
    Column {
        Box(modifier = Modifier.padding(15.dp)) {
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
        scrollableContent {
            Column {
                if (UI.sourceDir.currentReadDir != null || UI.targetDir.currentReadDir != null) {
                    Text("Reading:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                    UI.sourceDir.currentReadDir?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
                    UI.targetDir.currentReadDir?.let { Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize) }
                }

                if (UI.sourceDir.filesHashCalculatedSize > 0L) {
                    Text("New indexed (source): " + UI.sourceDir.filesHashCalculatedSize.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                }
                if (UI.targetDir.filesHashCalculatedSize > 0L) {
                    Text("New indexed (target): " + UI.targetDir.filesHashCalculatedSize.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                }

                @Composable
                fun pp(dir: UI.Dir, dirStr: String) {
                    dir.changes?.apply {
                        Text("$dirStr:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                        if (filesToAdd.isNotEmpty()) {
                            Text("Files to add: " + filesToAdd.size + " (${filesToAdd.fileSize().formatAsFileSize()})", Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (contentChanged.isNotEmpty()) {
                            Text(
                                "Files to update content: " + contentChanged.size + " (${contentChanged.fileSize().formatAsFileSize()})",
                                Modifier.padding(vertical = 5.dp),
                                fontSize = fontSize
                            )
                        }
                        if (modifiedChanged.isNotEmpty()) {
                            Text("Files to update modified: " + modifiedChanged.size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (renamed().isNotEmpty()) {
                            Text("Files to rename: " + renamed().size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (moved().isNotEmpty()) {
                            Text("Files to move: " + moved().size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (movedAndRenamed().isNotEmpty()) {
                            Text("Files to rename + move: " + movedAndRenamed().size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (movedAndContentChanged.isNotEmpty()) {
                            Text("Files to move + update content: " + movedAndContentChanged.size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (filesToDelete.isNotEmpty()) {
                            Text("Files to delete: " + filesToDelete.size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (foldersToAdd.isNotEmpty()) {
                            Text("Folders to create: " + foldersToAdd.size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                        if (foldersToDelete.isNotEmpty()) {
                            Text("Folders to delete: " + foldersToDelete.size, Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }

                        if (diskspaceNeeded > 0) {
                            Text("Diskspace needed: " + diskspaceNeeded.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                            Text("Diskspace available: " + usableSpace.formatAsFileSize(), Modifier.padding(vertical = 5.dp), fontSize = fontSize)
                        }
                    }
                }

                pp(UI.sourceDir, "SourceDir")
                pp(UI.targetDir, "TargetDir")

                if (UI.conflicts.isNotEmpty()) {
                    Spacer(Modifier.height(15.dp))

                    Text("Conflicts:", fontSize = fontSize, fontWeight = Bold)
                    UI.conflicts.forEach {
                        Text(it.first, Modifier.padding(all = 5.dp), fontSize = fontSize, color = Color.Red)

                        @Composable
                        fun btn(s: String, action: () -> Unit) {
                            Button(onClick = {
                                try {
                                    action()
                                    UI.conflicts -= it
                                } catch (e: Exception) {
                                    println(e.message)
                                }
                            }) {
                                Text("Resolve conflict: $s wins", fontSize = fontSize)
                            }
                        }
                        it.second?.let { btn("source", it) }
                        it.third?.let { btn("target", it) }
                    }
                }

                if (UI.currentOperations.isNotEmpty()) {
                    Text("Sync:", Modifier.padding(vertical = 5.dp), fontSize = fontSize, fontWeight = Bold)
                    if (UI.totalBytesToCopy > 0) {
                        Text(
                            "Progress: ${UI.currentBytesCopied * 100 / UI.totalBytesToCopy}% (transferred: ${UI.currentBytesCopied.formatAsFileSize()})",
                            Modifier.padding(vertical = 5.dp),
                            fontSize = fontSize
                        )
                    }
                    UI.currentOperations.forEach { Text(it, Modifier.padding(all = 3.dp), fontSize = fontSize) }
                }

                if (UI.failures.isNotEmpty()) {
                    Spacer(Modifier.height(15.dp))

                    Text("Failures:", fontSize = fontSize, fontWeight = Bold)
                    UI.failures.forEach {
                        Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize, color = Color.Red)
                    }
                }

                if (UI.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(15.dp))

                    Text("Warnings:", fontSize = fontSize, fontWeight = Bold)
                    UI.warnings.forEach {
                        Text(it, Modifier.padding(all = 5.dp), fontSize = fontSize, color = Color(255, 103, 0))
                    }
                }
            }
        }
    }
}

@Composable
fun scrollableContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(15.dp)
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