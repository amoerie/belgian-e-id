package be.msec.government;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Timestamp {
    public static void main(String[] arguments) {
        System.out.println("Implement the signed Timestamp");

        //server side
        System.setProperty("javax.net.debug", "ssl");
        System.setProperty("javax.net.ssl.keyStoreType", "jks");
        System.out.println("javax.net.ssl.keyStore = " + System.getProperty("javax.net.ssl.keyStore"));
        if (System.getProperty("javax.net.ssl.keyStore") == null) {
            System.setProperty("javax.net.ssl.keyStore", "src/belgianeidsha1.jks");
        }

        System.setProperty("javax.net.ssl.keyStorePassword", "123456");

        ServerSocket serverSocket = null;
        try {
            serverSocket = ((SSLServerSocketFactory) SSLServerSocketFactory.getDefault()).createServerSocket(4444);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Government Server up & ready for connections....");

        while (true)
            try {
                Socket clientSocket = serverSocket.accept();
                new ServerThread(clientSocket).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}
