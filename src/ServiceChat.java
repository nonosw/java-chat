import java.util.*;
import java.io.*;
import java.net.*;


public class ServiceChat extends Thread {

    private static final int PORTTCP = 1234;

    public static final int NBUSERSMAX = 2;
    public static int nbUsers = 0;
    public static PrintStream[] outputs = new PrintStream[NBUSERSMAX];

    private  final Socket clientSocket;
    private  Scanner clientScanner;

    public ServiceChat(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.start();
    }

    public static void main(String[] args) {

        Socket clientSocketMain;
        ServerSocket serverSocketMain;

        try {
            serverSocketMain = new ServerSocket(PORTTCP);
            System.out.println("ServiceChat ecoute sur le port " + PORTTCP);
            while (true) {
                if (nbUsers < NBUSERSMAX - 1) {
                    clientSocketMain = serverSocketMain.accept();
                    System.out.println("Nouvelle connexion depuis " + clientSocketMain.getRemoteSocketAddress().toString()+ " | Numero Client" + nbUsers);
                    new ServiceChat(clientSocketMain);
                } else {
                    serverSocketMain.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
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