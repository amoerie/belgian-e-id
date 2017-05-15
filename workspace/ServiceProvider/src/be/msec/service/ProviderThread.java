package be.msec.service;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ProviderThread extends Thread {
	ServerSocket serverSocket;
	ProviderThread(ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}
	
	//MESSAGES from M -> SP
	private final static String MSG_RESULT = "MessageResult";
	
	public void run() {
		
		
		try {
			
			Socket socket = serverSocket.accept();
			
			PrintWriter outputWriter = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            Provider.logging.setText(Provider.logging.getText() + "\nClientSocket ready...");
            
			String message = null;
			while ((message = inputReader.readLine()) != null) {
				
				Provider.logging.setText(Provider.logging.getText() + "\nMessage received");
				
				if (message.equals(MSG_RESULT)) {
					String output = TreatMessage(message);
					outputWriter.println(output);
				}
				//if other message like abort, etc...
			}			
		} catch(IOException e) {
			e.printStackTrace();
		}
		
	}

	private String TreatMessage(String message) {
		// TODO Auto-generated method stub
		return "";
	}
	
	
}
