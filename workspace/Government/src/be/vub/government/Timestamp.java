package be.vub.government;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLServerSocketFactory;

public class Timestamp {
	public static void main ( String [] arguments )
    {
        System.out.println("Implement the signed Timestamp");
        
        System.setProperty("javax.net.ssl.keyStore", "government.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "ab123456");
         
        ServerSocket serverSocket = null;
		try {
			serverSocket = ((SSLServerSocketFactory)SSLServerSocketFactory.getDefault()).createServerSocket(4444);
		} catch (IOException e) {
			e.printStackTrace();
		}
        System.out.println("Government Server up & ready for connections....");
        
        while (true)
			try {
				Socket newSocket = serverSocket.accept();
				new ServerThread(newSocket).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
    }
}
