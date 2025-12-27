package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class MyTcpServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private val serverPort = 12345
    private var isRunning = false
    private val TAG = "MyTcpServerService"

    // Lista thread-safe pentru a stoca toate PrintWriter-ele clienților conectați
    private val connectedClients = CopyOnWriteArrayList<PrintWriter>()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(serverPort)
                Log.d(TAG, "Server started on port $serverPort")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.let { socket ->
                        Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in server thread", e)
            } finally {
                serverSocket?.close()
                Log.d(TAG, "Server stopped.")
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        thread {
            val clientAddress = clientSocket.inetAddress.hostAddress
            var output: PrintWriter? = null
            try {
                val input = BufferedReader(InputStreamReader(clientSocket.inputStream))
                output = PrintWriter(clientSocket.outputStream, true)

                // Adaugă clientul la lista de clienți conectați
                connectedClients.add(output)
                broadcastMessage("S-a conectat: $clientAddress")
                Log.d(TAG, "Client added: $clientAddress. Total clients: ${connectedClients.size}")

                var message: String?
                while (clientSocket.isClosed == false && clientSocket.isConnected == true && input.readLine().also { message = it } != null) {
                    message?.let { clientMessage ->
                        Log.d(TAG, "Received from $clientAddress: $clientMessage")
                        val fullMessage = "[$clientAddress]: $clientMessage"
                        broadcastMessage(fullMessage, output)

                        if ("exit".equals(clientMessage, ignoreCase = true)) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in client handling thread for $clientAddress", e)
            } finally {
                // Elimină clientul din lista de clienți conectați
                output?.let { connectedClients.remove(it) }
                broadcastMessage("S-a deconectat: $clientAddress")
                Log.d(TAG, "Client disconnected: $clientAddress. Remaining clients: ${connectedClients.size}")
                try {
                    clientSocket.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing client socket for $clientAddress", e)
                }
            }
        }
    }

    // Funcție pentru a trimite un mesaj tuturor clienților conectați
    private fun broadcastMessage(message: String, senderOutput: PrintWriter? = null) {
        Log.d(TAG, "Broadcasting: $message")
        for (clientOutput in connectedClients) {
            // Nu trimite mesajul înapoi expeditorului, dacă este specificat
            if (clientOutput != senderOutput) {
                try {
                    clientOutput.println(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message to a client during broadcast", e)
                    // Opțional: Poți elimina clientOutput de aici dacă dorești să gestionezi deconectările imediat
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        Log.d(TAG, "Service destroyed, server socket closed.")
        connectedClients.clear() // Curăță lista de clienți la oprirea serviciului
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
