package de.danielscholz.fileSync.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import de.danielscholz.fileSync.common.Global
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch


interface Store<T> {
    fun send(state: T)
    val stateFlow: StateFlow<T>
    val state get() = stateFlow.value
}

fun <T> CoroutineScope.createStore(state: T): Store<T> {
    val mutableStateFlow = MutableStateFlow(state)
    val channel: Channel<T> = Channel(Channel.UNLIMITED)

    return object : Store<T> {
        init {
            launch {
                channel.consumeAsFlow().collect { action ->
                    println("receive: $action")
                    mutableStateFlow.value = action
                }
            }
        }

        override fun send(state: T) {
            launch {
                println("Sending $state")
                channel.send(state)
            }
        }

        override val stateFlow: StateFlow<T> = mutableStateFlow
    }
}

private val coroutineScope = CoroutineScope(SupervisorJob())
val finishedStore = coroutineScope.createStore(false)
val failuresStore = coroutineScope.createStore(emptyList<String>())

//var uiFinished by mutableStateOf(false)
//var uiFailures by mutableStateOf(listOf<String>())


fun startUI() = application(false) {
    val finished by finishedStore.stateFlow.collectAsState()
    val failures by failuresStore.stateFlow.collectAsState()
    Window(
        onCloseRequest = { if (finished) exitApplication() },
        title = "Status",
        state = WindowState(size = DpSize(800.dp, 800.dp)),
    ) {
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
                Column {
                    Text("Fehler:")
                    failures.forEach {
                        Text(it)
                    }
                    Spacer(modifier = Modifier.height(5.dp))

                    if (finished) {
                        Button(onClick = ::exitApplication) {
                            Text("OK")
                        }
                    } else {
                        Button(onClick = { Global.cancel = true; exitApplication() }) {
                            Text("Abbrechen")
                        }
                    }
                }
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
}

//@Composable
//fun TextBox(text: String = "Item") {
//    Box(
//        modifier = Modifier.height(32.dp)
//            .width(400.dp)
//            .background(color = Color(200, 0, 0, 20))
//            .padding(start = 10.dp),
//        contentAlignment = Alignment.CenterStart
//    ) {
//        Text(text = text)
//    }
//}