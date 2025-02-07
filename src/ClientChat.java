import java.io.*;
import java.net.*;

public class ClientChat extends Thread {
    private static Socket socket;
    private static BufferedReader inputConsole;
    private static PrintWriter outputNetwork;
    private static BufferedReader inputNetwork;
    private static PrintStream outputConsole;

    // Variable partagée pour contrôler l'exécution des boucles.
    private static volatile boolean running = true;

    // Constructeur
    public ClientChat(String[] args) {
        initStreams(args);
        start();
        try {
            while (running) {
                String line = inputConsole.readLine();
                if (line == null) {
                    break; // Si null, on sort ou on peut tenter de relire
                }
                outputNetwork.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        closeConnections();
    }


    public static void main(String[] args) {
        new ClientChat(args);
    }

    public void run() {

        try {
            while (running) {

                String serverLine = inputNetwork.readLine();
                if (serverLine == null) {
                    break; // Le serveur a coupé la connexion
                }
                outputConsole.println(serverLine);
                if (serverLine.trim().equals("Vous avez ete deconnecte...")) {
                    running = false;
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initStreams(String[] args) {
        String host = "localhost";
        int port = 2222;

        // Si deux arguments sont présents, on les utilise
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }

        try {
            socket = new Socket(host, port);
            inputNetwork = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            inputConsole = new BufferedReader(new InputStreamReader(System.in));
            outputNetwork = new PrintWriter(socket.getOutputStream(), true);
            outputConsole = System.out;
            outputConsole.println("Connecté à " + host + " sur le port " + port);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeConnections() {
        try {
            if (socket != null) socket.close();
            if (inputNetwork != null) inputNetwork.close();
            if (inputConsole != null) inputConsole.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
