import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class ServiceChat extends Thread {

    private static final int NBUSERSMAX = 3;

    // Au lieu de PrintStream[] outputs et d’un compteur nbUsers, on utilise une ArrayList<>()
    //Pour pas se prendre la tête quand c'est pas le dernier utilisateur connecté qui se deconnecte
    //On synchronise dessus quand on ajoute, supprime ou lit la liste.
    private static final List<PrintStream> outputs = new ArrayList<>();

    // structure pour avoir un pseudo unique
    private static final Set<String> pseudos = new HashSet<>();

    private final Socket clientSocket;
    private Scanner clientScanner;
    private PrintStream clientOutput;

    private String pseudoDuClient;

    public ServiceChat(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.start();
    }

    public static void main(String[] args) {
        int port = 1234;

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

        // Création du ServerSocket
        try (ServerSocket serverSocketMain = new ServerSocket(port)) {
            System.out.println("ServiceChat écoute sur le port " + port);

            // Boucle infinie d'acceptation des connexions
            while (true) {
                Socket clientSocketMain = serverSocketMain.accept();
                System.out.println("Nouvelle connexion depuis " + clientSocketMain.getRemoteSocketAddress());
                new ServiceChat(clientSocketMain);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            if (!initFlux()) {
                return; // Si initFlux échoue, on arrête ce thread
            }
            mainLoop();
        } finally {
            cleanup();
        }
    }

    private boolean initFlux() {
        try {
            this.clientOutput = new PrintStream(clientSocket.getOutputStream(), true);
            this.clientScanner = new Scanner(clientSocket.getInputStream());

            synchronized (outputs) {
                if (outputs.size() >= NBUSERSMAX) {
                    sendClient("Serveur plein, veuillez réessayer plus tard.");
                    clientSocket.close();
                    return false;
                }
            }

            while (true) {
                sendClient("Entrez votre pseudo :");
                if (!clientScanner.hasNextLine()) {
                    // Le client a fermé la connexion avant de choisir un pseudo
                    return false;
                }
                String choixPseudo = clientScanner.nextLine();
                if (choixPseudo == null) {
                    sendClient("Pseudo invalide. Veuillez réessayer.");
                    continue;
                }
                choixPseudo = choixPseudo.trim();
                if (choixPseudo.isEmpty()) {
                    sendClient("Pseudo vide. Veuillez fournir un nom non vide.");
                    continue;
                }

                synchronized (pseudos) {
                    if (pseudos.contains(choixPseudo)) {
                        sendClient("Ce pseudo est déjà utilisé. Choisissez-en un autre :");
                    } else {
                        pseudos.add(choixPseudo);
                        this.pseudoDuClient = choixPseudo;
                        break;
                    }
                }
            }

            synchronized (outputs) {
                outputs.add(this.clientOutput);
            }

            // Message de bienvenue côté console et côté client
            System.out.println("Nouvel utilisateur : " + this.pseudoDuClient  + " | Utilisateurs connectés : " + outputs.size());
            sendClient("Bienvenue " + this.pseudoDuClient + " !");
            sendClient("Utilisateurs connectés : " + outputs.size());

            diffusionMessage(this.pseudoDuClient + " a rejoint le chat !");

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendClient(String message) {
        if (clientOutput != null) {
            clientOutput.println(message);
        }
    }

    private void mainLoop() {
        try {
            while (clientScanner.hasNextLine()) {
                String buffer = clientScanner.nextLine();
                if (buffer.equalsIgnoreCase("/quit")) {
                    sendClient("Vous avez été déconnecté.");
                    break;
                }
                diffusionMessage(this.pseudoDuClient + " : " + buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cleanup() {
        if (this.pseudoDuClient != null) {
            synchronized (pseudos) {
                pseudos.remove(this.pseudoDuClient);
            }
        }

        if (this.clientOutput != null) {
            synchronized (outputs) {
                outputs.remove(this.clientOutput);
            }
        }

        try {
            if (clientScanner != null) clientScanner.close();
            if (clientOutput != null) clientOutput.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (this.pseudoDuClient != null) {
            System.out.println("Deconnexion utilisateur : " + this.pseudoDuClient
                    + " | Utilisateurs connectés : " + outputs.size());
            diffusionMessage(this.pseudoDuClient + " a quitté le chat !");
        }
    }

    public static void diffusionMessage(String message) {
        synchronized (outputs) {
            for (PrintStream ps : outputs) {
                ps.println(message);
            }
            System.out.println(message);
        }
    }
}
