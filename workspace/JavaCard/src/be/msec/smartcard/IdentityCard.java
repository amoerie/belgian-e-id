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

	private static final int APDU_MAX_BUFF_SIZE = 128;

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

	//last variables
	static byte[] last_message = new byte[0];
	static byte[] last_signature = new byte[0];
	static byte[] last_cert = new byte[0];
	static RSAPublicKey last_pk;
	static byte[] last_modulus;
	static byte[] last_exp = hexStringToByteArray("010001");
	
	// timestamps
	private byte[] lastValidationTime = new byte[] { 0x00 }; // (20)000101000000
	private byte[] lastValidationTimeString = hexStringToByteArray("303030313031303030303030"); // (20)000101000000
	private final static long VALIDATION_TIME = 24 * 60 * 60;
	private final static byte TIMESTAMP_SIZE = (byte) 0x12;
	

	
	//certificates - start \\\\
	private String common_key_hex;
	private String common_cert_hex;
	private String gov_cert_hex;
	
	private byte[] common_key_bytes;
	private byte[] common_cert_bytes;
	private byte[] common_modulus_bytes;
	private byte[] common_exp_priv_bytes;
	private byte[] common_exp_pub_bytes;
	private byte[] common_sig_bytes;
	private byte[] gov_cert_bytes;
	private byte[] gov_modulus_bytes;
	private byte[] gov_exp_pub_bytes;
	
	//different domains
	private final static String DOMAIN_DEFAULT_HEX = "64656661756C74";
	private final static byte[] DOMAIN_DEFAULT_BYTES = hexStringToByteArray(DOMAIN_DEFAULT_HEX);
	private final static String DOMAIN_SUPERMARKET_HEX = "65436F6D6D65726365";
	private final static byte[] DOMAIN_SUPERMARKET_BYTES = hexStringToByteArray(DOMAIN_SUPERMARKET_HEX);
	private final static String DOMAIN_EGOV_HEX = "65476F76";
	private final static byte[] DOMAIN_EGOV_BYTES = hexStringToByteArray(DOMAIN_EGOV_HEX);
	private final static String DOMAIN_SOCNET_HEX = "536F634E6574";
	private final static byte[] DOMAIN_SOCNET_BYTES = hexStringToByteArray(DOMAIN_SOCNET_HEX);
	
	//storage to process certificate
	static byte[] last_cert_issuer_cn;
	static byte[] last_cert_issuer_domain;
	static byte[] last_cert_subject_cn;
	static byte[] last_cert_subject_domain;
	static byte[] last_cert_valid_after;
	static byte[] last_cert_valid_before;
	static byte[] last_cert_modulus;
	static byte[] last_cert_exponent;
	static byte[] last_cert_signature;
	static byte[] last_cert_tbs;
	
	static AESKey last_symm_key;
	static byte[] last_symm_key_bytes;
	static byte[] last_symm_key_encrypted;
	static RSAPublicKey last_cert_pk;
	static byte[] last_challenge;
	static byte[] last_challenge_with_subject;
	static byte[] last_challenge_with_subject_encrypted;
	static byte[] sp_auth_response;
	static byte[] last_challenge_response_aes;
	static byte[] last_challenge_response;
	long chall_resp_long;
	long chall_long;
	static byte[] last_server_challenge;
	static byte[] last_server_challenge_resp;
	static byte[] last_server_challenge_resp_encrypted;
	boolean check_auth_content = false;
	
	static byte[] last_query = new byte[0];
	static byte[] query_item = new byte[0];
	
	private RSAPrivateKey common_sk = (RSAPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PRIVATE, KeyBuilder.LENGTH_RSA_512, false);
	private RSAPublicKey common_pk = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_512, false);
	private RSAPublicKey gov_pk = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_512, false);
	private RSAPublicKey time_pk = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_512, false);
	
	private final static int LENGTH_RSA_512_BYTES = KeyBuilder.LENGTH_RSA_512/8;
	private final static int LENGTH_AES_128_BYTES = KeyBuilder.LENGTH_AES_128/8;
	Cipher cipher;
	RandomData srng;
	Signature sig;
	MessageDigest md;
	
	byte[] query_result;
	byte[] query_result_encrypted;
	
	
	private int temp_int;
	
	//certificates - end \\\\
	
	//keywords - start \\\\
	private final static String MODULUS_HEX = "024100";
	private final static byte[] MODULUS_BYTES = hexStringToByteArray(MODULUS_HEX);
	private final static String EXPONENT_HEX = "010240";
	private final static byte[] EXPONENT_BYTES = hexStringToByteArray(EXPONENT_HEX);
	private final static String SIGNATURE_HEX = "300D06092A864886F70D0101050500034100";
	private final static byte[] SIGNATURE_BYTES = hexStringToByteArray(SIGNATURE_HEX);
	private final static String CERT_HEX = "308201"; // +1byte
	private final static byte[] CERT_BYTES = hexStringToByteArray(CERT_HEX);
	private final static String DOMAIN_HEX = "060A0992268993F22C64040D0C";
	private final static byte[] DOMAIN_BYTES = hexStringToByteArray(DOMAIN_HEX);
	private final static String CN_HEX = "06035504030C";
	private final static byte[] CN_BYTES = hexStringToByteArray(CN_HEX);
	private final static String VALIDITY_HEX = "170D";
	private final static byte[] VALIDITY_BYTES = hexStringToByteArray(VALIDITY_HEX);
		
	//keywords - end \\\\
	
	//Data fields
	private final static byte NYM_IDX = (byte)0;
	private final static byte NAME_IDX = (byte)1;
	private final static byte ADDRESS_IDX = (byte)2;
	private final static byte COUNTRY_IDX = (byte)3;
	private final static byte BIRTHDATE_IDX = (byte)4;
	private final static byte AGE_IDX = (byte)5;
	private final static byte GENDER_IDX = (byte)6;
	private final static byte PICTURE_IDX = (byte)7;
	private final static byte[] DOMAIN_DEFAULT_AUTH = new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00};
	private final static byte[] DOMAIN_SUPERMARKET_AUTH = new byte[]{0x01, 0x01, 0x01, 0x01, 0x00, 0x01, 0x00, 0x00};
	private final static byte[] DOMAIN_EGOV_AUTH = new byte[]{0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00};
	private final static byte[] DOMAIN_SOCNET_AUTH = new byte[]{0x01, 0x01, 0x00, 0x01, 0x00, 0x01, 0x00, 0x01};
	//Mocking DATA
	private static byte[] NYM = null;
	private final static byte[] NAME = "Tom Alex".getBytes();
	private final static byte[] ADDRESS = "Kerkstraat 1, 9340 Lede".getBytes();
	private final static byte[] COUNTRY = "BE".getBytes();
	private final static byte[] BIRTHDATE = "18/02/1978".getBytes();
	private final static byte[] AGE = "39".getBytes();
	private final static byte[] GENDER = "M".getBytes();
	private final static byte[] PICTURE = "\n|==========|\n|          |\n|  O   O   |\n|    0     |\n| \\      / |\n|  ------  |\n\\         /\n  =======".getBytes();
	
	
	

	private Boolean isServiceAuthenticated = false;
	private Boolean isPinValidated = false;
	
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

		// government certificates and common private key
		//sha256
		gov_cert_hex = "30820243308201EDA003020102020900E34DA0275BBFFBD6300D06092A864886F70D01010B05003075310B30090603550406130242453111300F06035504080C084272757373656C733111300F06035504070C084272757373656C73310B3009060355040A0C024341310C300A060355040B0C03565542310B300906035504030C0243413118301606092A864886F70D01090116096361407675622E6265301E170D3137303530373134323333355A170D3137303630363134323333355A3075310B30090603550406130242453111300F06035504080C084272757373656C733111300F06035504070C084272757373656C73310B3009060355040A0C024341310C300A060355040B0C03565542310B300906035504030C0243413118301606092A864886F70D01090116096361407675622E6265305C300D06092A864886F70D0101010500034B003048024100CE832F1E76C683B0DD150F189660FF4D5A63478CC35BA6BB78BD2A061829BDD852889CFE04F44674933B146A5EB45276219FAB763BC589F696F6F0306C6D49950203010001A360305E301D0603551D0E04160414FAFF2F7386E7AF24F46EA5EA734F703CD8E3407B301F0603551D23041830168014FAFF2F7386E7AF24F46EA5EA734F703CD8E3407B300F0603551D130101FF040530030101FF300B0603551D0F040403020106300D06092A864886F70D01010B0500034100405D4CB97716896FFF73490439688CC24664DEF2977C316491D089C288530929C9A660FA0AD1A4ED8652419796143F4CF56D99AAF06C3FCC95999CE8693BEF8F";
		//sha1
		//TODO gov_cert_hex = "";
		gov_cert_bytes = hexStringToByteArray(gov_cert_hex);
		
		temp_int = arraySubstrIndex(gov_cert_bytes, MODULUS_BYTES) + MODULUS_BYTES.length;
		gov_modulus_bytes = new byte[LENGTH_RSA_512_BYTES];
		Util.arrayCopy(gov_cert_bytes, (short)temp_int, gov_modulus_bytes, (short)0, (short)LENGTH_RSA_512_BYTES);
		gov_exp_pub_bytes = hexStringToByteArray("010001");


		// make keys
		gov_pk.setExponent(gov_exp_pub_bytes, (short)0, (short)gov_exp_pub_bytes.length);
		gov_pk.setModulus(gov_modulus_bytes, (short)0, (short)gov_modulus_bytes.length);
		

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

		// step 1
		case HELLO_DIS:
			validateHello(apdu);
			break;
		case NEW_TIME:
			newTime(apdu);
			break;
			
		// step 2
		case NEW_SERVICE_CERT:
			setServiceCertificate(apdu);
			break;
		case SERVICE_CERT_DONE:
			treatServiceCertificate(apdu);
			break;
		case SERVICE_AUTH:
			authenticateService(apdu);
			break;
		case SERVICE_RESP_CHALLENGE:
			verifyServiceRespChallenge(apdu);
			break;

		// step 3
		case SERVICE_CHALLENGE:
			sendServerChallengeResponse(apdu);
			break;
		
		// step 4
		case NEW_QUERY:
			setLastQuery(apdu);
			break;
		case QUERY_DONE:
			processLastQuery(apdu);
			break;
		//case VALIDATE_PIN_INS - already defined
		case GET_QUERY:
			if (isServiceAuthenticated != false && isPinValidated != false){
				sendQueryResult(apdu);
			} else {
				ISOException.throwIt(SW_ABORT);
			}
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

	// STEP 1 ---------------------------------------------------------------------------------
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
		System.out.println("Verify signature with PK of Government");
		//why not ALG_RSA_SHAT_256_PKCS1
		//https://docs.oracle.com/javacard/3.0.5/api/javacard/security/Signature.html
		//https://www.win.tue.nl/pinpasjc/docs/apis/jc222/javacard/security/Signature.html
		//Signature sig = Signature.getInstance(Signature.ALG_RSA_SHA_256_PKCS1, false); --> not yet in 2.2.2
		//Signature sig = Signature.getInstance(Signature.ALG_HMAC_SHA_256, false);
		//sha1
		
		//TODO: signing now in comment!
//		Signature sig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
//		sig.init(time_pk, Signature.MODE_VERIFY);
//		System.out.println("verify test");
//		Boolean verify = sig.verify(new_time, (short) 0, (short) new_time.length, new_timesig, (short) 0,
//				(short) new_timesig.length);
//
//		if (verify != false) {
			System.out.println("Time signature verified");
			// (11) Abort if current time on card is later than the time from the Government Timeserver
			if (byteArrayToLong(lastValidationTime) < byteArrayToLong(new_timestamp)) {

				// (12) Update the time.
				lastValidationTime = new byte[new_timestamp.length];
				Util.arrayCopy(new_timestamp, (short) 0, lastValidationTime, (short) 0, (short) new_timestamp.length);
				lastValidationTimeString = new byte[new_timestring.length];
				Util.arrayCopy(new_timestring, (short) 0, lastValidationTimeString, (short) 0,
						(short) new_timestring.length);

//			} else
//				ISOException.throwIt(SW_ABORT);
		} else
			ISOException.throwIt(SW_ABORT);
	}

	// STEP 2 --------------------------------------------------------------------------------
	// step 2 (1)
	private void setServiceCertificate(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		int buffer_length;
		if (buffer[ISO7816.OFFSET_LC] <0){
			buffer_length = buffer[ISO7816.OFFSET_LC] * -1;
		} else {
			buffer_length = buffer[ISO7816.OFFSET_LC];
		}
		byte[] temp = new byte[last_cert.length];
		Util.arrayCopy(last_cert, (short)0, temp, (short)0, (short)temp.length);
		last_cert = new byte[temp.length + buffer_length];
		Util.arrayCopy(temp, (short)0, last_cert, (short)0, (short)temp.length);
		Util.arrayCopy(buffer, (short)5, last_cert, (short)temp.length, (short)buffer_length);
		System.out.println(byteArrayToHexString(last_cert));
	}
	private void treatServiceCertificate(APDU apdu) {
		//get issuer cn
		int issuer_cn_offset = arraySubstrIndex(last_cert, CN_BYTES) + CN_BYTES.length;
		last_cert_issuer_cn = new byte[last_cert[issuer_cn_offset]];
		Util.arrayCopy(last_cert, (short)(issuer_cn_offset+1), last_cert_issuer_cn, (short)0, (short)last_cert_issuer_cn.length);
		//get issuer domain
		int issuer_domain_offset = arraySubstrIndexFrom(last_cert, DOMAIN_BYTES, issuer_cn_offset) + DOMAIN_BYTES.length;
		last_cert_issuer_domain = new byte[last_cert[issuer_domain_offset]];
		Util.arrayCopy(last_cert, (short)(issuer_domain_offset+1), last_cert_issuer_domain, (short)0, (short)last_cert_issuer_domain.length);
		//get valid after
		int valid_after_offset = arraySubstrIndexFrom(last_cert, VALIDITY_BYTES, issuer_domain_offset) + VALIDITY_BYTES.length;
		last_cert_valid_after = new byte[12];
		Util.arrayCopy(last_cert, (short)valid_after_offset, last_cert_valid_after, (short)0, (short)last_cert_valid_after.length);
		//get valid before
		int valid_before_offset = arraySubstrIndexFrom(last_cert, VALIDITY_BYTES, valid_after_offset) + VALIDITY_BYTES.length;
		last_cert_valid_before = new byte[12];
		Util.arrayCopy(last_cert, (short)valid_before_offset, last_cert_valid_before, (short)0, (short)last_cert_valid_before.length);
		//get subject cn
		int subject_cn_offset = arraySubstrIndexFrom(last_cert, CN_BYTES, valid_before_offset) + CN_BYTES.length;
		last_cert_subject_cn = new byte[last_cert[subject_cn_offset]];
		Util.arrayCopy(last_cert, (short)(subject_cn_offset+1), last_cert_subject_cn, (short)0, (short)last_cert_subject_cn.length);
		//get subject domain
		int subject_domain_offset = arraySubstrIndexFrom(last_cert, DOMAIN_BYTES, subject_cn_offset) + DOMAIN_BYTES.length;
		last_cert_subject_domain = new byte[last_cert[subject_domain_offset]];
		Util.arrayCopy(last_cert, (short)(subject_domain_offset+1), last_cert_subject_domain, (short)0, (short)last_cert_subject_domain.length);
		//get pk modulus
		int modulus_offset = arraySubstrIndexFrom(last_cert, MODULUS_BYTES, subject_domain_offset) + MODULUS_BYTES.length;
		last_cert_modulus = new byte[LENGTH_RSA_512_BYTES];
		Util.arrayCopy(last_cert, (short)(modulus_offset), last_cert_modulus, (short)0, (short)last_cert_modulus.length);
		//get pk exponent
		int exponent_offset = modulus_offset + last_cert_modulus.length + 1;
		last_cert_exponent = new byte[last_cert[exponent_offset]];
		Util.arrayCopy(last_cert, (short)(exponent_offset+1), last_cert_exponent, (short)0, (short)last_cert_exponent.length);
		//get signature
		int signature_offset = arraySubstrIndexFrom(last_cert, SIGNATURE_BYTES, exponent_offset) + SIGNATURE_BYTES.length;
		last_cert_signature = new byte[LENGTH_RSA_512_BYTES];
		last_cert_tbs = new byte[signature_offset - SIGNATURE_BYTES.length - 4];
		Util.arrayCopy(last_cert, (short)(signature_offset), last_cert_signature, (short)0, (short)last_cert_signature.length);
		Util.arrayCopy(last_cert, (short)4, last_cert_tbs, (short)0, (short)last_cert_tbs.length);
		
		//verify
		System.out.println("verify last cert");
		System.out.println(byteArrayToHexString(last_cert_issuer_cn));
		System.out.println(byteArrayToHexString(last_cert_issuer_domain));
		System.out.println(byteArrayToHexString(last_cert_subject_cn));
		System.out.println(byteArrayToHexString(last_cert_subject_domain));
		System.out.println(byteArrayToHexString(last_cert_valid_after));
		System.out.println(byteArrayToHexString(last_cert_valid_before));
		System.out.println(byteArrayToHexString(last_cert_modulus));
		System.out.println(byteArrayToHexString(last_cert_exponent));
		System.out.println(byteArrayToHexString(last_cert_signature));
	}
	private void authenticateService(APDU apdu) {
		//Step 2 (2) verify certificate
		if (verifySig(last_cert_tbs, last_cert_signature, gov_pk) != false){
			System.out.println("Certificate verified");
		}else ISOException.throwIt(SW_SIG_NO_MATCH);
		
		//Step 2 (3) verify if certificate is valid
		if (verifyValid(last_cert_valid_after, last_cert_valid_before, lastValidationTimeString) != false){
			System.out.println("Certificate valid");
		}else ISOException.throwIt(SW_CERT_EXPIRED);
		
		//Step 2 (4) generate new symmetric key
		last_symm_key = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
		genSymmKey();
		last_symm_key.setKey(last_symm_key_bytes, (short)0);

		//Step 2 (5) asymetrische encryptie with public key of service provider
		last_cert_pk = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_512, false);
		last_cert_pk.setExponent(last_cert_exponent, (short)0, (short)last_cert_exponent.length);
		last_cert_pk.setModulus(last_cert_modulus, (short)0, (short)last_cert_modulus.length);
		cipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
		cipher.init(last_cert_pk, Cipher.MODE_ENCRYPT);
		last_symm_key_encrypted = new byte[LENGTH_RSA_512_BYTES];
		cipher.doFinal(last_symm_key_bytes, (short)0, (short)last_symm_key_bytes.length, last_symm_key_encrypted, (short)0);
		
		//generate challenge
		genChallenge(); 
		//encrypt challenge with generated symmetric key
		cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
		cipher.init(last_symm_key, Cipher.MODE_ENCRYPT);
		temp_int = last_challenge.length + last_cert_subject_cn.length + 2;
		temp_int += LENGTH_AES_128_BYTES - (temp_int%16);
		last_challenge_with_subject = new byte[temp_int];
		last_challenge_with_subject[0] = (byte)last_challenge.length;
		Util.arrayCopy(last_challenge, (short)0, last_challenge_with_subject, (short)1, (short)last_challenge.length);
		last_challenge_with_subject[last_challenge.length+1] = (byte)last_cert_subject_cn.length;
		Util.arrayCopy(last_cert_subject_cn, (short)0, last_challenge_with_subject, (short)(last_challenge.length+2), (short)last_cert_subject_cn.length);
		last_challenge_with_subject_encrypted = new byte[temp_int];
		cipher.doFinal(last_challenge_with_subject, (short)0, (short)last_challenge_with_subject.length, last_challenge_with_subject_encrypted, (short)0);
		
		System.out.println("sending this challenge: " + byteArrayToHexString(last_challenge));
		System.out.println("sending this subject: " + byteArrayToHexString(last_challenge_with_subject));
		System.out.println("sending this symm key: " + byteArrayToHexString(last_symm_key_bytes));
		//generate the response array with the symmetric key and the challenge
		// length + symm key + length + challenge with subject
		sp_auth_response = new byte[1 + last_symm_key_encrypted.length + 1 + last_challenge_with_subject_encrypted.length];
		sp_auth_response[0] = (byte)last_symm_key_encrypted.length;
		Util.arrayCopy(last_symm_key_encrypted, (short)0, sp_auth_response, (short)1, (short)last_symm_key_encrypted.length);
		sp_auth_response[last_symm_key_encrypted.length+1] = (byte)last_challenge_with_subject_encrypted.length;
		Util.arrayCopy(last_challenge_with_subject_encrypted, (short)0, sp_auth_response, (short)(last_symm_key_encrypted.length+2), (short)last_challenge_with_subject_encrypted.length);
		
		//send response back to M
		apdu.setOutgoing();
		apdu.setOutgoingLength((short)sp_auth_response.length);
		System.out.println("sending response with length " + sp_auth_response.length);
		apdu.sendBytesLong(sp_auth_response,(short)0,(short)sp_auth_response.length);
		
	}
	private boolean verifySig(byte[] message, byte[] signature, RSAPublicKey pk) {
		System.out.println("verifying signature");
		System.out.println(byteArrayToHexString(message));
		System.out.println(byteArrayToHexString(signature));
		Signature sig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
		sig.init(pk, Signature.MODE_VERIFY);
		return sig.verify(message, (short)0, (short)message.length, signature, (short)0, (short)signature.length);
	}
	private boolean verifyValid(byte[] cert_after, byte[] cert_before, byte[] time_string) {
		System.out.println(byteArrayToHexString(cert_after));
		System.out.println(byteArrayToHexString(cert_before));
		System.out.println(byteArrayToHexString(time_string));
		
		String after = byteArrayToHexString(cert_after);
		String before = byteArrayToHexString(cert_before);
		String time = byteArrayToHexString(time_string);
		
		return (time.compareTo(after) > 0 && time.compareTo(before) < 0);
	}
	private void genSymmKey() {
		last_symm_key_bytes = new byte[LENGTH_AES_128_BYTES];
		srng = RandomData.getInstance(RandomData.ALG_PSEUDO_RANDOM); //ALG_SECURE_RANDOM NOT WORKING...
		srng.generateData(last_symm_key_bytes, (short)0, (short)last_symm_key_bytes.length);
	}
	private void genChallenge() {
		last_challenge = new byte[2];
		srng = RandomData.getInstance(RandomData.ALG_PSEUDO_RANDOM);
		srng.generateData(last_challenge, (short)0, (short)2);
	}
	// Step 2 (13)
	private void verifyServiceRespChallenge(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		System.out.println("using key to decrypt: " + byteArrayToHexString(last_symm_key_bytes));
		
		byte[] message_chunk = new byte[buffer[ISO7816.OFFSET_LC]];
		Util.arrayCopy(buffer, (short)ISO7816.OFFSET_CDATA, message_chunk, (short)0, (short)message_chunk.length);
		System.out.println("incoming buffer: " + byteArrayToHexString(message_chunk));
		System.out.println("incoming buffer length: " + message_chunk.length);
		
		last_challenge_response_aes = new byte[LENGTH_AES_128_BYTES];
		cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
		cipher.init(last_symm_key, Cipher.MODE_DECRYPT);
		cipher.doFinal(message_chunk, (short)0, (short)message_chunk.length, last_challenge_response_aes, (short)0);
		last_challenge_response = new byte[last_challenge_response_aes[0]];
		System.out.println(byteArrayToHexString(last_challenge_response_aes));
		System.out.println(last_challenge_response.length);
		Util.arrayCopy(last_challenge_response_aes, (short)1, last_challenge_response, (short)0, (short)last_challenge_response.length);
		
		chall_long = Long.parseLong(byteArrayToHexString(last_challenge), 16);
		chall_resp_long = Long.parseLong(byteArrayToHexString(last_challenge_response), 16);
	
		if ((chall_resp_long-chall_long) == 1){
			isServiceAuthenticated = true;
			System.out.println("Server authenticated!");
			//challenge answer OK, SP is now authenticated
			//respond to challenge from server
			last_server_challenge = new byte[last_challenge_response_aes[1 + last_challenge_response.length]];
			Util.arrayCopy(last_challenge_response_aes, (short)(1 + last_challenge_response.length + 1), last_server_challenge, (short)0, (short)last_server_challenge.length);
			
			//sign challenge with common SK
			temp_int = 1 + LENGTH_RSA_512_BYTES + 1 + common_cert_bytes.length;
			temp_int += LENGTH_AES_128_BYTES - (temp_int%16);
			last_server_challenge_resp = new byte[temp_int];
			last_server_challenge_resp[0] = (byte) LENGTH_RSA_512_BYTES;
			sig = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
			sig.init(common_sk, Signature.MODE_SIGN);
			sig.sign(last_server_challenge, (short)0, (short)last_server_challenge.length, last_server_challenge_resp, (short)1);
			//add the common certificate
			System.out.println("cert length " + common_cert_bytes.length);
			last_server_challenge_resp[1 + LENGTH_RSA_512_BYTES] = (byte)(last_server_challenge_resp.length - 1 - LENGTH_RSA_512_BYTES - 1 - common_cert_bytes.length); //we will not send the cert length, but the number of remaining bytes in the array
			Util.arrayCopy(common_cert_bytes, (short)0, last_server_challenge_resp, (short)(1 + LENGTH_RSA_512_BYTES + 1), (short)common_cert_bytes.length);
			//encrypt with symmetric key
			cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
			cipher.init(last_symm_key, Cipher.MODE_ENCRYPT);
			last_server_challenge_resp_encrypted = new byte[temp_int];
			cipher.doFinal(last_server_challenge_resp, (short)0, (short)last_server_challenge_resp.length, last_server_challenge_resp_encrypted, (short)0);
			
			System.out.println("response to server: " + byteArrayToHexString(last_server_challenge_resp));
			//now wait for middleware to get the string as it is too long to send back in one response
				
		} else {
			ISOException.throwIt(SW_ABORT);
		}

	}
	
	

	// STEP 3 ----
	private void sendServerChallengeResponse(APDU apdu){
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		short i = (short)buffer[ISO7816.OFFSET_CDATA];
		if (i<=last_server_challenge_resp_encrypted.length/APDU_MAX_BUFF_SIZE){
			int my_message_part_length = APDU_MAX_BUFF_SIZE;
			if (last_server_challenge_resp_encrypted.length-(i*APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE){
				my_message_part_length = last_server_challenge_resp_encrypted.length-(i*APDU_MAX_BUFF_SIZE);
			}
			if (my_message_part_length > 0){
				byte[] my_message_part = new byte[my_message_part_length];
				System.arraycopy(last_server_challenge_resp_encrypted, APDU_MAX_BUFF_SIZE*i, my_message_part, 0, my_message_part_length);
				System.out.println(byteArrayToHexString(my_message_part));
				apdu.setOutgoing();
				apdu.setOutgoingLength((short)my_message_part_length);
				System.out.println("sending response with length " + my_message_part_length);
				apdu.sendBytesLong(my_message_part,(short)0,(short)my_message_part_length);
			}
		} else {
			apdu.setOutgoing();
			apdu.setOutgoingLength((short)0);
			apdu.sendBytesLong(new byte[0],(short)0,(short)0);
		}
	}	
	

	// STEP 4 ----
	private void setLastQuery(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		int buffer_length;
		if (buffer[ISO7816.OFFSET_LC] <0){
			buffer_length = buffer[ISO7816.OFFSET_LC] * -1;
		} else {
			buffer_length = buffer[ISO7816.OFFSET_LC];
		}
		byte[] temp = new byte[last_query.length];
		Util.arrayCopy(last_query, (short)0, temp, (short)0, (short)temp.length);
		last_query = new byte[temp.length + buffer_length];
		Util.arrayCopy(temp, (short)0, last_query, (short)0, (short)temp.length);
		Util.arrayCopy(buffer, (short)5, last_query, (short)temp.length, (short)buffer_length);
		System.out.println(byteArrayToHexString(last_query));
	}
	private void processLastQuery(APDU apdu) {
		// TODO Auto-generated method stub
		temp_int = 0;
		query_result = new byte[(short)0];
		while (temp_int < last_query.length){
			query_item = new byte[(short)last_query[temp_int]];
			Util.arrayCopy(last_query, (short)(temp_int+1), query_item, (short)0, (short)query_item.length);
			
			if ("nym".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "nym".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested nym");
				if (checkAuthorized(NYM_IDX, "nym".getBytes(), genNym()) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("name".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "name".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested name");
				if (checkAuthorized(NAME_IDX, "name".getBytes(), NAME) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("address".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "address".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested address");
				if (checkAuthorized(ADDRESS_IDX, "address".getBytes(), ADDRESS) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("country".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "country".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested country");
				if (checkAuthorized(COUNTRY_IDX, "country".getBytes(), COUNTRY) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("birthdate".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "birthdate".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested birthdate");
				if (checkAuthorized(BIRTHDATE_IDX, "birthdate".getBytes(), BIRTHDATE) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("age".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "age".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested age");
				if (checkAuthorized(AGE_IDX, "age".getBytes(), AGE) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("gender".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "gender".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested gender");
				if (checkAuthorized(GENDER_IDX, "gender".getBytes(), GENDER) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			} else if ("picture".getBytes().length == query_item.length && (short)Util.arrayCompare(query_item, (short)0, "picture".getBytes(), (short)0, (short)query_item.length) == 0){
				System.out.println("requested picture");
				if (checkAuthorized(PICTURE_IDX, "picture".getBytes(), PICTURE) == false){
					ISOException.throwIt(SW_ABORT);
					break;
				}
			}
			
			
			temp_int++;
			temp_int += query_item.length;
		}
		System.out.println("loop done");
		
		//encrypt the query result
		temp_int = query_result.length;
		temp_int += LENGTH_AES_128_BYTES - (temp_int%16);
		byte[] query_result_aes = new byte[temp_int];
		Util.arrayCopy(query_result, (short)0, query_result_aes, (short)(0), (short)query_result.length);
		cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
		cipher.init(last_symm_key, Cipher.MODE_ENCRYPT);
		query_result_encrypted = new byte[temp_int];
		cipher.doFinal(query_result_aes, (short)0, (short)query_result_aes.length, query_result_encrypted, (short)0);
		
	}
	private boolean checkAuthorized(byte idx, byte[] param, byte[] val) {
		check_auth_content = false;
		if (DOMAIN_DEFAULT_BYTES.length == last_cert_subject_domain.length && (short)Util.arrayCompare(DOMAIN_DEFAULT_BYTES, (short)0, last_cert_subject_domain, (short)0, (short)last_cert_subject_domain.length) == 0){
			System.out.println("default domain");
			if ((short)DOMAIN_DEFAULT_AUTH[(short)idx] == (short)1){
				check_auth_content = true;
			}
		} else if (DOMAIN_EGOV_BYTES.length == last_cert_subject_domain.length && (short)Util.arrayCompare(DOMAIN_EGOV_BYTES, (short)0, last_cert_subject_domain, (short)0, (short)last_cert_subject_domain.length) == 0){
			System.out.println("egov domain");
			if ((short)DOMAIN_EGOV_AUTH[(short)idx] == (short)1){
				check_auth_content = true;
			}
		} else if (DOMAIN_SOCNET_BYTES.length == last_cert_subject_domain.length && (short)Util.arrayCompare(DOMAIN_SOCNET_BYTES, (short)0, last_cert_subject_domain, (short)0, (short)last_cert_subject_domain.length) == 0){
			System.out.println("socnet domain");
			if ((short)DOMAIN_SOCNET_AUTH[(short)idx] == (short)1){
				check_auth_content = true;
			}
		} else if (DOMAIN_SUPERMARKET_BYTES.length == last_cert_subject_domain.length && (short)Util.arrayCompare(DOMAIN_SUPERMARKET_BYTES, (short)0, last_cert_subject_domain, (short)0, (short)last_cert_subject_domain.length) == 0){
			System.out.println("super market domain");
			if ((short)DOMAIN_SUPERMARKET_AUTH[(short)idx] == (short)1){
				check_auth_content = true;
			}
		}
		
		if (check_auth_content != false){
			byte[] temp_array = new byte[(short)query_result.length];
			if(temp_array.length > 0){
				Util.arrayCopy(query_result, (short)0, temp_array, (short)0, (short)temp_array.length);
			}
			query_result = new byte[(short)(temp_array.length + 2 + param.length + val.length)];
			if(temp_array.length > 0){
				Util.arrayCopy(temp_array, (short)0, query_result, (short)0, (short)temp_array.length);
			}
			query_result[temp_array.length] = (byte)param.length;
			Util.arrayCopy(param, (short)0, query_result, (short)(temp_array.length+1), (short)param.length);
			query_result[temp_array.length+1+param.length] = (byte)val.length;
			Util.arrayCopy(val, (short)0, query_result, (short)(temp_array.length+1+param.length+1), (short)val.length);
		}
		return check_auth_content;
	}
	private byte[] genNym() {
		md = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
		NYM = new byte[MessageDigest.LENGTH_SHA];
		byte[] temp_array = new byte[last_cert_subject_cn.length + serial.length];
		Util.arrayCopy(serial, (short)0, temp_array, (short)(0), (short)serial.length);
		Util.arrayCopy(last_cert_subject_cn, (short)0, temp_array, (short)(serial.length), (short)last_cert_subject_cn.length);
		md.doFinal(temp_array, (short)0, (short)temp_array.length, NYM, (short)0);
		return NYM;
	}
	private void sendQueryResult(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		apdu.setIncomingAndReceive();
		short i = (short)buffer[ISO7816.OFFSET_CDATA];
		if (i<=query_result_encrypted.length/APDU_MAX_BUFF_SIZE){
			int my_message_part_length = APDU_MAX_BUFF_SIZE;
			if (query_result_encrypted.length-(i*APDU_MAX_BUFF_SIZE) < APDU_MAX_BUFF_SIZE){
				my_message_part_length = query_result_encrypted.length-(i*APDU_MAX_BUFF_SIZE);
			}
			if (my_message_part_length > 0){
				byte[] my_message_part = new byte[my_message_part_length];
				System.arraycopy(query_result_encrypted, APDU_MAX_BUFF_SIZE*i, my_message_part, 0, my_message_part_length);
				System.out.println(byteArrayToHexString(my_message_part));
				apdu.setOutgoing();
				apdu.setOutgoingLength((short)my_message_part_length);
				System.out.println("sending response with length " + my_message_part_length);
				apdu.sendBytesLong(my_message_part,(short)0,(short)my_message_part_length);
			}
		} else {
			apdu.setOutgoing();
			apdu.setOutgoingLength((short)0);
			apdu.sendBytesLong(new byte[0],(short)0,(short)0);
		}
	}

	
	
	

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
