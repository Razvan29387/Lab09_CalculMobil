import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class SimpleTcpClient {
    public static void main(String[] args) {
        String serverAddress = "127.0.0.1"; // Folosește 127.0.0.1 pentru emulator (cu adb forward activ)
        // sau adresa IP a dispozitivului fizic (ex: "192.168.1.100")
        int serverPort = 12345;

        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to the server. Type 'exit' to quit.");
            String userInput;
            do {
                System.out.print("Enter message: ");
                userInput = scanner.nextLine();
                output.println(userInput);
                System.out.println("Sent: " + userInput);

                // Așteaptă răspunsul de la server (care ar trebui să fie mesajul difuzat)
                String serverResponse = input.readLine();
                if (serverResponse == null) {
                    System.out.println("Server closed the connection.");
                    break;
                }
                System.out.println("Received: " + serverResponse);

            } while (!"exit".equalsIgnoreCase(userInput));

        } catch (Exception e) {
            System.err.println("Error in TCP client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("Client disconnected.");
        }
    }
}
