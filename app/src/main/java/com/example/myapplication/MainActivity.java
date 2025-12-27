package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TcpClientTerminal";
    private TextView tvMessages;
    private EditText etMessage;
    private Button btnSend;
    private ScrollView scrollView;

    private Socket clientSocket;
    private PrintWriter output;
    private BufferedReader input;

    private final String serverAddress = "127.0.0.1";
    private final int serverPort = 12345;

    // Handler pentru a posta mesaje pe UI thread
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvMessages = findViewById(R.id.tvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        scrollView = findViewById(R.id.scrollView);

        // RE-ACTIVAT: Pornim serviciul TCP server
        startService(new Intent(this, MyTcpServerService.class));

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = etMessage.getText().toString();
                if (!message.isEmpty()) {
                    sendMessage(message);
                }
            }
        });

        // Ne conectăm la server într-un thread separat
        new Thread(this::connectToServer).start();
    }

    private void connectToServer() {
        try {
            // Adăugăm o mică pauză pentru a da timp serverului să pornească
            Thread.sleep(1000); // Așteaptă 1 secundă

            clientSocket = new Socket(serverAddress, serverPort);
            output = new PrintWriter(clientSocket.getOutputStream(), true);
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            runOnUiThread(() -> addMessageToTerminal("Conectat la server " + serverAddress + ":" + serverPort));

            // RE-ACTIVAT: Așteaptă mesaje de la server
            String serverMessage;
            while ((serverMessage = input.readLine()) != null) {
                final String finalMessage = serverMessage;
                runOnUiThread(() -> addMessageToTerminal(finalMessage));
            }

        } catch (IOException | InterruptedException e) { // Am adăugat InterruptedException
            Log.e(TAG, "Error connecting or communicating with server", e);
            runOnUiThread(() -> addMessageToTerminal("Eroare: " + e.getMessage()));
        } finally {
            disconnectFromServer();
        }
    }

    private void sendMessage(String message) {
        addMessageToTerminal("Client: " + message);
        etMessage.setText(""); // Clear the input field

        // Trimiterea mesajului se face pe un alt thread pentru a nu bloca UI-ul
        new Thread(() -> {
            try {
                if (output != null) {
                    output.println(message);
                    Log.d(TAG, "Sent to server: " + message);
                } else {
                     runOnUiThread(() -> addMessageToTerminal("Nu sunt conectat la server."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                runOnUiThread(() -> addMessageToTerminal("Eroare la trimitere: " + e.getMessage()));
            }

            if ("exit".equalsIgnoreCase(message)) {
                disconnectFromServer();
            }
        }).start();
    }

    private void disconnectFromServer() {
        try {
            if (clientSocket != null) {
                clientSocket.close();
                clientSocket = null;
            }
            if (output != null) {
                output.close();
                output = null;
            }
            if (input != null) {
                input.close();
                input = null;
            }
            Log.d(TAG, "Disconnected from server.");
            runOnUiThread(() -> addMessageToTerminal("Deconectat."));
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting from server", e);
        }
    }

    private void addMessageToTerminal(String message) {
        uiHandler.post(() -> {
            tvMessages.append(message + "\n");
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromServer();
    }
}
