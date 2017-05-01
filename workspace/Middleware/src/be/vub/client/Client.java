package be.vub.client;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Label;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.swt.SWT;

public class Client {

	protected Shell shell;

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			//socket connection to Government
			//System.setProperty("javax.net.ssl.trustStore", "eid.store");
			
			System.setProperty("javax.net.debug", "ssl");
			System.setProperty("javax.net.ssl.keyStoreType", "jks");
			System.setProperty("javax.net.ssl.keyStore", "middleware.jks");
			System.setProperty("javax.net.ssl.keyStorePassword", "ab123456");
			System.setProperty("javax.net.ssl.trustStoreType", "jks");
			System.setProperty("javax.net.ssl.trustStore", "middleware.jks");
			System.setProperty("javax.net.ssl.trustStorePassword", "ab123456");
			
			SSLSocketFactory socketFactory = ((SSLSocketFactory)SSLSocketFactory.getDefault());
			
			URL url = new URL("https://localhost:4444");
			HttpsURLConnection connection = (HttpsURLConnection)url.openConnection();
			connection.setSSLSocketFactory(socketFactory);

			InputStream inputStream = connection.getInputStream();
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			
			//OutputStream outputStream = connection.getOutputStream();
			PrintWriter printWriter = new PrintWriter(connection.getOutputStream(), true);
			
			System.out.print("Sending message to server: ");
			printWriter.write("Hallo dit is de client");
			
			System.out.print("Message reply from server: ");
			String string = null;
			while ((string = bufferedReader.readLine()) != null) {
			    System.out.println("Received: " + string);
			}
			
			
			//open GUI
			Client window = new Client();
			window.open();
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
		Display display = Display.getDefault();
		createContents();
		shell.open();
		shell.layout();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();
		shell.setSize(450, 300);
		shell.setText("SWT Application");
		
		Label lblTodoLoginlogoutTo = new Label(shell, SWT.NONE);
		lblTodoLoginlogoutTo.setBounds(32, 29, 226, 15);
		lblTodoLoginlogoutTo.setText("TODO: login/logout to the active service");

	}
}
