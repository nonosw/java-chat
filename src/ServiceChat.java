import java.util.*;
import java.io.*;
import java.net.*;

public class ServiceChat extends Thread {

    private static final int DEFAULT_TCP_PORT = 1234;

    public static final int NBUSERSMAX = 2;
    public static int nbUsers = 0;
    public static PrintStream[] outputs = new PrintStream[NBUSERSMAX];

    private  Socket clientSocket;
    private  Scanner clientScanner;

    public ServiceChat(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.start();
    }

    public static void main(String[] args) {

        Socket clientSocketMain;
        ServerSocket serverSocketMain;

        try {
            serverSocketMain = new ServerSocket(DEFAULT_TCP_PORT);
            System.out.println(String.format("ServiceChat ecoute sur le port %d", DEFAULT_TCP_PORT));
            while (true) {
                if (nbUsers < NBUSERSMAX - 1) {
                    clientSocketMain = serverSocketMain.accept();
                    System.out.println(String.format("Nouvelle connexion depuis %s  numero Client %d", clientSocketMain.getRemoteSocketAddress().toString(),nbUsers));
                    new ServiceChat(clientSocketMain);
                } else {
                    serverSocketMain.close();
                }
            }

        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void run() {
        this.initFlux();
        this.mainLoop();
    }

    private synchronized void initFlux() {
        try {
            outputs[nbUsers] = new PrintStream(this.clientSocket.getOutputStream());
            this.clientScanner = new Scanner(this.clientSocket.getInputStream());
            nbUsers++;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mainLoop() {
        String buffer;
        while(this.clientScanner.hasNextLine()) {
            buffer = this.clientScanner.nextLine();
            diffusionMessage(buffer);
        }
    }

    public static synchronized void diffusionMessage(String message) {
        for(int i=0; i<nbUsers; i++) {
            outputs[i].println(message);
        }
    }
}