package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val TAG = "TcpClientTerminal"
    private var clientSocket: Socket? = null
    private var output: PrintWriter? = null
    private var input: BufferedReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pornim serviciul TCP server (acesta va asculta pe portul 12345 intern în emulator/dispozitiv)
        startService(Intent(this, MyTcpServerService::class.java))

        setContent {
            MyApplicationTheme {
                TcpTerminalScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromServer()
        // Oprim serviciul când activitatea este distrusă, dacă este cazul.
        // În multe cazuri, un serviciu ar trebui să ruleze independent.
        // Dacă vrei ca serviciul să se oprească automat cu activitatea, decomentează linia de mai jos:
        // stopService(Intent(this, MyTcpServerService::class.java))
    }

    private fun connectToServer(
        serverAddress: String,
        serverPort: Int,
        onMessageReceived: (String) -> Unit,
        onConnected: () -> Unit
    ) {
        if (clientSocket != null && clientSocket?.isConnected == true) {
            Log.d(TAG, "Already connected.")
            return
        }

        thread {
            try {
                clientSocket = Socket(serverAddress, serverPort)
                output = PrintWriter(clientSocket!!.getOutputStream(), true)
                input = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))

                Log.d(TAG, "Connected to server at $serverAddress:$serverPort")
                onConnected()

                var message: String? = null
                while (clientSocket?.isClosed == false && clientSocket?.isConnected == true && input?.readLine().also { message = it } != null) {
                    message?.let {
                        onMessageReceived("Server: $it")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting or communicating with server", e)
                onMessageReceived("Eroare: ${e.message}")
            } finally {
                disconnectFromServer()
            }
        }
    }

    private fun sendMessage(message: String) {
        thread {
            try {
                if (clientSocket?.isConnected == true && output != null) {
                    output?.println(message)
                    Log.d(TAG, "Sent to server: $message")
                } else {
                    Log.e(TAG, "Not connected to server.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
            }
        }
    }

    private fun disconnectFromServer() {
        try {
            clientSocket?.close()
            output?.close()
            input?.close()
            clientSocket = null
            output = null
            input = null
            Log.d(TAG, "Disconnected from server.")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from server", e)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TcpTerminalScreen() {
        val messages = remember { mutableStateListOf<String>() }
        var inputText by remember { mutableStateOf("") }
        // Clientul se va conecta la serverul local pe portul său direct
        val serverAddress = "127.0.0.1" 
        val serverPort = 12345 

        val coroutineScope = rememberCoroutineScope()

        // Conectează-te la server când ecranul este compus pentru prima dată
        LaunchedEffect(Unit) {
            coroutineScope.launch(Dispatchers.IO) {
                connectToServer(serverAddress, serverPort,
                    onMessageReceived = { msg ->
                        messages.add(msg)
                    },
                    onConnected = {
                        messages.add("Conectat la server local $serverAddress:$serverPort")
                    }
                )
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp)
            ) {
                // Afișarea mesajelor
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages) { message ->
                        Text(text = message)
                    }
                }

                // Câmpul de introducere și butonul de trimitere
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Mesaj") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages.add("Client: $inputText")
                                sendMessage(inputText)
                                if (inputText.equals("exit", ignoreCase = true)) {
                                    messages.add("Deconectat.")
                                    disconnectFromServer()
                                }
                                inputText = ""
                            }
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text("Trimite")
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun PreviewTcpTerminalScreen() {
        MyApplicationTheme {
            TcpTerminalScreen()
        }
    }
}
