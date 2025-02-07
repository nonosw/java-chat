import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.HashMap;


public class ServiceChat extends Thread {

    private static final int NBUSERSMAX = 3;

    // Au lieu de PrintStream[] outputs et d’un compteur nbUsers, on utilise une ArrayList<>()
    //Pour pas se prendre la tête quand c'est pas le dernier utilisateur connecté qui se deconnecte
    //On synchronise dessus quand on ajoute, supprime ou lit la liste.
    private static final List<PrintStream> outputs = new ArrayList<>();
    // structure pour avoir un pseudo unique
    private static final Set<String> pseudos = new HashSet<>();

    private static final HashMap<String, PrintStream> userStreams = new HashMap<>();

    private static final HashMap<String, String> userPasswords = new HashMap<>();


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
                    sendClient("[SERVER] Serveur plein, veuillez réessayer plus tard.");
                    clientSocket.close();
                    return false;
                }
            }

            while (true) {
                sendClient("[SERVER] Entrez votre pseudo :");
                if (!clientScanner.hasNextLine()) {
                    // Le client a fermé la connexion avant de choisir un pseudo
                    return false;
                }
                String choixPseudo = clientScanner.nextLine();
                if (choixPseudo == null) {
                    sendClient("[SERVER] Pseudo invalide. Veuillez réessayer.");
                    continue;
                }
                choixPseudo = choixPseudo.trim().toLowerCase();
                if (choixPseudo.isEmpty()) {
                    sendClient("[SERVER] Pseudo vide. Veuillez fournir un nom non vide.");
                    continue;
                }

                synchronized (pseudos) {
                    // Vérifier si le pseudo est déjà connecté
                    if (userStreams.containsKey(choixPseudo)) {
                        sendClient("[SERVER] Ce login est déjà connecté. Impossible de se connecter avec.");
                        return false;
                    }

                    if (userPasswords.containsKey(choixPseudo)){
                        int essais = 3;
                        while (essais > 0) {
                            sendClient("[SERVER] Entrez votre mot de passe :");
                            if (!clientScanner.hasNextLine()) {
                                return false;
                            }
                            String password = clientScanner.nextLine().trim();

                            if (password.equals(userPasswords.get(choixPseudo))) {
                                this.pseudoDuClient = choixPseudo;
                                break; // Connexion réussie
                            } else {
                                essais--;
                                sendClient("[SERVER] Mot de passe incorrect. Tentatives restantes : " + essais);
                            }
                        }

                        if (essais == 0) {
                            sendClient("[SERVER] Trop de tentatives échouées. Déconnexion.");
                            return false;
                        }
                    } else {
                        // Nouvel utilisateur, demander un mot de passe
                        sendClient("[SERVER] Nouveau login détecté. Choisissez un mot de passe :");
                        if (!clientScanner.hasNextLine()) {
                            return false;
                        }
                        String newPassword = clientScanner.nextLine().trim();

                        if (newPassword.isEmpty()) {
                            sendClient("[SERVER] Mot de passe invalide. Connexion refusée.");
                            return false;
                        }

                        // Stocker le login et le mot de passe
                        userPasswords.put(choixPseudo, newPassword);
                        this.pseudoDuClient = choixPseudo;
                    }
                    // Ajouter le pseudo à la liste des connectés
                    pseudos.add(this.pseudoDuClient);
                    synchronized (userStreams) {
                        userStreams.put(this.pseudoDuClient, this.clientOutput);
                    }
                }
                break;
            }

            synchronized (outputs) {
                outputs.add(this.clientOutput);
            }

            // Message de bienvenue côté console et côté client
            System.out.println("Nouvel utilisateur : <" + this.pseudoDuClient  + "> | Utilisateurs connectes : " + outputs.size());
            sendClient("[SERVER] Bienvenue <" + this.pseudoDuClient + ">! Utilisateurs connectes : " + outputs.size());
            sendClient(afficheListPseudo());
            diffusionMessage("[SERVER] <" + this.pseudoDuClient + "> a rejoint le chat !", this.clientOutput);
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
                String buffer = clientScanner.nextLine().trim();

                if (buffer.isEmpty()) {
                    continue; // Ignore les lignes vides
                }

                // Découper l'entrée pour récupérer la commande et ses arguments
                String[] parts = buffer.split(" ", 2);
                String command = parts[0].toLowerCase(); // Convertir en minuscules pour éviter la casse
                String arguments = (parts.length > 1) ? parts[1] : ""; // Arguments après la commande

                switch (command) {
                    case "/quit":
                        return; // Quitter la boucle

                    case "/list":
                        sendClient(afficheListPseudo());
                        break;

                    case "/msgto":
                        envoyerMessagePrive(arguments);
                        break;

                    case "/msgall":
                        diffusionMessage("[" + this.pseudoDuClient + "] : " + arguments, clientOutput);
                        break;

                    default:
                        diffusionMessage("[" + this.pseudoDuClient + "] : " + buffer, clientOutput);
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void cleanup() {
        if (this.pseudoDuClient != null) {
            synchronized (userStreams) {
                userStreams.remove(this.pseudoDuClient);
            }
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
            sendClient("Vous avez ete deconnecte... ");
            if (clientScanner != null) clientScanner.close();
            if (clientOutput != null) clientOutput.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (this.pseudoDuClient != null) {
            System.out.println("Deconnexion utilisateur : " + this.pseudoDuClient  + " | Utilisateurs connectés : " + outputs.size());
            diffusionMessage("[SERVER] " + this.pseudoDuClient + " a quitte le chat !", clientOutput);
        }
    }

    public static void diffusionMessage(String message, PrintStream emeteur) {
        synchronized (outputs) {
            for (PrintStream ps : outputs) {
                if ( ps != emeteur ){
                    ps.println(message);
                }
            }
            System.out.println(message);
        }
    }

    private static String getPseudoByPrintStream(PrintStream ps) {
        synchronized (userStreams) {
            for (HashMap.Entry<String, PrintStream> entry : userStreams.entrySet()) {
                if (entry.getValue().equals(ps)) {
                    return entry.getKey(); // Retourne le pseudo associé au PrintStream
                }
            }
        }
        return null; // Retourne null si aucun pseudo trouvé
    }

    private String afficheListPseudo() {
        String listepseudo;
        listepseudo = "[SERVER] Utilisateurs connectes : ";
        for ( String parcourPseudo : pseudos ){
            listepseudo += "<" + parcourPseudo + "> ";
        }
        listepseudo += "!";
        return listepseudo;
    }


    private void envoyerMessagePrive(String arguments) {
        if (arguments.isEmpty() || !arguments.contains(" ")) {
            sendClient("[SERVER] Usage : /msgTo <pseudo> <message>");
            return;
        }

        // Extraire le pseudo et le message
        String[] parts = arguments.split(" ", 2);
        String targetPseudo = parts[0];
        String message = parts[1];

        synchronized (userStreams) {
            if (!userStreams.containsKey(targetPseudo)) {
                sendClient("[SERVER] L'utilisateur '" + targetPseudo + "' n'existe pas ou n'est pas connecté.");
                return;
            }

            PrintStream targetStream = userStreams.get(targetPseudo);
            targetStream.println("[PRIVE] [" + this.pseudoDuClient + "] : " + message);
            System.out.println("[PRIVE] -> [" + targetPseudo + "] : " + message);
        }
    }


}
