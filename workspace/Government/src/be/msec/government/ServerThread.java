package be.msec.government;
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

public class ServerThread extends Thread {
	Socket socket;
	ServerThread(Socket socket) {
		this.socket = socket;
	}
	
	//MESSAGES from M -> G
	private final static String MSG_GET_TIME = "RevalidationRequest";
		
	//certificate                               
	private static String store_location = "src/belgianeid.jks";
	private static char[] mypass = "123456".toCharArray();
	private static RSAPrivateCrtKey my_key;
	private static X509Certificate my_cert;
	private static byte[] my_cert_bytes;
	
	
	public void run() {
		
		try {
			getCertificate();
		} catch (UnrecoverableKeyException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (CertificateException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			PrintWriter outputWriter = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            
			String message = null;
			while ((message = inputReader.readLine()) != null) {
				if (message.equals(MSG_GET_TIME)) {
					try {
						outputWriter.println(byteArrayToHexString(getRevalidationRequest()));
					} catch (InvalidKeyException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (SignatureException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			    //System.out.println("Received: " + message);
			    //outputWriter.println("ECHO: " + message);
			}
			//string = inputReader.readLine();
			//System.out.println("Received: " + string);
			//outputWriter.println("ECHO: " + string);
			
		} catch(IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private void getCertificate() throws NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
		KeyStore store;
		try {
			store = KeyStore.getInstance("JKS");
			FileInputStream fis = new FileInputStream(store_location);
			store.load(fis, mypass);
			fis.close();
			my_key = (RSAPrivateCrtKey) store.getKey("gov", mypass);
			my_cert = (X509Certificate) store.getCertificate("gov");
			my_cert_bytes = my_cert.getEncoded();
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private static byte[] getRevalidationRequest() throws SignatureException, InvalidKeyException, NoSuchAlgorithmException{
		//make timestamp
		Date date = new Date();
		Long timestamp = date.getTime()/1000;
		byte[] timestamp_bytes = longToBytes(timestamp);
		
		//make timestring
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMddHHmmss");
		String timestring = formatter.format(date);
		byte[] timestring_bytes = timestring.getBytes();
		
		//sign timestamp + timestring
		byte[] time_bytes = new byte[timestamp_bytes.length + timestring_bytes.length];
		System.arraycopy(timestamp_bytes, (short)0, time_bytes, (short)0, (short)timestamp_bytes.length);
		System.arraycopy(timestring_bytes, (short)0, time_bytes, (short)timestamp_bytes.length, (short)timestring_bytes.length);
		
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(my_key);
		signature.update(time_bytes);
		byte[] timesig_bytes = signature.sign();
		
		// result
		byte[] res = new byte[1 + timestamp_bytes.length + 1 + timestring_bytes.length + 1 + timesig_bytes.length];
		res[0] = (byte) timestamp_bytes.length;
		System.arraycopy(timestamp_bytes, (short)0, res, (short)1, (short)timestamp_bytes.length);
		res[1 + timestamp_bytes.length] = (byte) timestring_bytes.length;
		System.arraycopy(timestring_bytes, (short)0, res, (short)(1 + timestamp_bytes.length + 1), (short)timestring_bytes.length);
		res[1 + timestamp_bytes.length + 1 + timestring_bytes.length] = (byte) timesig_bytes.length;
		System.arraycopy(timesig_bytes, (short)0, res, (short)(1 + timestamp_bytes.length + 1 + timestring_bytes.length + 1), (short)timesig_bytes.length);

		return res;
	}
	
	
	//helper functions --------------------------------------
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
