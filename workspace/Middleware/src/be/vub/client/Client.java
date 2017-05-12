package be.vub.client;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import be.vub.client.connection.Connection;
import be.vub.client.connection.IConnection;
import be.vub.client.connection.SimulatedConnection;
import javax.smartcardio.*;

import org.eclipse.swt.widgets.Label;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextArea;

import org.eclipse.swt.SWT;

public class Client {

	protected Shell shell;
	
	
	//global variables
	
	
	//connection to card
	IConnection con;
	CommandAPDU a;
	ResponseAPDU res;

	//UI variables
	private JFrame frame;
    JTextArea communication;
	
	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Client window = new Client();
			window.frame.setVisible(true);
						
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Client() {
		initialize();
	}

	private void initialize() {
		frame = new JFrame();
		frame.setBounds(200, 200, 400, 400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		JButton btnConnectButton = new JButton("Connect");
		btnConnectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					cardConnect();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		frame.getContentPane().add(btnConnectButton, BorderLayout.SOUTH);
		
		communication = new JTextArea();
		frame.getContentPane().add(communication, BorderLayout.CENTER);
	}
	
	private void cardConnect() throws Exception{
		//Simulation:
		con = new SimulatedConnection();

		//Real Card:
		//con = new Connection();
		//((Connection)c).setTerminal(0); //depending on which cardreader you use
		
		con.connect(); 
		
		try {

			/*
			 * For more info on the use of CommandAPDU and ResponseAPDU:
			 * See http://java.sun.com/javase/6/docs/jre/api/security/smartcardio/spec/index.html
			 */
			CreateSelectApplet();
			
			
			
		} catch (Exception e) {
			throw e;
		}
		finally {
			con.close();  // close the connection with the card
		}
	}
	
	private void CreateSelectApplet() throws Exception {
		try {
			//0. create applet (only for simulator!!!)
			a = new CommandAPDU(0x00, 0xa4, 0x04, 0x00,new byte[]{(byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x08, 0x01}, 0x7f);
			res = con.transmit(a);
			System.out.println(res);
			if (res.getSW()!=0x9000) throw new Exception("select installer applet failed");
			
			a = new CommandAPDU(0x80, 0xB8, 0x00, 0x00,new byte[]{0xb, 0x01,0x02,0x03,0x04, 0x05, 0x06, 0x07, 0x08, 0x09,0x00, 0x00, 0x00}, 0x7f);
			res = con.transmit(a);
			System.out.println(res);
			if (res.getSW()!=0x9000) throw new Exception("Applet creation failed");
			
			//1. Select applet  (not required on a real card, applet is selected by default)		
			a = new CommandAPDU(0x00, 0xa4, 0x04, 0x00,new byte[]{0x01,0x02,0x03,0x04, 0x05, 0x06, 0x07, 0x08, 0x09,0x00, 0x00}, 0x7f);
			res = con.transmit(a);
			System.out.println(res);
			if (res.getSW()!=0x9000) throw new Exception("Applet selection failed");

		} catch (Exception e) {
			throw e;
		}
	}
	
	private byte[] getNewTimeFromGov()
	{
		//socket connection to Government
		//System.setProperty("javax.net.ssl.trustStore", "eid.store");
		HttpsURLConnection.setDefaultHostnameVerifier ((hostname, session) -> true);
		System.setProperty("javax.net.debug", "ssl");
		System.setProperty("javax.net.ssl.keyStoreType", "jks");
		System.setProperty("javax.net.ssl.keyStore", "middleware.jks");
		System.setProperty("javax.net.ssl.keyStorePassword", "ab123456");
		System.setProperty("javax.net.ssl.trustStoreType", "jks");
		System.setProperty("javax.net.ssl.trustStore", "middleware.jks");
		System.setProperty("javax.net.ssl.trustStorePassword", "ab123456");
		
		SSLSocketFactory socketFactory = ((SSLSocketFactory)SSLSocketFactory.getDefault());
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) socketFactory.createSocket("127.0.0.1",4444);
			
			InputStream inputStream = sslsocket.getInputStream();
			OutputStream outputStream = sslsocket.getOutputStream();
			
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			
			//OutputStream outputStream = connection.getOutputStream();
			PrintWriter printWriter = new PrintWriter(outputStream, true);
			
			System.out.print("Sending message to server: ");
			printWriter.println("Hallo dit is de client");
			
			System.out.print("Message reply from server: ");
			String string = null;
			while ((string = bufferedReader.readLine()) != null) {
			    System.out.println("Received: " + string);
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}

	
	
	//byte array from hex string
	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	public static String byteArrayToHexString(byte[] bytes){
		final char[] hexArray = "0123456789ABCDEF".toCharArray();
		char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

	public static byte[] longToBytes(long x) {
	    ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE/8);
	    buffer.putLong(x);
	    return buffer.array();
	}
	
}
	
