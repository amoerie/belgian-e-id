package be.msec.government;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class Timestamp {
	public static void main ( String [] arguments )
    {
        System.out.println("Implement the signed Timestamp");
        
        //server side
        System.setProperty("javax.net.debug", "ssl");
		System.setProperty("javax.net.ssl.keyStoreType", "jks");
		//System.setProperty("javax.net.ssl.keyStore", "src/belgianeid.jks");
		System.setProperty("javax.net.ssl.keyStore", "src/belgianeidsha1.jks");
		System.setProperty("javax.net.ssl.keyStorePassword", "123456");
		//client side
		//**System.setProperty("javax.net.ssl.trustStoreType", "jks");
		//System.setProperty("javax.net.ssl.trustStore", "src/belgianeid.jks");
		//**System.setProperty("javax.net.ssl.trustStore", "src/belgianeidsha1.jks");
		//**System.setProperty("javax.net.ssl.trustStorePassword", "123456");
         
        ServerSocket serverSocket = null;
        try {
			serverSocket = ((SSLServerSocketFactory)SSLServerSocketFactory.getDefault()).createServerSocket(4444);
//			serverSocket = new ServerSocket(4444);
//        	SSLContext context = SSLContext.getInstance("TLS");
//            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
//            KeyStore keyStore = KeyStore.getInstance("JKS");
//         
//            keyStore.load(new FileInputStream("src/belgianeidsha1.jks"), "123456".toCharArray());
//            keyManagerFactory.init(keyStore, "123456".toCharArray());
//            context.init(keyManagerFactory.getKeyManagers(), null, null);
//         
//            SSLServerSocketFactory factory = context.getServerSocketFactory();
//         
//            serverSocket = factory.createServerSocket(4444);
		} catch (IOException e) {
			e.printStackTrace();
//		} catch (NoSuchAlgorithmException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (KeyStoreException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (CertificateException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (UnrecoverableKeyException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (KeyManagementException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
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
