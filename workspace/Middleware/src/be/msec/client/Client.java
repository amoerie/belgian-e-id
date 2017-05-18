package be.msec.client;

//import be.msec.client.connection.Connection;
import be.msec.client.connection.IConnection;
import be.msec.client.connection.SimulatedConnection;
//import javax.smartcardio.*;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

import javax.net.ServerSocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;

public class Client {

	
	//global variables
	private final static byte IDENTITY_CARD_CLA = (byte) 0x80;
	private static final byte VALIDATE_PIN_INS = 0x22;
	private static final byte GET_SERIAL_INS = 0x24;
	
	
	//from M -> SC
	private static final byte HELLO_DIS = 0x40;
	private static final byte NEW_TIME = 0x41;
	private static final byte NEW_SERVICE_CERT = 0x42;
	private static final byte SERVICE_CERT_DONE = 0x43;
	private static final byte SERVICE_AUTH = 0x44;
	private static final byte SERVICE_RESP_CHALLENGE = 0x45;
	private static final byte SERVICE_CHALLENGE = 0x46;
	private static final byte NEW_QUERY = 0x47;
	private static final byte QUERY_DONE = 0x48;
	private static final byte GET_QUERY = 0x49;
	
	
	//from SC -> M
	private final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
	private final static short SW_ABORT = 0x6339;
	private final static short SW_REQ_REVALIDATION = 0x6340;
	private final static short SW_SIG_NO_MATCH = 0x6341;
	private final static short SW_CERT_EXPIRED = 0x6342;
	private final static short SW_VERIFICATION_FAILED = 0x6343;
	
	//MESSAGES from M -> G
	private final static String MSG_GET_TIME = "RevalidationRequest";
	
	//APDU
	private static final int APDU_MAX_BUFF_SIZE = 128;
	
	//connection to card
	IConnection con;
	CommandAPDU a;
	ResponseAPDU res;

	//UI variables
	private JFrame frame;
    JTextArea communication;
    
    //SP connection
    Socket providerSocket;
	BufferedReader providerReader;
	PrintWriter providerWriter;
	
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
			createSelectApplet();
			
			//step 1: updateTime()
			updateTime();
			//step 2: authenticateServiceProvider()
			initConnectionServiceProvider();
			authenticateServiceProvider();
			//step 3: authenticateCard()
			String serviceResponse = authenticateCard();
			//step 4: releaseAttributes()
			releaseAttributes(serviceResponse);
			
			closeConnectionServiceProvider();
			
			
		} catch (Exception e) {
			throw e;
		}
		finally {
			con.close();  // close the connection with the card
		}
	}
	
	//step 0
	private void createSelectApplet() throws Exception {
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
	
	//step 1
	private void updateTime() throws Exception {
		communication.append("Connecting to card - inserted in reader\n");
		
		//step 1 (1) Hello", send currentTime to card
		Date date = new Date();
		Long timestamp = date.getTime()/1000;
		a = new CommandAPDU(IDENTITY_CARD_CLA, HELLO_DIS, 0x00, 0x00,longToBytes(timestamp));
		res = con.transmit(a);
		System.out.println(res);
		//receive reqRevalidation
		if (res.getSW()== SW_REQ_REVALIDATION){
			communication.append("RevalidationRequest: new timestamp required from Government server\n");
			
			byte[] new_time = getNewTimeFromGov();
			
			if (new_time != null) {
				//step 1 (9) update time on card
				a = new CommandAPDU(IDENTITY_CARD_CLA, NEW_TIME, 0x00, 0x00,new_time);
				res = con.transmit(a);
				System.out.println(res);
				if (res.getSW()==SW_ABORT)throw new Exception("Aborted: Cannot update time");
				else if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
				
				communication.append("RevalidationRequest: new timestamp is saved on the card\n");	
			}
			
			//****only for testing the code: extra send hello to the card with the same timestamp 
			//a = new CommandAPDU(IDENTITY_CARD_CLA, HELLO_DIS, 0x00, 0x00,longToBytes(timestamp));
			//res = con.transmit(a);
			//if (res.getSW()!=SW_REQ_REVALIDATION) {
			//	communication.append("NEW RevalidationRequest is false now\n");
			//}
			else if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
			
		} else { //goto step 2 : authenticateServiceProvider
			communication.append("RevalidationRequest: false");
		}
	}
	
	//step 1 (7)
	private byte[] getNewTimeFromGov()
	{
		//socket connection to Government
		HttpsURLConnection.setDefaultHostnameVerifier ((hostname, session) -> true);
		//only for the server side
		//**System.setProperty("javax.net.debug", "ssl");
		//**System.setProperty("javax.net.ssl.keyStoreType", "jks");
		//System.setProperty("javax.net.ssl.keyStore", "src/belgianeid.jks");
		//**System.setProperty("javax.net.ssl.keyStore", "src/belgianeidsha1.jks");
		//**System.setProperty("javax.net.ssl.keyStorePassword", "123456");
		//only for the client side
		System.setProperty("javax.net.ssl.trustStoreType", "jks");
		//System.setProperty("javax.net.ssl.trustStore", "src/belgianeid.jks");
		System.setProperty("javax.net.ssl.keyStore", "src/belgianeidsha1.jks");
		System.setProperty("javax.net.ssl.trustStorePassword", "123456");
		
		//SSL socket connection
//		SSLSocketFactory socketFactory = ((SSLSocketFactory)SSLSocketFactory.getDefault());
//		SSLSocket sslsocket;
		try {
//			sslsocket = (SSLSocket) socketFactory.createSocket("127.0.0.1",4444);
//			InputStream inputStream = sslsocket.getInputStream();
//			OutputStream outputStream = sslsocket.getOutputStream();
			
			//TEST: socket connection without SSL
			Socket govSocket = new Socket("127.0.0.1", 4444);
			
			InputStream inputStream = govSocket.getInputStream();
			OutputStream outputStream = govSocket.getOutputStream();
			
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			
			//OutputStream outputStream = connection.getOutputStream();
			PrintWriter printWriter = new PrintWriter(outputStream, true);
			
			//System.out.print("Sending message to server: ");
			printWriter.println(MSG_GET_TIME);
			
			System.out.print("Message reply from server: ");
			
			String msgFromGServer = bufferedReader.readLine();
			if (msgFromGServer.equalsIgnoreCase("Abort")){
            	communication.append("Error in timeserver\n");
            	try {
					throw new Exception("Error in timeserver");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }else{
            	System.out.println("Received: " + msgFromGServer);
            	communication.append(msgFromGServer + "\n");
            	return hexStringToByteArray(msgFromGServer);
            }
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	//step 2
	private void initConnectionServiceProvider() throws IOException {
		//connection to the SP
		providerSocket = new Socket("127.0.0.1", 8888);
		
		InputStream providerInputStream = providerSocket.getInputStream();
		OutputStream providerOutputStream = providerSocket.getOutputStream();
		InputStreamReader providerInputStreamReader = new InputStreamReader(providerInputStream);
		
		providerReader = new BufferedReader(providerInputStreamReader);
		providerWriter = new PrintWriter(providerOutputStream, true);
	}
	private void authenticateServiceProvider() throws Exception {
		String certificateMessage = providerReader.readLine();
		if (certificateMessage.equalsIgnoreCase("Abort")){
        	communication.append("Error in connection with service provider\n");
        	try {
				throw new Exception("Error in service provider");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }else{
        	//System.out.println("Received: " + certificateMessage);
        	//step 2(1) send certificate to card
        	byte[] msg = hexStringToByteArray(certificateMessage);
            for (int i=0; i <= msg.length/APDU_MAX_BUFF_SIZE; i++){
				int msg_chunk_length = APDU_MAX_BUFF_SIZE;
				if (msg.length-(i*APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE){
					msg_chunk_length = msg.length-(i*APDU_MAX_BUFF_SIZE);
				}
				if (msg_chunk_length > 0){
					byte[] msg_chunk = new byte[msg_chunk_length];
					System.arraycopy(msg, APDU_MAX_BUFF_SIZE*i, msg_chunk, 0, msg_chunk_length);
					System.out.println(byteArrayToHexString(msg_chunk));
					a = new CommandAPDU(IDENTITY_CARD_CLA, NEW_SERVICE_CERT, 0x00, 0x00, msg_chunk);
					res = con.transmit(a);
					if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
				}
			}
            //SERVICE_CERT_DONE
            a = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CERT_DONE, 0x00, 0x00, new byte[]{0x00});
			res = con.transmit(a);
			if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
			System.out.println("Certificate sent");
            
			
            //step 2 (2) -> (7) verify service certificate + timestamp + challenge from card
			a = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_AUTH, 0x00, 0x00, new byte[]{0x00});
			res = con.transmit(a);
			if(res.getSW()==SW_SIG_NO_MATCH){
				communication.append("Problem with service certificate. Aborting.\n");
				throw new Exception("Problem with service certificate. Aborting.\n");
			}
			//step 2 (3) - catch verify timestamp
			else if(res.getSW()==SW_CERT_EXPIRED){
				communication.append("Service provider certificate expired. Abort.\n");
				throw new Exception("Service provider certificate expired");
			}
			else if(res.getSW()!=0x9000) throw new Exception("Exception..." + res.getSW());
			
			System.out.println("Certificate verified");
			communication.append("Service provider certificate verified\n");
			
			
			//step 2 (8)  Send symmetric key and challenge to service provider
			byte[] sp_auth_response = res.getData();
			providerWriter.println(byteArrayToHexString(sp_auth_response).substring(14)); //send only the data
			//System.out.println(byteArrayToHexString(sp_auth_response));
            //System.out.println("Client: " + byteArrayToHexString(sp_auth_response).substring(14));

            // step 2 (13) send the response to the card
            String serviceResponse = providerReader.readLine();
            System.out.println("Service response: " + serviceResponse);
            a = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_RESP_CHALLENGE, 0x00, 0x00, hexStringToByteArray(serviceResponse));
			res = con.transmit(a);
			if (res.getSW()==SW_ABORT)throw new Exception("Not a correct response, aborting...");
			else if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
			
			communication.append("Service is authenticated to card\n");
			
        }
		
	}
	
	//Step 3
	private String authenticateCard() throws Exception {
		//Step 3 (1)
		String service_chall_response_hex = "";
		int j = 0;
		a = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CHALLENGE, 0x00, 0x00, new byte[]{(byte)j});
		res = con.transmit(a);
		byte[] service_chall_response = res.getData();
		while (Integer.parseInt(byteArrayToHexString(new byte[]{service_chall_response[6]}), 16) > 0){
			System.out.println("response part " + j);
			j++;
			System.out.println(byteArrayToHexString(service_chall_response));
			service_chall_response_hex = service_chall_response_hex + byteArrayToHexString(service_chall_response).substring(14);
			a = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CHALLENGE, 0x00, 0x00, new byte[]{(byte)j});
			res = con.transmit(a);
			service_chall_response = res.getData();
		}
        
		System.out.println("Client: " + service_chall_response_hex);
        providerWriter.println(service_chall_response_hex);
        
        String serviceResponse = providerReader.readLine();
        System.out.println("Server: " + serviceResponse);
        communication.append("Server requests: " + new String(hexStringToByteArray(serviceResponse)) + "\n");
        
        return serviceResponse;
		
	}
	
	//Step 4
	private void releaseAttributes(String serviceResponse) throws Exception {
		// send query to card
		byte[] msg = hexStringToByteArray(serviceResponse);
        for (int i=0; i <= msg.length/APDU_MAX_BUFF_SIZE; i++){
			int msg_chunk_length = APDU_MAX_BUFF_SIZE;
			if (msg.length-(i * APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE){
				msg_chunk_length = msg.length - ( i * APDU_MAX_BUFF_SIZE);
			}
			if (msg_chunk_length > 0){
				byte[] msg_chunk = new byte[msg_chunk_length];
				System.arraycopy(msg, APDU_MAX_BUFF_SIZE*i, msg_chunk, 0, msg_chunk_length);
				System.out.println(byteArrayToHexString(msg_chunk));
				a = new CommandAPDU(IDENTITY_CARD_CLA, NEW_QUERY, 0x00, 0x00, msg_chunk);
				res = con.transmit(a);
				if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
			}
		}
		a = new CommandAPDU(IDENTITY_CARD_CLA, QUERY_DONE, 0x00, 0x00, new byte[]{0x00});
		res = con.transmit(a);
		if (res.getSW()==SW_ABORT){
            System.out.println("Client: " + "Aborting... bad query");
            providerWriter.println("Badquery");
			throw new Exception("Aborted: Bad query from server");
		} else if(res.getSW()!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
		System.out.println("Query sent");
        
		communication.append("PIN required\n");
		
		//get the user's pin code from screen
		JPanel panel = new JPanel();
		JLabel label = new JLabel("Enter your PIN:");
		JPasswordField pass = new JPasswordField(4);
		panel.add(label);
		panel.add(pass);
		String[] options = new String[]{"OK", "Cancel"};
		int option = 1;
		int pin_result = SW_VERIFICATION_FAILED;
		while (option != 0 || pin_result==SW_VERIFICATION_FAILED){
			option = JOptionPane.showOptionDialog(null, panel, "PIN required",
                     JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                     null, options, options[1]);
			if(option == 0) // pressing OK button
			{
			    char[] password = pass.getPassword();
			    System.out.println("PIN: " + new String(password));
			    
			    byte[] pin_array = new byte[4];
			    for (int k=0; k<pin_array.length; k++){
				    pin_array[k] = (byte) Character.getNumericValue(password[k]);
			    }
			    
			    a = new CommandAPDU(IDENTITY_CARD_CLA, VALIDATE_PIN_INS, 0x00, 0x00,pin_array);
				res = con.transmit(a);
				pin_result=res.getSW();
				
				System.out.println(res);
			}
		}
		if(pin_result!=0x9000) throw new Exception("Exception on the card: " + res.getSW());
		System.out.println("PIN Verified");
		communication.append("PIN OK!\n");
		
		//Step 4 (10) - Send query result to sp
		String query = "";
		int j = 0;
		a = new CommandAPDU(IDENTITY_CARD_CLA, GET_QUERY, 0x00, 0x00, new byte[]{(byte)j});
		res = con.transmit(a);
		byte[] query_chunk = res.getData();
		while (Integer.parseInt(byteArrayToHexString(new byte[]{query_chunk[6]}), 16) > 0){
			System.out.println("response part " + j);
			j++;
			System.out.println(byteArrayToHexString(query_chunk));
			query = query + byteArrayToHexString(query_chunk).substring(14);
			a = new CommandAPDU(IDENTITY_CARD_CLA, GET_QUERY, 0x00, 0x00, new byte[]{(byte)j});
			res = con.transmit(a);
			query_chunk = res.getData();
		}
		
        System.out.println("Client: " + query);
        providerWriter.println(query);
        
        System.out.println("Server: " + providerReader.readLine());
        
        communication.append("Data is sent to server. Check the output tab!\n");
	}
	
	private void closeConnectionServiceProvider() throws IOException {
		providerSocket.close();
	}
	
	
	
	//helper functions -----------------------------------------------------------
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
	
