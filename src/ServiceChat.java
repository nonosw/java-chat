import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class ServiceChat extends Thread {

    public static final int NBUSERSMAX = 2;
    private static int nbUsers = 0;
    private static final PrintStream[] outputs = new PrintStream[NBUSERSMAX];
    private static final Set<String> pseudos = new HashSet<>();

    private final Socket clientSocket;
    private Scanner clientScanner;
    private String pseudo;

    public ServiceChat(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.start();
    }

    public static void main(String[] args) {
        int port = 1234; // Port par défaut

        // Vérification des arguments pour spécifier le port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1024 || port > 65535) {
                    System.err.println("Port invalide ! Utilisez un port entre 1024 et 65535.");
                    return;
                }
            } catch (NumberFormatException e) {
                System.err.println("Argument invalide : le port doit être un entier.");
                return;
            }
        }

        // Démarrage du serveur
        try (ServerSocket serverSocketMain = new ServerSocket(port)) {
            System.out.println("ServiceChat écoute sur le port " + port);
            while (true) {
                if (nbUsers < NBUSERSMAX) {
                    Socket clientSocketMain = serverSocketMain.accept();
                    System.out.println("Nouvelle connexion depuis " + clientSocketMain.getRemoteSocketAddress());
                    new ServiceChat(clientSocketMain);
                } else {
                    try (Socket clientSocketMain = serverSocketMain.accept();
                         PrintStream out = new PrintStream(clientSocketMain.getOutputStream())) {
                        out.println("Serveur plein. Veuillez réessayer plus tard.");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            initFlux();
            askPseudo();
            mainLoop();
        } finally {
            cleanup();
        }
    }

    private synchronized void initFlux() {
        try {
            outputs[nbUsers] = new PrintStream(this.clientSocket.getOutputStream());
            this.clientScanner = new Scanner(this.clientSocket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void askPseudo() {
        PrintStream out = outputs[nbUsers];
        out.println("Bienvenue sur le chat ! Veuillez choisir un pseudo unique :");

        while (true) {
            String proposedPseudo = clientScanner.nextLine().trim();
            synchronized (pseudos) {
                if (!pseudos.contains(proposedPseudo)) {
                    pseudos.add(proposedPseudo);
                    this.pseudo = proposedPseudo;
                    out.println("Pseudo accepté : " + this.pseudo);
                    System.out.println("Nouveau pseudo accepté : " + this.pseudo);
                    break;
                } else {
                    out.println("Pseudo déjà pris. Veuillez en choisir un autre :");
                }
            }
        }

        synchronized (this) {
            nbUsers++;
        }
    }

    private void mainLoop() {
        String buffer;
        try {
            while (this.clientScanner.hasNextLine()) {
                buffer = this.clientScanner.nextLine();
                diffusionMessage(this.pseudo + ": " + buffer);
                System.out.println("User : " + this.pseudo + "message : " + buffer );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void cleanup() {
        try {
            if (this.pseudo != null) {
                synchronized (pseudos) {
                    pseudos.remove(this.pseudo);
                }
            }
            if (clientScanner != null) clientScanner.close();
            if (clientSocket != null) clientSocket.close();
            synchronized (this) {
                nbUsers--;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void diffusionMessage(String message) {
        for (int i = 0; i < nbUsers; i++) {
            outputs[i].println(message);
        }

    }
}
