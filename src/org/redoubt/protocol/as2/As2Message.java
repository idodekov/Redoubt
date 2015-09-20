package org.redoubt.protocol.as2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.internet.MimeBodyPart;

import org.apache.log4j.Logger;
import org.redoubt.api.configuration.ICertificateManager;
import org.redoubt.api.configuration.ICryptoHelper;
import org.redoubt.api.factory.Factory;
import org.redoubt.application.VersionInformation;
import org.redoubt.util.Utils;

public class As2Message {
	private static final Logger sLogger = Logger.getLogger(As2Message.class);
	
	private MimeBodyPart data;
	private String toAddress;
	private String fromAddress;
	private boolean encrypt;
	private boolean sign;
	private boolean compress;
	private String signCertAlias;
	private String signCertKeyPassword;
	private String signDigestAlgorithm;
	private String encryptAlgorithm;
	private String encryptCertAlias;
	private String compressionAlgorithm;
	
	private Map<String, String> headers;
	
	private String messageId;
	
	public As2Message() {
		data = new MimeBodyPart();
		headers = new HashMap<String, String>();
	}
	
	public As2Message(As2ProtocolSettings settings) {
		data = new MimeBodyPart();
		headers = new HashMap<String, String>();
		
		fromAddress= settings.getFrom();
		toAddress = settings.getTo();
		
		encrypt = settings.isEncryptionEnabled();
        sign = settings.isSigningEnabled();
        compress = settings.isCompressionEnabled();
        
        signCertAlias = settings.getSignCertAlias();
        signCertKeyPassword = settings.getSignCertKeyPassword();
        signDigestAlgorithm = settings.getSignDigestAlgorithm();
        encryptAlgorithm = settings.getEncryptAlgorithm();
        encryptCertAlias = settings.getEncryptCertAlias();
        compressionAlgorithm = settings.getCompressionAlgorithm();
	}
	
	public MimeBodyPart generateMimeData(String payload) throws Exception {
		sLogger.debug("Generating MIME data...");
		Path workFile = Paths.get(payload);
		
		messageId = Utils.generateMessageID(fromAddress);
        
        // TODO: add support for large files
		data.setDataHandler(new DataHandler(Files.readAllBytes(workFile), As2HeaderDictionary.MIME_TYPE_APPLICATION_OCTET_STREAM));
		
		data.setHeader(As2HeaderDictionary.CONTENT_TYPE, As2HeaderDictionary.MIME_TYPE_APPLICATION_OCTET_STREAM);
		data.setHeader(As2HeaderDictionary.CONTENT_TRANSFER_ENCODING, As2HeaderDictionary.TRANSFER_ENCODING_BINARY);

		secure();
        
		String contentType = Utils.normalizeContentType(data.getContentType());
        headers.put(As2HeaderDictionary.CONTENT_TYPE, contentType);
        headers.put(As2HeaderDictionary.AS2_FROM, fromAddress);
        headers.put(As2HeaderDictionary.AS2_TO, toAddress);
        headers.put(As2HeaderDictionary.AS2_VERSION, As2HeaderDictionary.AS2_VERSION_1_1);
        headers.put(As2HeaderDictionary.CONNECTION, "close, TE");
        headers.put(As2HeaderDictionary.USER_AGENT, VersionInformation.APP_NAME + " " + VersionInformation.APP_VERSION);
        headers.put(As2HeaderDictionary.ACCEPT_ENCODING, "gzip,deflate");
        headers.put(As2HeaderDictionary.MIME_VERSION, As2HeaderDictionary.MIME_VERSION_1_0);
        headers.put(As2HeaderDictionary.MIME_VERSION, As2HeaderDictionary.MIME_VERSION_1_0);
        headers.put(As2HeaderDictionary.DATE, Utils.createTimestamp());
        headers.put(As2HeaderDictionary.MESSAGE_ID, messageId);
        
        return data;
	}
	
	public void secure() throws Exception {
        // Encrypt and/or sign the data if requested
        if (encrypt || sign || compress) {
        	ICertificateManager certificateManager = Factory.getInstance().getCertificateManager();
        	ICryptoHelper cryptoHelper = Factory.getInstance().getCryptoHelper();

        	// Compress the data if requested
            if(compress) {
            	sLogger.debug("Compression is enabled - will attempt to compress the message.");
            	data = cryptoHelper.compress(data, compressionAlgorithm);
            }
        	
            // Sign the data if requested
            if (sign) {
                sLogger.debug("Signing is enabled - will attempt to sign the message.");
                X509Certificate signingCert = certificateManager.getX509Certificate(signCertAlias);
                PrivateKey senderKey = certificateManager.getPrivateKey(signCertAlias, signCertKeyPassword.toCharArray());

                data = cryptoHelper.sign(data, signingCert, senderKey, signDigestAlgorithm);
            }
            
            // Encrypt the data if requested
            if (encrypt) {
                sLogger.debug("Encryption is enabled - will attempt to encrypt the message.");
                X509Certificate receiverCert = certificateManager.getX509Certificate(encryptCertAlias);
                data = cryptoHelper.encrypt(data, receiverCert, encryptAlgorithm);
            }
        }
    }
	
	public String getMessageId() {
		return messageId;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getToAddress() {
		return toAddress;
	}
	
	public void setToAddres(String to) {
		toAddress = to;
	}
	
	public String getFromAddress() {
		return fromAddress;
	}
	
	public void setFromAddres(String from) {
		fromAddress = from;
	}

	public MimeBodyPart getData() {
		return data;
	}

	public void setData(MimeBodyPart data) {
		this.data = data;
	}

	public boolean isEncrypt() {
		return encrypt;
	}

	public void setEncrypt(boolean encrypt) {
		this.encrypt = encrypt;
	}

	public boolean isSign() {
		return sign;
	}

	public void setSign(boolean sign) {
		this.sign = sign;
	}

	public boolean isCompress() {
		return compress;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public String getSignCertAlias() {
		return signCertAlias;
	}

	public void setSignCertAlias(String signCertAlias) {
		this.signCertAlias = signCertAlias;
	}

	public String getSignCertKeyPassword() {
		return signCertKeyPassword;
	}

	public void setSignCertKeyPassword(String signCertKeyPassword) {
		this.signCertKeyPassword = signCertKeyPassword;
	}

	public String getSignDigestAlgorithm() {
		return signDigestAlgorithm;
	}

	public void setSignDigestAlgorithm(String signDigestAlgorithm) {
		this.signDigestAlgorithm = signDigestAlgorithm;
	}

	public String getEncryptAlgorithm() {
		return encryptAlgorithm;
	}

	public void setEncryptAlgorithm(String encryptAlgorithm) {
		this.encryptAlgorithm = encryptAlgorithm;
	}

	public String getEncryptCertAlias() {
		return encryptCertAlias;
	}

	public void setEncryptCertAlias(String encryptCertAlias) {
		this.encryptCertAlias = encryptCertAlias;
	}
}
