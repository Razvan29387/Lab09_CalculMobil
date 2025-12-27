package com.example.myapplication;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyTcpServerService extends Service {

    private static final String TAG = "MyTcpServerService";
    private ServerSocket serverSocket;
    private final int serverPort = 12345;
    private volatile boolean isRunning = false;

    // Lista thread-safe pentru a stoca toate PrintWriter-ele clienților conectați
    private CopyOnWriteArrayList<PrintWriter> connectedClients = new CopyOnWriteArrayList<>();
    private ExecutorService clientHandlingExecutor = Executors.newCachedThreadPool();

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(serverPort);
                Log.d(TAG, "Server started on port " + serverPort);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    if (clientSocket != null) {
                        Log.d(TAG, "Client connected: " + clientSocket.getInetAddress().getHostAddress());
                        clientHandlingExecutor.submit(() -> handleClient(clientSocket));
                    }
                }
            } catch (IOException e) {
                if (isRunning) { // Only log error if not gracefully shutting down
                    Log.e(TAG, "Error in server thread", e);
                }
            } finally {
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error closing server socket", e);
                }
                Log.d(TAG, "Server stopped.");
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        PrintWriter output = null;
        try {
            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);

            // Adaugă clientul la lista de clienți conectați
            connectedClients.add(output);
            broadcastMessage("S-a conectat: " + clientAddress);
            Log.d(TAG, "Client added: " + clientAddress + ". Total clients: " + connectedClients.size());

            String clientMessage;
            while (clientSocket.isClosed() == false && clientSocket.isConnected() == true && (clientMessage = input.readLine()) != null) {
                Log.d(TAG, "Received from " + clientAddress + ": " + clientMessage);
                String fullMessage = "[" + clientAddress + "]: " + clientMessage;
                broadcastMessage(fullMessage, output);

                if ("exit".equalsIgnoreCase(clientMessage)) {
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error in client handling for " + clientAddress, e);
        } finally {
            // Elimină clientul din lista de clienți conectați
            if (output != null) {
                connectedClients.remove(output);
            }
            broadcastMessage("S-a deconectat: " + clientAddress);
            Log.d(TAG, "Client disconnected: " + clientAddress + ". Remaining clients: " + connectedClients.size());
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket for " + clientAddress, e);
            }
        }
    }

    // Funcție pentru a trimite un mesaj tuturor clienților conectați
    private void broadcastMessage(String message) {
        broadcastMessage(message, null);
    }

    private void broadcastMessage(String message, PrintWriter senderOutput) {
        Log.d(TAG, "Broadcasting: " + message);
        for (PrintWriter clientOutput : connectedClients) {
            // Nu trimite mesajul înapoi expeditorului, dacă este specificat
            if (clientOutput != senderOutput) {
                try {
                    clientOutput.println(message);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message to a client during broadcast", e);
                    // Opțional: Poți elimina clientOutput de aici dacă dorești să gestionezi deconectările imediat
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
        clientHandlingExecutor.shutdownNow(); // Attempt to stop all client handling threads
        Log.d(TAG, "Service destroyed, server socket closed.");
        connectedClients.clear(); // Curăță lista de clienți la oprirea serviciului
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
