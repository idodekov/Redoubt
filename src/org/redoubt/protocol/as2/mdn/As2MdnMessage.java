package org.redoubt.protocol.as2.mdn;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;
import org.redoubt.api.configuration.ICryptoHelper;
import org.redoubt.api.factory.Factory;
import org.redoubt.api.protocol.IMdnMonitor;
import org.redoubt.api.protocol.IProtocolSettings;
import org.redoubt.application.VersionInformation;
import org.redoubt.protocol.ProtocolException;
import org.redoubt.protocol.as2.As2HeaderDictionary;
import org.redoubt.protocol.as2.As2Message;
import org.redoubt.util.Utils;

public class As2MdnMessage extends As2Message {
	private static final Logger sLogger = Logger.getLogger(As2MdnMessage.class);
	
    private String originalMessageId;
    private String originalMessageDate;
    private String originalSubject;
    private String text;
    
    public As2MdnMessage(As2Message message) {
    	super();
    	mic = message.getMic();
		fromAddress = message.getToAddress();
		toAddress = message.getFromAddress();
		asynchronousMdnUrl = message.getAsynchronousMdnUrl();
		mdnType = message.getMdnType();
		mdnSigningAlgorithm = message.getMdnSigningAlgorithm();
		requestSignedMdn = message.isRequestSignedMdn();
		mdnRequested = message.isMdnReqested();
		localParty = message.getLocalParty();
		remoteParty = message.getRemoteParty();
		originalMessageDate = message.getMessageDate();
		originalSubject = message.getSubject();
		disposition = message.getDisposition();
		originalMessageId = message.getMessageId();
	}
    
    
	public As2MdnMessage(Path payload, InternetHeaders internetHeaders) throws MessagingException, IOException {
		super(payload, internetHeaders);
	}


	public As2MdnMessage(byte[] content, InternetHeaders internetHeaders) throws MessagingException {
		super(content, internetHeaders);
	}

	@Override
	public void packageMessage(IProtocolSettings settings) throws Exception {
		sLogger.debug("Packaging As2 MDN message...");
        MimeMultipart multipart = new MimeMultipart();
        multipart.setSubType(As2HeaderDictionary.MIME_SUBTYPE_REPORT);
        
        MimeBodyPart textPart = createTextPart();
        multipart.addBodyPart(textPart);

        MimeBodyPart dispositionPart = createDispositionPart();
        multipart.addBodyPart(dispositionPart);

        data.setContent(multipart);
        data.setHeader(As2HeaderDictionary.CONTENT_TYPE, multipart.getContentType());
            
        signCertAlias = getLocalParty().getSignCertAlias();
    	signCertKeyPassword = getLocalParty().getSignCertKeyPassword();
    	
    	messageDate = Utils.createTimestamp();
		fromEmail = Utils.generateMessageSender(fromAddress);
    	subject = "This is an AS2 MDN message generated by " + VersionInformation.APP_NAME + ".";
    	messageId = Utils.generateMessageID(fromAddress);
    	
        headers.put(As2HeaderDictionary.AS2_FROM, fromAddress);
        headers.put(As2HeaderDictionary.AS2_TO, toAddress);
        headers.put(As2HeaderDictionary.AS2_VERSION, As2HeaderDictionary.AS2_VERSION_1_1);
        headers.put(As2HeaderDictionary.CONNECTION, "close");
        headers.put(As2HeaderDictionary.USER_AGENT, As2HeaderDictionary.USER_AGENT_REDOUBT);
        headers.put(As2HeaderDictionary.ACCEPT_ENCODING, "gzip,deflate");
        headers.put(As2HeaderDictionary.MIME_VERSION, As2HeaderDictionary.MIME_VERSION_1_0);
        headers.put(As2HeaderDictionary.DATE, messageDate);
        headers.put(As2HeaderDictionary.MESSAGE_ID, messageId);
        headers.put(As2HeaderDictionary.FROM, fromEmail);
        headers.put(As2HeaderDictionary.SUBJECT, subject);
    		
    	if(isRequestSignedMdn()) {
    		setSign(true);
    		setEncrypt(false);
    		setCompress(false);
    		setSignDigestAlgorithm(getMdnSigningAlgorithm());
    		secure();
    	}
    	
    	String contentType = Utils.normalizeContentType(data.getContentType());
		headers.put(As2HeaderDictionary.CONTENT_TYPE, contentType);
	}

	@Override
	public void unpackageMessage(IProtocolSettings settings) throws Exception {
		sLogger.debug("Unpackaging As2 MDN message...");
		
		fromAddress= headers.get(As2HeaderDictionary.AS2_FROM);
		if(Utils.isNullOrEmptyTrimmed(fromAddress)) {
			throw new ProtocolException(As2HeaderDictionary.AS2_FROM + " header is empty. Unknown sender - rejecting the message.");
		}
		toAddress = headers.get(As2HeaderDictionary.AS2_TO);
		if(Utils.isNullOrEmptyTrimmed(toAddress)) {
			throw new ProtocolException(As2HeaderDictionary.AS2_TO + " header is empty. Unknown sender - rejecting the message.");
		}
		
		if(fromAddress.equalsIgnoreCase(toAddress)) {
			throw new ProtocolException(As2HeaderDictionary.AS2_TO + " header can't be equal to [" + As2HeaderDictionary.AS2_FROM + "].");
		}
		
		messageId = headers.get(As2HeaderDictionary.MESSAGE_ID);
		if(Utils.isNullOrEmptyTrimmed(messageId)) {
			throw new ProtocolException(As2HeaderDictionary.MESSAGE_ID + " header can't be empty. Unknown message id - rejecting the message.");
		}
		
		fromEmail = headers.get(As2HeaderDictionary.FROM);
		messageDate = headers.get(As2HeaderDictionary.DATE);
		subject = headers.get(As2HeaderDictionary.SUBJECT);
		
		resolveParties(fromAddress, toAddress);
		
		if(!confirmThisIsMdn()) {
			sLogger.debug("This is not an MDN message - this is a regular As2 message.");
			throw new MdnException();
		}
		
		signCertAlias = remoteParty.getSignCertAlias();
		decryptAndVerify(false, localParty.isRequestSignedMdn());
		
		readDispositionPart();
		
		IMdnMonitor monitor = Factory.getInstance().getMdnMonitor();
		if(monitor.isMessageRegistered(mic)) {
			As2Message originalMessage = (As2Message) monitor.getMessage(mic);
			
			if(originalMessage.getMessageId().equals(originalMessageId)) {
				sLogger.info("Message with Message-Id: " + originalMessageId + " and MIC: " + mic + " has been confirmed with a MDN.");
				monitor.confirmAndDeregisterMessage(mic);
			} else {
				sLogger.error("Received an MDN, which is not expected. MIC: " + mic + ". From: " + 
						fromAddress + ". To: " + toAddress + ". Original-Message-Id: " + originalMessageId);
			}
		} else {
			sLogger.error("Received an MDN, which is not expected. MIC: " + mic + ". From: " + 
					fromAddress + ". To: " + toAddress + ". Original-Message-Id: " + originalMessageId);
		}
		
		sLogger.debug("As2 MDN message successfully unpackaged.");
	}
	
	private boolean confirmThisIsMdn() throws Exception {
		ICryptoHelper cryptoHelper = Factory.getInstance().getCryptoHelper();
		if(cryptoHelper.isSigned(data)) {
			MimeMultipart mainParts = (MimeMultipart) data.getContent();
			
			BodyPart part1 = mainParts.getBodyPart(0);
			String part1ContentType = part1.getContentType();
			if(part1ContentType.contains("disposition-notification")) {
				return true;
			}
			
			BodyPart part2 = mainParts.getBodyPart(1);
			String part2ContentType = part2.getContentType();
			if(part2ContentType.contains("disposition-notification")) {
				return true;
			}
			
		} else {
			if(data.getContentType().contains("disposition-notification")) {
				return true;
			}
		}
		
		return false;
	}
	
	protected void readDispositionPart() throws IOException, MessagingException {
		MimeMultipart mainParts = (MimeMultipart) data.getContent();
		int partCount = mainParts.getCount();
		
		for (int i = 0; i < partCount; i++) {
			BodyPart part = mainParts.getBodyPart(i);
			
			if (part.isMimeType(As2HeaderDictionary.MIME_TYPE_DISPOSITION_NOTIFICATION)) {
	            try {
	                InternetHeaders headers = new InternetHeaders(part.getInputStream());
	                originalMessageId = headers.getHeader("Original-Message-ID", ", ");
	                disposition.setStatus((headers.getHeader("Disposition", ", ")));
	                mic = headers.getHeader("Received-Content-MIC", ", ");
	            } catch (IOException ioe) {
	                throw new MessagingException("Error parsing disposition notification: " + ioe.getMessage());
	            }
	        } else if(part.isMimeType(As2HeaderDictionary.MIME_TYPE_TEXT_PLAIN)) {
                text = part.getContent().toString();
            }
		}
	}

	protected MimeBodyPart createDispositionPart() throws IOException, MessagingException {
        MimeBodyPart dispositionPart = new MimeBodyPart();

        InternetHeaders dispValues = new InternetHeaders();
        dispValues.setHeader(As2HeaderDictionary.ORIGINAL_RECIPIENT, "rfc822; " + fromAddress);
        dispValues.setHeader(As2HeaderDictionary.FINAL_RECIPIENT, "rfc822; " + fromAddress);
        dispValues.setHeader(As2HeaderDictionary.ORIGINAL_MESSAGE_ID, originalMessageId);
        dispValues.setHeader(As2HeaderDictionary.DISPOSITION, disposition.getStatus());
        dispValues.setHeader(As2HeaderDictionary.RECEIVED_CONTENT_MIC, mic);

        @SuppressWarnings("rawtypes")
        Enumeration dispEnum = dispValues.getAllHeaderLines();
        StringBuffer dispData = new StringBuffer();

        while (dispEnum.hasMoreElements()) {
            dispData.append((String) dispEnum.nextElement()).append("\r\n");
        }

        dispData.append("\r\n");

        String dispText = dispData.toString();
        dispositionPart.setContent(dispText, As2HeaderDictionary.MIME_TYPE_DISPOSITION_NOTIFICATION);
        dispositionPart.setHeader(As2HeaderDictionary.CONTENT_TYPE, As2HeaderDictionary.MIME_TYPE_DISPOSITION_NOTIFICATION);
        dispositionPart.setHeader(As2HeaderDictionary.CONTENT_TRANSFER_ENCODING, As2HeaderDictionary.TRANSFER_ENCODING_7BIT);

        return dispositionPart;
    }
	
	protected MimeBodyPart createTextPart() throws IOException, MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();
        
        text = "The message sent to Recipient [" + fromAddress + "] on [" + originalMessageDate + "]\r\n" + 
        "with Subject [" + originalSubject + "] and Id [" + originalMessageId + "] has been received.\r\n" +
        "In addition, the sender of the message, [" + toAddress + "] was authenticated\r\n" + 
        "as the originator of the message.\r\n" +
        "This is not a guarantee that the message has been completely processed or\r\n" +
        "understood by the receiving party.\r\n";
        
        textPart.setContent(text, As2HeaderDictionary.MIME_TYPE_TEXT_PLAIN_US_ASCII);
        textPart.setHeader(As2HeaderDictionary.CONTENT_TYPE, As2HeaderDictionary.MIME_TYPE_TEXT_PLAIN_US_ASCII);
        textPart.setHeader(As2HeaderDictionary.CONTENT_TRANSFER_ENCODING, As2HeaderDictionary.TRANSFER_ENCODING_7BIT);

        return textPart;
    }

	public String getOriginalMessageId() {
		return originalMessageId;
	}

	public void setOriginalMessageId(String originalMessageId) {
		this.originalMessageId = originalMessageId;
	}
}
