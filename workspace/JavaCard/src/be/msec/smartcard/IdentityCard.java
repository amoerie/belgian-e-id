package be.msec.smartcard;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.*;
import javacardx.crypto.*;

public class IdentityCard extends Applet {
	private final static byte IDENTITY_CARD_CLA = (byte) 0x80;

	private static final byte VALIDATE_PIN_INS = 0x22;
	private static final byte GET_SERIAL_INS = 0x24;

	private final static byte PIN_TRY_LIMIT = (byte) 0x03;
	private final static byte PIN_SIZE = (byte) 0x04;

	private final static short SW_VERIFICATION_FAILED = 0x6300;
	private final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;

	// from M -> SC
	private static final byte HELLO_DIS = 0x40;
	private static final byte NEW_TIME = 0x41;

	// from SC -> M
	private final static short SW_ABORT = 0x6339;
	private final static short SW_REQ_REVALIDATION = 0x6340;

	// timestamps
	byte[] lastValidationTime = new byte[] { 0x00 }; // (20)000101000000
	byte[] lastValidationTimeString = hexStringToByteArray("303030313031303030303030"); // (20)000101000000
	private final static long VALIDATION_TIME = 24 * 60 * 60;
	private final static byte TIMESTAMP_SIZE = (byte) 0x12;

	
	//certificates
	private String gov_cert_hex;
	private byte[] gov_cert_bytes;
	private RSAPublicKey gov_pk = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_512, false);

	
	
	
	private byte[] serial = new byte[] { 0x30, 0x35, 0x37, 0x36, 0x39, 0x30, 0x31, 0x05 };
	private OwnerPIN pin;

	private IdentityCard() {
		System.out.println("IdentityCard");

		/*
		 * During instantiation of the applet, all objects are created. In this
		 * example, this is the 'pin' object.
		 */
		pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_SIZE);
		pin.update(new byte[] { 0x01, 0x02, 0x03, 0x04 }, (short) 0, PIN_SIZE);

		// TODO: certificates and common private key
		//??? to check
		gov_cert_hex = "308201463081F10202101D300D06092A864886F70D0101050500302B3113301106035504030C0A476F7665726E6D656E7431143012060A0992268993F22C64040D0C04526F6F74301E170D3137303430393038323132395A170D3138303431393038323132395A30313113301106035504030C0A54696D65536572766572311A3018060A0992268993F22C64040D0C0A54696D65536572766572305C300D06092A864886F70D0101010500034B003048024100B9FA9461841DB13BF6AEE5F5D84AD3025D76EF094CF0FB99292A71FE64E9533878A932CE6A9411485C04B79F07255A890FEDB271AA8F34EB217801F1C6FEDA330203010001300D06092A864886F70D0101050500034100AB09931481C691FDBC04116D011DACF013D85ED7B5801991ADF0B4D1130A785A79698421C1E9FF1B421EBC274ADC78A89A62962D960BA7E914FE905FC03D53FE";
		gov_cert_bytes = hexStringToByteArray(gov_cert_hex);
		
		
		// TODO: byte arrays with modulus and exponents
		

		// TODO: make keys
		//gov_pk.setExponent(gov_exp_pub_bytes, (short)0, (short)gov_exp_pub_bytes.length);
		//gov_pk.setModulus(gov_modulus_bytes, (short)0, (short)gov_modulus_bytes.length);
		

		/*
		 * This method registers the applet with the JCRE on the card.
		 */
		register();
	}

	/*
	 * This method is called by the JCRE when installing the applet on the card.
	 */
	public static void install(byte bArray[], short bOffset, byte bLength) throws ISOException {
		new IdentityCard();
	}

	/*
	 * If no tries are remaining, the applet refuses selection. The card can,
	 * therefore, no longer be used for identification.
	 */
	public boolean select() {
		if (pin.getTriesRemaining() == 0)
			return false;
		return true;
	}

	/*
	 * This method is called when the applet is selected and an APDU arrives.
	 */
	// it is always waiting on requests
	// you can not use new
	// you can only use static!!!
	public void process(APDU apdu) throws ISOException {
		System.out.println("process");

		// A reference to the buffer, where the APDU data is stored, is
		// retrieved.
		byte[] buffer = apdu.getBuffer();

		// If the APDU selects the applet, no further processing is required.
		if (this.selectingApplet())
			return;

		// Check whether the indicated class of instructions is compatible with
		// this applet.
		if (buffer[ISO7816.OFFSET_CLA] != IDENTITY_CARD_CLA)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		// A switch statement is used to select a method depending on the
		// instruction
		switch (buffer[ISO7816.OFFSET_INS]) {
		case VALIDATE_PIN_INS:
			validatePIN(apdu);
			break;
		case GET_SERIAL_INS:
			getSerial(apdu);
			break;

		// step1
		case HELLO_DIS:
			validateHello(apdu);
			break;
		case NEW_TIME:
			newTime(apdu);
			break;

		// If no matching instructions are found it is indicated in the status
		// word of the response.
		// given that indicates
		// the type of warning. There are several predefined warnings in the
		// 'ISO7816' class.
		default:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

	/*
	 * This method is used to authenticate the owner of the card using a PIN
	 * code.
	 */
	private void validatePIN(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		// The input data needs to be of length 'PIN_SIZE'.
		// Note that the byte values in the Lc and Le fields represent values
		// between
		// 0 and 255. Therefore, if a short representation is required, the
		// following
		// code needs to be used: short Lc = (short) (buffer[ISO7816.OFFSET_LC]
		// & 0x00FF);
		if (buffer[ISO7816.OFFSET_LC] == PIN_SIZE) {
			// This method is used to copy the incoming data in the APDU buffer.
			apdu.setIncomingAndReceive();
			// Note that the incoming APDU data size may be bigger than the APDU
			// buffer
			// size and may, therefore, need to be read in portions by the
			// applet.
			// Most recent smart cards, however, have buffers that can contain
			// the maximum
			// data size. This can be found in the smart card specifications.
			// If the buffer is not large enough, the following method can be
			// used:
			//
			// byte[] buffer = apdu.getBuffer();
			// short bytesLeft = (short) (buffer[ISO7816.OFFSET_LC] & 0x00FF);
			// Util.arrayCopy(buffer, START, storage, START, (short)5);
			// short readCount = apdu.setIncomingAndReceive();
			// short i = ISO7816.OFFSET_CDATA;
			// while ( bytesLeft > 0){
			// Util.arrayCopy(buffer, ISO7816.OFFSET_CDATA, storage, i,
			// readCount);
			// bytesLeft -= readCount;
			// i+=readCount;
			// readCount = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
			// }
			if (pin.check(buffer, ISO7816.OFFSET_CDATA, PIN_SIZE) == false)
				ISOException.throwIt(SW_VERIFICATION_FAILED);
		} else
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
	}

	/*
	 * This method checks whether the user is authenticated and sends the
	 * identity file.
	 */
	private void getSerial(APDU apdu) {
		// If the pin is not validated, a response APDU with the
		// 'SW_PIN_VERIFICATION_REQUIRED' status word is transmitted.
		if (!pin.isValidated())
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		else {
			// This sequence of three methods sends the data contained in
			// 'identityFile' with offset '0' and length 'identityFile.length'
			// to the host application.
			apdu.setOutgoing(); // you can not allocate memory, you must used
								// your input, it clears your APDU
			apdu.setOutgoingLength((short) serial.length); // 0 if you don't
															// want to send
															// something, the
															// lenght of the
															// data
			apdu.sendBytesLong(serial, // array that you want to send
					(short) 0, // offset
					(short) serial.length); // if you want to send more data!!
											// you must send the data in chunks!
											// If error <-- smaller chunks
		}
	}

	// STEP 1 ----
	// (2)
	private void validateHello(APDU apdu) {
		System.out.println("validateHello");

		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		
		//step 1 (3)
		if (checkTimestamp(buffer, (int) ISO7816.OFFSET_CDATA, (int) buffer[ISO7816.OFFSET_LC]) == false)
			ISOException.throwIt(SW_REQ_REVALIDATION);
	}

	private boolean checkTimestamp(byte[] buffer, int offset, int length) {
		byte[] tempArray = new byte[(short) length];
		Util.arrayCopy(buffer, (short) offset, tempArray, (short) 0, (short) length);

		long buffer_time = byteArrayToLong(tempArray);
		long last_time = byteArrayToLong(lastValidationTime);

		System.out.println(buffer_time);
		System.out.println(last_time);

		return ((buffer_time - last_time) < VALIDATION_TIME);
	}

	private long byteArrayToLong(byte[] byteArray) {
		return Long.parseLong(byteArrayToHexString(byteArray), 16);
	}

	// (9)
	private void newTime(APDU apdu) {
		System.out.println("newTime");
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();

		byte[] new_time = new byte[buffer[ISO7816.OFFSET_CDATA]
				+ buffer[ISO7816.OFFSET_CDATA + buffer[ISO7816.OFFSET_CDATA] + 1]];
		byte[] new_timestamp = new byte[buffer[ISO7816.OFFSET_CDATA]];
		Util.arrayCopy(buffer, (short) (ISO7816.OFFSET_CDATA + 1), new_time, (short) 0, (short) new_timestamp.length);
		Util.arrayCopy(buffer, (short) (ISO7816.OFFSET_CDATA + 1), new_timestamp, (short) 0,
				(short) new_timestamp.length);
		byte[] new_timestring = new byte[buffer[ISO7816.OFFSET_CDATA + new_timestamp.length + 1]];

		Util.arrayCopy(buffer, (short) (ISO7816.OFFSET_CDATA + new_timestamp.length + 2), new_time,
				(short) new_timestamp.length, (short) new_timestring.length);
		Util.arrayCopy(buffer, (short) (ISO7816.OFFSET_CDATA + new_timestamp.length + 2), new_timestring, (short) 0,
				(short) new_timestring.length);
		byte[] new_timesig = new byte[buffer[ISO7816.OFFSET_CDATA + new_time.length + 2]];

		Util.arrayCopy(buffer, (short) (ISO7816.OFFSET_CDATA + new_time.length + 2 + 1), new_timesig, (short) 0,
				(short) new_timesig.length);

		// (10) Verify signature with PK of Government
		Signature sig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
		//TODO: ---- sig.init(time_pk, Signature.MODE_VERIFY);
		Boolean verify = sig.verify(new_time, (short) 0, (short) new_time.length, new_timesig, (short) 0,
				(short) new_timesig.length);

		if (verify != false) {
			System.out.println("Time signature verified");
			// (11) Abort if current time on card is later than the time from the Government Timeserver
			if (byteArrayToLong(lastValidationTime) < byteArrayToLong(new_timestamp)) {

				// (12) Update the time.
				lastValidationTime = new byte[new_timestamp.length];
				Util.arrayCopy(new_timestamp, (short) 0, lastValidationTime, (short) 0, (short) new_timestamp.length);
				lastValidationTimeString = new byte[new_timestring.length];
				Util.arrayCopy(new_timestring, (short) 0, lastValidationTimeString, (short) 0,
						(short) new_timestring.length);

			} else
				ISOException.throwIt(SW_ABORT);
		} else
			ISOException.throwIt(SW_ABORT);
	}

	// STEP 2 ----

	// STEP 3 ----

	// STEP 4 ----

	// ------ help functions
	// ------------------------------------------------------
	// byte array from hex string
	private static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	// hex from byte array
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

	// find substring in byte array
	private static int arraySubstrIndex(byte[] str, byte[] substr) {
		return arraySubstrIndexFrom(str, substr, 0);
	}

	private static int arraySubstrIndexFrom(byte[] str, byte[] substr, int offset) {
		int res = -1;
		for (int i = offset; i < str.length; i++) {
			int temp_res = -1;
			if (str[i] == substr[0]) {
				temp_res = i;
				for (int j = 1; j < substr.length; j++) {
					if (str[i + j] != substr[j]) {
						temp_res = -1;
						break;
					}
				}
				if (temp_res >= 0) {
					res = temp_res;
					break;
				}
			}
		}
		return res;
	}

}
