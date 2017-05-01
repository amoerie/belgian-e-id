package be.vub.government;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class ServerThread extends Thread {
	Socket socket;
	ServerThread(Socket socket) {
		this.socket = socket;
	}
	
	public void run() {
		try {
			OutputStream outputStream = socket.getOutputStream();
			InputStream inputStream = socket.getInputStream();
			
			BufferedReader inputReader =  new BufferedReader(new InputStreamReader(inputStream));
			PrintWriter outputWriter = new PrintWriter(outputStream, true);
			
			String string = null;
			while ((string = inputReader.readLine()) != null) {
			    System.out.println("Received: " + string);
			    outputWriter.println("ECHO: " + string);
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
		
	}
}
