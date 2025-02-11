import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

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
        start(); // Lance la thread qui écoute le serveur (méthode run())
    }

    public static void main(String[] args) {
        ClientChat clientChat = new ClientChat(args);

        try {
            while (running) {
                String line = inputConsole.readLine();
                if (line == null) {
                    break;
                }

                // -- PARSEUR DES COMMANDES UTILISATEUR DANS LA CONSOLE
                if (line.trim().startsWith("/")) {
                    // On découpe la ligne sur les espaces
                    String[] tokens = line.trim().split("\\s+", 3);
                    String command = tokens[0]; // ex: /sendFile
                    switch (command) {
                        case "/sendFile":
                            // Usage attendu : /sendFile nomFichier pseudo
                            handleSendFile(tokens);
                            break;

                        default:
                            // Pour toutes les autres commandes, on envoie directement au serveur
                            outputNetwork.println(line);
                            break;
                    }
                } else {
                    // Ce n'est pas une commande, on envoie directement au serveur
                    outputNetwork.println(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnections();
        }
    }

    // Thread qui écoute le serveur (messages entrants)
    @Override
    public void run() {
        try {
            while (running) {
                String serverLine;
                try {
                    serverLine = inputNetwork.readLine();
                } catch (SocketException e) {
                    running = false;
                    outputConsole.println("La connexion au serveur a été interrompue !");
                    break;
                }
                if (serverLine == null) {
                    // Le serveur a peut-être fermé la connexion
                    running = false;
                    break;
                }

                // -- PARSEUR DES MESSAGES SERVEUR (ou messages d'autres clients)
                if (serverLine.contains("FILE:")) {
                    handleFileReception(serverLine);
                } else {
                    // Sinon, c’est un message normal (ou /SERVER, /msgAll, /whois, etc.)
                    outputConsole.println(serverLine);
                }

                // Si le message du serveur indique qu'on est déconnecté
                if (serverLine.trim().equals("Vous avez ete deconnecte...")) {
                    running = false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gère la commande /sendFile
     * Format attendu : /sendFile <nomFichier> <pseudo>
     */
    private static void handleSendFile(String[] tokens) {
        // Vérifier le nombre de paramètres
        if (tokens.length < 3) {
            outputConsole.println("Usage : /sendFile <nomFichier> <pseudo>");
            return;
        }

        String fileName = tokens[1];
        String targetPseudo = tokens[2];

        // Lecture et encodage du fichier en base64
        try {
            // 1) On encode le nom du fichier
            String b64FileName = Base64.getEncoder()
                    .encodeToString(fileName.getBytes(StandardCharsets.UTF_8));

            // 2) On lit le contenu du fichier et on l’encode
            byte[] fileData = Files.readAllBytes(Paths.get(fileName));
            String b64FileContent = Base64.getEncoder().encodeToString(fileData);

            // Puis on envoie la commande /msgTo <pseudo> FILE:bs64(nomFichier):bs64(contenu)
            String messageToSend = "/msgTo " + targetPseudo
                    + " FILE:" + b64FileName
                    + ":" + b64FileContent;

            outputNetwork.println(messageToSend);
        } catch (IOException e) {
            outputConsole.println("Erreur lors de la lecture ou de l'encodage du fichier : " + e.getMessage());
        }
    }

    /**
     * Gère la réception d'un message contenant "FILE:"
     * Format attendu :
     *   [PRIVE] [pseudoSource] : FILE:bs64(nomFichier):bs64(contenuFichier)
     */
    private static void handleFileReception(String serverLine) {
        try {
            // On récupère la partie avant "FILE:" => ex: "[PRIVE] [alice] : "
            int fileIndex = serverLine.indexOf("FILE:");
            String prefix = serverLine.substring(0, fileIndex);

            // On récupère la partie après "FILE:" => ex: "bs64(nomFichier):bs64(contenuFichier)"
            String filePart = serverLine.substring(fileIndex + 5);

            // On découpe sur ":" => on devrait avoir 2 morceaux : [ b64NomFichier, b64ContenuFichier ]
            String[] fileTokens = filePart.split(":", 2);
            if (fileTokens.length < 2) {
                // Mauvais format => on affiche le message tel quel
                outputConsole.println(serverLine);
                return;
            }

            String b64Name = fileTokens[0];
            String b64Content = fileTokens[1];

            // Décodage du nom de fichier
            byte[] decodedNameBytes = Base64.getDecoder().decode(b64Name);
            String decodedFileName = new String(decodedNameBytes, StandardCharsets.UTF_8);

            // Décodage du contenu
            byte[] decodedContent = Base64.getDecoder().decode(b64Content);

            // Écriture dans le répertoire local du client
            Files.write(Paths.get(decodedFileName + "_1"), decodedContent);

            // Enfin, on affiche le message :
            // ex: "[PRIVE] [bob] : Réception du fichier image.png OK !"
            outputConsole.println(prefix + "Réception du fichier " + decodedFileName + " OK !");

        } catch (IOException e) {
            outputConsole.println("Erreur pendant la réception ou l'écriture du fichier : " + e.getMessage());
        }
    }

    private void initStreams(String[] args) {
        String host = "localhost";
        int port = 2222;

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
            // Pour signaler au serveur qu'on est un client Java (si tu utilises déjà /clientjava)
            outputNetwork.println("/clientjava");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void closeConnections() {
        try {
            running = false;
            if (socket != null) socket.close();
            if (inputNetwork != null) inputNetwork.close();
            if (inputConsole != null) inputConsole.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
