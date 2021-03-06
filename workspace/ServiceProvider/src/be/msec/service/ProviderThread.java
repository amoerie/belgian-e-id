package be.msec.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ProviderThread extends Thread {
    private ServerSocket serverSocket;
    private String domain;
    private String service;

    //different states for treated messages
    private static final int INITIALIZE = 0;
    private static final int SENT_CERTIFICATE_WAITING_FOR_CARD_CHALLENGE = 1; // on client socket connection -> certificate is send to M
    private static final int ANSWERED_CARD_CHALLENGE = 2; // receiving symmetric key + message --> decrypt + send his challenge to M
    private static final int SEND_DATA_REQUEST = 3; // receiving attributes --> send query data request to M
    private static final int FINAL = 4; // receiving attributes from M
    private int state = INITIALIZE;

    ProviderThread(ServerSocket serverSocket, String domain, String service) {
        this.serverSocket = serverSocket;
        this.domain = domain;
        this.service = service;
    }

    //MESSAGES from M -> SP
    private final static String MSG_RESULT = "MessageResult";

    //variables
    private PrintWriter middlewareWriter;
    private BufferedReader middlewareReader;

    //certificate
    //sha256
    //private static String store_location = "src/belgianeid.jks";
    //sha1
    private static RSAPrivateCrtKey service_key;
    private static X509Certificate service_cert;
    private static X509Certificate ca_cert;
    //private static X509Certificate common_cert_test;
    private static byte[] service_cert_bytes;

    private SecretKeySpec my_symm_key;
    private byte[] server_challenge;

    private final static int LENGTH_RSA_512_BYTES = 512 / 8;
    private final static int LENGTH_AES_128_BYTES = 128 / 8;
    byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    public void run() {

        try {

            Socket middlewareSocket = serverSocket.accept();

            middlewareWriter = new PrintWriter(middlewareSocket.getOutputStream(), true);
            middlewareReader = new BufferedReader(new InputStreamReader(middlewareSocket.getInputStream()));

            Provider.logging.setText(Provider.logging.getText() + "\nClientSocket ready...");

            //step 2(1) - send certificate to client
            sendCertificate();
            state = SENT_CERTIFICATE_WAITING_FOR_CARD_CHALLENGE;

            String message = null;
            while ((message = middlewareReader.readLine()) != null) {

                Provider.logging.setText(Provider.logging.getText() + "\nMessage received");

                //if (message.equals(MSG_RESULT)) {
                String output = treatMessage(message);
                middlewareWriter.println(output);
                //}
                //if other message like abort, etc...
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendCertificate() throws FileNotFoundException {

        //get certificate from store
        try {
            getCertificate();
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | CertificateException | IOException e1) {
            e1.printStackTrace();
        }

        //send certificate to client (M)
        middlewareWriter.println(byteArrayToHexString(service_cert_bytes));
    }


    private void getCertificate() throws NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
        KeyStore store;
        try {
            store = KeyStore.getInstance("JKS");
            String keyStore = System.getProperty("javax.net.ssl.keyStore");
            char[] keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword").toCharArray();
            FileInputStream stream = new FileInputStream(keyStore);
            store.load(stream, keyStorePassword);
            stream.close();
            service_key = (RSAPrivateCrtKey) store.getKey(service, keyStorePassword);
            service_cert = (X509Certificate) store.getCertificate(service);
            service_cert_bytes = service_cert.getEncoded();
            ca_cert = (X509Certificate) store.getCertificate("ca");
            //common_cert_test = (X509Certificate) store.getCertificate("common");
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
    }

    private String treatMessage(String message) {
        try {
            switch (state) {
                case SENT_CERTIFICATE_WAITING_FOR_CARD_CHALLENGE: // STEP 2: authenticateServiceProvider
                    //Already done: on client socket connection -> certificate is send to M
                    //Now: first message is the receiving of the symmetric key and challenge from the card
                    return treatSendCertificate(message);
                case ANSWERED_CARD_CHALLENGE: // STEP 2 : authenticateServiceProvider
                    //Already done: Card has generated a challenge
                    //Now: receiving symmetric key + message --> decrypt + send his challenge to M
                    return treatAnsweredChallenge(message);
                case SEND_DATA_REQUEST: // STEP 4 : releaseAttributes
                    //Already done:
                    //BUSY: receiving challenge --> send query data request to M
                    return treatSendDataRequest(message);
                case FINAL: // STEP 4: receiveAttributes

                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String treatSendCertificate(String message) throws NoSuchAlgorithmException, NoSuchPaddingException
            , InvalidKeyException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        String result;

        if (message.length() > 8) { // at least 2 length indicators and 2 data bytes
            //break the input apart
            byte[] input_bytes = hexStringToByteArray(message);
            byte[] last_symm_key_encrypted = new byte[input_bytes[0]];
            byte[] last_symm_key_decrypted = new byte[LENGTH_RSA_512_BYTES];
            byte[] last_symm_key = new byte[LENGTH_AES_128_BYTES];
            byte[] challenge_with_subject_encrypted = new byte[input_bytes[1 + input_bytes[0]]];
            byte[] challenge_with_subject = new byte[input_bytes[1 + input_bytes[0]]];
            System.arraycopy(input_bytes, 1, last_symm_key_encrypted, 0, last_symm_key_encrypted.length);
            System.arraycopy(input_bytes, last_symm_key_encrypted.length + 2, challenge_with_subject_encrypted, 0, challenge_with_subject_encrypted.length);

            //decrypt symmetric key
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, service_key);
            cipher.doFinal(last_symm_key_encrypted, 0, last_symm_key_encrypted.length, last_symm_key_decrypted, 0);
            System.arraycopy(last_symm_key_decrypted, 0, last_symm_key, 0, last_symm_key.length);
            System.out.println("received this symm key: " + byteArrayToHexString(last_symm_key));
            my_symm_key = new SecretKeySpec(last_symm_key, "AES");

            //decrypt challenge and subject
            cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, my_symm_key, new IvParameterSpec(iv));
            cipher.doFinal(challenge_with_subject_encrypted, 0, challenge_with_subject_encrypted.length, challenge_with_subject, 0);
            byte[] last_challenge = new byte[challenge_with_subject[0]];
            byte[] last_subject = new byte[challenge_with_subject[last_challenge.length + 1]];
            System.arraycopy(challenge_with_subject, 1, last_challenge, 0, last_challenge.length);
            System.arraycopy(challenge_with_subject, last_challenge.length + 2, last_subject, 0, last_subject.length);

            System.out.println("received this challenge: " + byteArrayToHexString(last_challenge));
            System.out.println("received this subject: " + new String(last_subject));
            System.out.println("received this compare with: " + service);

            String last_subject_string = new String(last_subject);

            if (last_subject_string.compareToIgnoreCase(service) == 0) {
                //subject is OK, answer challenge and generate own challenge
                long challenge_long = Long.parseLong(byteArrayToHexString(last_challenge), 16);
                challenge_long += 1;
                byte[] challenge_long_bytes_temp = longToBytes(challenge_long);
                byte[] challenge_long_bytes = new byte[2];
                System.arraycopy(challenge_long_bytes_temp, challenge_long_bytes_temp.length - 2, challenge_long_bytes, 0, challenge_long_bytes.length);
                byte[] challenge_resp_bytes = new byte[LENGTH_AES_128_BYTES];
                challenge_resp_bytes[0] = (byte) challenge_long_bytes.length;
                System.arraycopy(challenge_long_bytes, 0, challenge_resp_bytes, 1, challenge_long_bytes.length);

                SecureRandom random = new SecureRandom();
                server_challenge = new byte[2];
                random.nextBytes(server_challenge);
                challenge_resp_bytes[1 + challenge_long_bytes.length] = (byte) server_challenge.length;
                System.arraycopy(server_challenge, 0, challenge_resp_bytes, 1 + challenge_long_bytes.length + 1, server_challenge.length);
                System.out.println("Sending challenge to card: " + byteArrayToHexString(server_challenge));
                cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, my_symm_key, new IvParameterSpec(iv));
                byte[] challenge_resp_encrypted = new byte[LENGTH_AES_128_BYTES];
                cipher.doFinal(challenge_resp_bytes, 0, challenge_resp_bytes.length, challenge_resp_encrypted, 0);
                System.out.println("response to challenge: " + byteArrayToHexString(challenge_resp_bytes));
                System.out.println("encrypted response to challenge: " + byteArrayToHexString(challenge_resp_encrypted));

                result = byteArrayToHexString(challenge_resp_encrypted);
                state = ANSWERED_CARD_CHALLENGE;
            } else {
                result = "Abort";
                state = INITIALIZE;
            }

        } else {
            result = "Abort";
        }

        return result;
    }

    private String treatAnsweredChallenge(String message) throws CertificateException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException
            , InvalidAlgorithmParameterException, ShortBufferException, IllegalBlockSizeException, BadPaddingException, SignatureException {
        StringBuilder result;

        if (message.length() > 8) {
            //Decrypt data with symmetric key
            byte[] msg_bytes = hexStringToByteArray(message);
            byte[] msg_decrypted = new byte[msg_bytes.length];
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, my_symm_key, new IvParameterSpec(iv));
            cipher.doFinal(msg_bytes, 0, msg_bytes.length, msg_decrypted, 0);
            System.out.println("Step 3 - (8) received message: " + byteArrayToHexString(msg_decrypted));

            //extract challenge response and certificate
            byte[] service_challenge_resp = new byte[msg_decrypted[0]];
            System.arraycopy(msg_decrypted, 1, service_challenge_resp, 0, service_challenge_resp.length);
            int cert_length = (msg_decrypted.length - 1 - service_challenge_resp.length - 1 - (int) msg_decrypted[1 + service_challenge_resp.length]);
            byte[] common_cert_temp = new byte[cert_length];
            //input, inputPos, output, outputPos, length!!
            System.arraycopy(msg_decrypted, service_challenge_resp.length + 2, common_cert_temp, 0, common_cert_temp.length);
            System.out.println("common certificate: " + byteArrayToHexString(common_cert_temp));
            System.out.println("cert length is " + common_cert_temp.length);

            //verify certificate signature, date and subject
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            InputStream in = new ByteArrayInputStream(common_cert_temp);
            X509Certificate common_cert = (X509Certificate) certFactory.generateCertificate(in);
            System.out.println(common_cert.toString());

            Signature cert_sig = Signature.getInstance("SHA1withRSA");
            cert_sig.initVerify(ca_cert.getPublicKey());
            cert_sig.update(common_cert.getTBSCertificate());
            System.out.println("Verifying that card has correct common certificate");
            boolean cert_verify = cert_sig.verify(common_cert.getSignature());
            System.out.println("Card certificate is valid: " + cert_verify);

            
            RSAPublicKey pub = (RSAPublicKey) common_cert.getPublicKey();
            System.out.println(pub.getModulus().toString(16));
            System.out.println(pub.getPublicExponent().toString(16));
            
            Date new_date = new Date();
            boolean sig_verify = false;
            if (new_date.after(common_cert.getNotBefore())
                    && new_date.before(common_cert.getNotAfter())
                    && common_cert.getSubjectDN().getName().equals("OID.0.9.2342.19200300.100.4.13=common, CN=COMMON, O=VUB, L=Brussels, ST=Brussels, C=BE")
                    && cert_verify) {
                // verify signature on challenge with pk from common certificate
            	//https://msec.be/wiscy/seminarie/ho_sc.pdf
                System.out.println("Verifying that card has signed the challenge correctly: " + byteArrayToHexString(server_challenge));
                Signature sig = Signature.getInstance("SHA1withRSA");
                sig.initVerify(common_cert.getPublicKey());
                //sig.initVerify(common_cert_test.getPublicKey());
                sig.update(server_challenge);
				sig_verify = sig.verify(service_challenge_resp);

                System.out.println("Card has correctly signed challenge:  " + sig_verify);
            }

            //send query for card data.
            List<String> listAskedFields = new ArrayList<String>();
            if (Provider.chckbxNym.isSelected())
                listAskedFields.add("nym");
            if (Provider.chckbxName.isSelected())
                listAskedFields.add("name");
            if (Provider.chckbxAddress.isSelected())
                listAskedFields.add("address");
            if (Provider.chckbxCountry.isSelected())
                listAskedFields.add("country");
            if (Provider.chckbxBirthdate.isSelected())
                listAskedFields.add("birthdate");
            if (Provider.chckbxAge.isSelected())
                listAskedFields.add("age");
            if (Provider.chckbxGender.isSelected())
                listAskedFields.add("gender");
            if (Provider.chckbxPicture.isSelected())
                listAskedFields.add("picture");
            String[] arrayOfAskedFields = listAskedFields.toArray(new String[listAskedFields.size()]);

            if (sig_verify) {
                result = new StringBuilder();
                for (String askedField : arrayOfAskedFields) {
                    System.out.println(askedField);
                    byte[] req = askedField.getBytes();
                    result.append(byteArrayToHexString(new byte[]{(byte) req.length})).append(byteArrayToHexString(req));
                }
                state = SEND_DATA_REQUEST;
            } else {
                result = new StringBuilder("Abort");
                state = INITIALIZE;
            }

            //} else {
            //result = "Abort";
            //state = INITIALIZE;
            //}

        } else {
            result = new StringBuilder("Abort");
            state = INITIALIZE;
        }

        return result.toString();
    }

    private String treatSendDataRequest(String message) throws NoSuchAlgorithmException, NoSuchPaddingException
            , InvalidKeyException, InvalidAlgorithmParameterException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        String result;

        if (message.equalsIgnoreCase("Bad query")) {
            result = "Abort.";
            state = INITIALIZE;
        } else {
            //decrypt the results
            byte[] input_bytes = hexStringToByteArray(message);
            byte[] decrypted_input = new byte[input_bytes.length];
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, my_symm_key, new IvParameterSpec(iv));
            cipher.doFinal(input_bytes, 0, input_bytes.length, decrypted_input, 0);
            //parse the results to the screen
            Provider.output.setText("");
            int i = 0;
            while (i < decrypted_input.length && (int) decrypted_input[i] > 0) {
                int l = (int) decrypted_input[i];
                byte[] param = new byte[l];
                System.arraycopy(decrypted_input, i + 1, param, 0, param.length);
                l = (int) decrypted_input[i + param.length + 1];
                byte[] val = new byte[l];
                System.arraycopy(decrypted_input, i + 1 + param.length + 1, val, 0, val.length);
                i += 2;
                i += param.length;
                i += val.length;

                Provider.output.setText(Provider.output.getText() + new String(param) + ": " + new String(val) + "\n");
            }

            result = "Bye.";
            state = FINAL;
        }
        return result;

    }

    //helper functions --------------------------------------
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
