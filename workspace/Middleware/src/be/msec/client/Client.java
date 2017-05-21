package be.msec.client;

import be.msec.client.connection.IConnection;
import be.msec.client.connection.SimulatedConnection;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Date;

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
    private IConnection connectionWithCard;
    private CommandAPDU cardCommandAPDU;
    private ResponseAPDU cardResponseAPDU;

    //UI variables
    private JFrame frame;
    private JTextArea communication;

    //SP connection
    //Socket activeServiceProviderSocket;
    private SSLSocket activeServiceProviderSocket;
    private BufferedReader activeServiceProviderReader;
    private PrintWriter activeServiceProviderWriter;

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        try {
            Client window = new Client();
            window.frame.setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Client() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame();
        frame.setBounds(200, 200, 400, 400);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

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

    private void cardConnect() throws Exception {
        //Simulation:
        connectionWithCard = new SimulatedConnection();

        //Real Card:
        //connectionWithCard = new Connection();
        //((Connection)c).setTerminal(0); //depending on which cardreader you use
        connectionWithCard.connect();

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
            String serviceProviderResponse = authenticateCard();

            //step 4: releaseAttributes()
            releaseAttributes(serviceProviderResponse);

            closeConnectionServiceProvider();
        } catch (Exception e) {
            throw e;
        } finally {
            connectionWithCard.close();  // close the connection with the card
        }
    }

    //step 0
    private void createSelectApplet() throws Exception {
        try {
            //0. create applet (only for simulator!!!)
            cardCommandAPDU = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, new byte[]{(byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x08, 0x01}, 0x7f);
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            System.out.println(cardResponseAPDU);
            if (cardResponseAPDU.getSW() != 0x9000) throw new Exception("select installer applet failed");

            cardCommandAPDU = new CommandAPDU(0x80, 0xB8, 0x00, 0x00, new byte[]{0xb, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x00, 0x00, 0x00}, 0x7f);
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            System.out.println(cardResponseAPDU);
            if (cardResponseAPDU.getSW() != 0x9000) throw new Exception("Applet creation failed");

            //1. Select applet  (not required on cardCommandAPDU real card, applet is selected by default)
            cardCommandAPDU = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x00, 0x00}, 0x7f);
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            System.out.println(cardResponseAPDU);
            if (cardResponseAPDU.getSW() != 0x9000) throw new Exception("Applet selection failed");

        } catch (Exception e) {
            throw e;
        }
    }

    //step 1
    private void updateTime() throws Exception {
        communication.append("Connecting to card - inserted in reader\n");

        //step 1 (1) Hello", send currentTime to card
        Date date = new Date();
        Long timestamp = date.getTime() / 1000;
        cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, HELLO_DIS, 0x00, 0x00, longToBytes(timestamp));
        cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
        System.out.println(cardResponseAPDU);

        //receive reqRevalidation
        if (cardResponseAPDU.getSW() == SW_REQ_REVALIDATION) {
            communication.append("RevalidationRequest: new timestamp required from Government server\n");

            byte[] new_time = getNewTimeFromGov();

            if (new_time != null) {
                //step 1 (9) update time on card
                cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, NEW_TIME, 0x00, 0x00, new_time);
                cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
                System.out.println(cardResponseAPDU);
                if (cardResponseAPDU.getSW() == SW_ABORT) throw new Exception("Aborted: Cannot update time");
                else if (cardResponseAPDU.getSW() != 0x9000)
                    throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());

                communication.append("RevalidationRequest: new timestamp is saved on the card\n");
            }

            //****only for testing the code: extra send hello to the card with the same timestamp
            //cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, HELLO_DIS, 0x00, 0x00,longToBytes(timestamp));
            //cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            //if (cardResponseAPDU.getSW()!=SW_REQ_REVALIDATION) {
            //	communication.append("NEW RevalidationRequest is false now\n");
            //}
            else if (cardResponseAPDU.getSW() != 0x9000)
                throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());

        } else { //goto step 2 : authenticateServiceProvider
            communication.append("RevalidationRequest: false");
        }
    }

    //step 1 (7)
    private byte[] getNewTimeFromGov() {
        //socket connection to Government
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        //System.setProperty("javax.net.ssl.trustStore", "src/belgianeid.jks");
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            System.setProperty("javax.net.ssl.trustStore", "src/belgianeidsha1.jks");
        }
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

        //SSL socket connection
        SSLSocketFactory socketFactory = ((SSLSocketFactory) SSLSocketFactory.getDefault());
        SSLSocket sslsocket;
        try {
            sslsocket = (SSLSocket) socketFactory.createSocket("127.0.0.1", 4444);
            InputStream inputStream = sslsocket.getInputStream();
            OutputStream outputStream = sslsocket.getOutputStream();

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            PrintWriter printWriter = new PrintWriter(outputStream, true);

            //System.out.print("Sending message to server: ");
            printWriter.println(MSG_GET_TIME);

            System.out.print("Message reply from server: ");

            String msgFromGServer = bufferedReader.readLine();
            if (msgFromGServer.equalsIgnoreCase("Abort")) {
                communication.append("Error in timeserver\n");
                try {
                    throw new Exception("Error in timeserver");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Received: " + msgFromGServer);
                communication.append(msgFromGServer + "\n");
                return hexStringToByteArray(msgFromGServer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    //step 2
    private void initConnectionServiceProvider() throws IOException {
        //connection to the SP
//		activeServiceProviderSocket = new Socket("127.0.0.1", 8888);
//		
//		InputStream providerInputStream = activeServiceProviderSocket.getInputStream();
//		OutputStream providerOutputStream = activeServiceProviderSocket.getOutputStream();
//		InputStreamReader providerInputStreamReader = new InputStreamReader(providerInputStream);

        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            System.setProperty("javax.net.ssl.trustStore", "src/belgianeidsha1.jks");
        }
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

        //SSL socket connection
        SSLSocketFactory socketFactory = ((SSLSocketFactory) SSLSocketFactory.getDefault());

        activeServiceProviderSocket = (SSLSocket) socketFactory.createSocket("127.0.0.1", 8888);
        InputStream providerInputStream = activeServiceProviderSocket.getInputStream();
        OutputStream providerOutputStream = activeServiceProviderSocket.getOutputStream();
        InputStreamReader providerInputStreamReader = new InputStreamReader(providerInputStream);

        activeServiceProviderReader = new BufferedReader(providerInputStreamReader);
        activeServiceProviderWriter = new PrintWriter(providerOutputStream, true);
    }

    private void authenticateServiceProvider() throws Exception {
        String certificateMessage = activeServiceProviderReader.readLine();
        if (certificateMessage.equalsIgnoreCase("Abort")) {
            communication.append("Error in connection with service provider\n");
            try {
                throw new Exception("Error in service provider");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            //System.out.println("Received: " + certificateMessage);
            //step 2(1) send certificate to card
            byte[] msg = hexStringToByteArray(certificateMessage);
            for (int i = 0; i <= msg.length / APDU_MAX_BUFF_SIZE; i++) {
                int msg_chunk_length = APDU_MAX_BUFF_SIZE;
                if (msg.length - (i * APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE) {
                    msg_chunk_length = msg.length - (i * APDU_MAX_BUFF_SIZE);
                }
                if (msg_chunk_length > 0) {
                    byte[] msg_chunk = new byte[msg_chunk_length];
                    System.arraycopy(msg, APDU_MAX_BUFF_SIZE * i, msg_chunk, 0, msg_chunk_length);
                    System.out.println(byteArrayToHexString(msg_chunk));
                    cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, NEW_SERVICE_CERT, 0x00, 0x00, msg_chunk);
                    cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
                    if (cardResponseAPDU.getSW() != 0x9000)
                        throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());
                }
            }
            //SERVICE_CERT_DONE
            cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CERT_DONE, 0x00, 0x00, new byte[]{0x00});
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            if (cardResponseAPDU.getSW() != 0x9000)
                throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());
            System.out.println("Certificate sent");


            //step 2 (2) -> (7) verify service certificate + timestamp + challenge from card
            cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_AUTH, 0x00, 0x00, new byte[]{0x00});
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            if (cardResponseAPDU.getSW() == SW_SIG_NO_MATCH) {
                communication.append("Problem with service certificate. Aborting.\n");
                throw new Exception("Problem with service certificate. Aborting.\n");
            }
            //step 2 (3) - catch verify timestamp
            else if (cardResponseAPDU.getSW() == SW_CERT_EXPIRED) {
                communication.append("Service provider certificate expired. Abort.\n");
                throw new Exception("Service provider certificate expired");
            } else if (cardResponseAPDU.getSW() != 0x9000)
                throw new Exception("Exception..." + cardResponseAPDU.getSW());

            System.out.println("Certificate verified");
            communication.append("Service provider certificate verified\n");


            //step 2 (8)  Send symmetric key and challenge to service provider
            byte[] sp_auth_response = cardResponseAPDU.getData();
            activeServiceProviderWriter.println(byteArrayToHexString(sp_auth_response).substring(14)); //send only the data
            //System.out.println(byteArrayToHexString(sp_auth_response));
            //System.out.println("Client: " + byteArrayToHexString(sp_auth_response).substring(14));

            // step 2 (13) send the response to the card
            String serviceResponse = activeServiceProviderReader.readLine();
            System.out.println("Service response: " + serviceResponse);
            cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_RESP_CHALLENGE, 0x00, 0x00, hexStringToByteArray(serviceResponse));
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            if (cardResponseAPDU.getSW() == SW_ABORT)
                throw new Exception("Not cardCommandAPDU correct response, aborting...");
            else if (cardResponseAPDU.getSW() != 0x9000)
                throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());

            communication.append("Service is authenticated to card\n");

        }

    }

    //Step 3
    private String authenticateCard() throws Exception {
        //Step 3 (1)
        String service_chall_response_hex = "";
        int j = 0;
        cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CHALLENGE, 0x00, 0x00, new byte[]{(byte) j});
        cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
        byte[] service_chall_response = cardResponseAPDU.getData();
        while (Integer.parseInt(byteArrayToHexString(new byte[]{service_chall_response[6]}), 16) > 0) {
            System.out.println("response part " + j);
            j++;
            System.out.println(byteArrayToHexString(service_chall_response));
            service_chall_response_hex = service_chall_response_hex + byteArrayToHexString(service_chall_response).substring(14);
            cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, SERVICE_CHALLENGE, 0x00, 0x00, new byte[]{(byte) j});
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            service_chall_response = cardResponseAPDU.getData();
        }

        System.out.println("Client: " + service_chall_response_hex);
        activeServiceProviderWriter.println(service_chall_response_hex);

        String serviceProviderResponse = activeServiceProviderReader.readLine();
        System.out.println("Server: " + serviceProviderResponse);
        if ("Abort".equals(serviceProviderResponse)) {
            throw new Exception("Service provider refused to authenticate the card!");
        }
        communication.append("Service Provider response: " + new String(hexStringToByteArray(serviceProviderResponse)) + "\n");

        return serviceProviderResponse;

    }

    //Step 4
    private void releaseAttributes(String serviceResponse) throws Exception {
        // send query to card
        byte[] msg = hexStringToByteArray(serviceResponse);
        for (int i = 0; i <= msg.length / APDU_MAX_BUFF_SIZE; i++) {
            int msg_chunk_length = APDU_MAX_BUFF_SIZE;
            if (msg.length - (i * APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE) {
                msg_chunk_length = msg.length - (i * APDU_MAX_BUFF_SIZE);
            }
            if (msg_chunk_length > 0) {
                byte[] msg_chunk = new byte[msg_chunk_length];
                System.arraycopy(msg, APDU_MAX_BUFF_SIZE * i, msg_chunk, 0, msg_chunk_length);
                System.out.println(byteArrayToHexString(msg_chunk));
                cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, NEW_QUERY, 0x00, 0x00, msg_chunk);
                cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
                if (cardResponseAPDU.getSW() != 0x9000)
                    throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());
            }
        }
        cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, QUERY_DONE, 0x00, 0x00, new byte[]{0x00});
        cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
        if (cardResponseAPDU.getSW() == SW_ABORT) {
            System.out.println("Client: " + "Aborting... bad query");
            activeServiceProviderWriter.println("Badquery");
            throw new Exception("Aborted: Bad query from server");
        } else if (cardResponseAPDU.getSW() != 0x9000)
            throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());
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
        while (option != 0 || pin_result == SW_VERIFICATION_FAILED) {
            option = JOptionPane.showOptionDialog(null, panel, "PIN required",
                    JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[1]);
            if (option == 0) // pressing OK button
            {
                char[] password = pass.getPassword();
                System.out.println("PIN: " + new String(password));

                byte[] pin_array = new byte[4];
                for (int k = 0; k < pin_array.length; k++) {
                    pin_array[k] = (byte) Character.getNumericValue(password[k]);
                }

                cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, VALIDATE_PIN_INS, 0x00, 0x00, pin_array);
                cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
                pin_result = cardResponseAPDU.getSW();

                System.out.println(cardResponseAPDU);
            }
        }
        if (pin_result != 0x9000) throw new Exception("Exception on the card: " + cardResponseAPDU.getSW());
        System.out.println("PIN Verified");
        communication.append("PIN OK!\n");

        //Step 4 (10) - Send query result to sp
        String query = "";
        int j = 0;
        cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, GET_QUERY, 0x00, 0x00, new byte[]{(byte) j});
        cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
        byte[] query_chunk = cardResponseAPDU.getData();
        while (Integer.parseInt(byteArrayToHexString(new byte[]{query_chunk[6]}), 16) > 0) {
            System.out.println("response part " + j);
            j++;
            System.out.println(byteArrayToHexString(query_chunk));
            query = query + byteArrayToHexString(query_chunk).substring(14);
            cardCommandAPDU = new CommandAPDU(IDENTITY_CARD_CLA, GET_QUERY, 0x00, 0x00, new byte[]{(byte) j});
            cardResponseAPDU = connectionWithCard.transmit(cardCommandAPDU);
            query_chunk = cardResponseAPDU.getData();
        }

        System.out.println("Client: " + query);
        activeServiceProviderWriter.println(query);

        System.out.println("Server: " + activeServiceProviderReader.readLine());

        communication.append("Data is sent to server. Check the output tab!\n");
    }

    private void closeConnectionServiceProvider() throws IOException {
        activeServiceProviderSocket.close();
    }


    //helper functions -----------------------------------------------------------
    //byte array from hex string
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static String byteArrayToHexString(byte[] bytes) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / 8);
        buffer.putLong(x);
        return buffer.array();
    }

}

