package be.vub.client;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Label;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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
			System.setProperty("javax.net.ssl.trustStore", "eid.store");
			Socket socket = ((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket("localhost",4444);
			BufferedReader socketBufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
			String message = "message send to server";
			printWriter.println(message);
			System.out.print("Message reply from server: ");
			//System.out.println(socketBufferedReader.readLine());
			
			//close socket after an event later on
			socket.close();
			
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
