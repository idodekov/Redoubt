package org.redoubt.protocol.as2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import org.apache.log4j.Logger;
import org.redoubt.api.configuration.ICertificateManager;
import org.redoubt.api.configuration.ICryptoHelper;
import org.redoubt.api.factory.Factory;
import org.redoubt.api.protocol.TransferContext;
import org.redoubt.protocol.BaseProtocol;
import org.redoubt.protocol.ProtocolException;
import org.redoubt.transport.TransportConstants;
import org.redoubt.util.FileSystemUtils;

public class As2Protocol extends BaseProtocol {
    private static final Logger sLogger = Logger.getLogger(As2Protocol.class);

    @Override
    public void receive(TransferContext context) throws ProtocolException {
        As2ProtocolSettings settings = (As2ProtocolSettings) getSettings();
        try {
	        Path productionFolder = settings.getProductionFolder();
	        String fullTarget = (String) context.get(TransportConstants.CONTEXT_FULL_TARGET);
	        Path workFile = Paths.get(fullTarget);
	        
	        FileSystemUtils.checkAs2SizeRestrictions(workFile);
	        
	        @SuppressWarnings("unchecked")
			Map<String,String> headersMap = (Map<String, String>) context.get(TransportConstants.CONTEXT_HEADER_MAP);
	        InternetHeaders headers = new InternetHeaders();
	        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
	        	//data.setHeader(entry.getKey(), entry.getValue());
	        	headers.setHeader(entry.getKey(), entry.getValue());
	        }
	        
	        MimeBodyPart data = new MimeBodyPart(headers, Files.readAllBytes(workFile));
	        
	        data.writeTo(Files.newOutputStream(workFile));
	        
	        data = decryptAndVerify(data);
	        
	        Path productionFile = Paths.get(productionFolder.toString(), workFile.getFileName().toString());
	        
	        Files.copy(data.getInputStream(), productionFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            sLogger.error("An error has occured while unpackaging As2 message. " + e.getMessage(), e);
            throw new ProtocolException(e.getMessage(), e);
        }
    }
    
    private MimeBodyPart decryptAndVerify(MimeBodyPart data) throws Exception {
    	As2ProtocolSettings settings = (As2ProtocolSettings) getSettings();
    	ICertificateManager certificateManager = Factory.getInstance().getCertificateManager();
    	ICryptoHelper cryptoHelper = Factory.getInstance().getCryptoHelper();
    	
    	boolean sign = settings.isSigningEnabled();
    	boolean encrypt = settings.isEncryptionEnabled();

	    if (cryptoHelper.isEncrypted(data)) {
	       	sLogger.debug("Message is encrypted - will attempt to decrypt it.");
	
            X509Certificate receiverCert = certificateManager.getX509Certificate(settings.getEncryptCertAlias());
            PrivateKey receiverKey = certificateManager.getPrivateKey(settings.getEncryptCertAlias(), settings.getEncryptCertKeyPassword().toCharArray());
            data = cryptoHelper.decrypt(data, receiverCert, receiverKey);
            sLogger.debug("Message is decrypted.");
        } else {
        	if(encrypt) {
        		//Encryption is enforced
        		throw new ProtocolException("Encryption is enabled, however the message doesn't appear to be encrypted. Will reject it.");
        	}
        }
	
    		
   		if (cryptoHelper.isSigned(data)) {
   			sLogger.debug("Message is signed - will attempt to verify the signature.");
	
   			X509Certificate senderCert = certificateManager.getX509Certificate(settings.getSignCertAlias());
   			data = cryptoHelper.verify(data, senderCert);
   			sLogger.debug("Signature verified.");
        } else {
        	if(sign) {
        		//Signing is enforced
        		throw new ProtocolException("Signing is enabled, however the message doesn't appear to be signed. Will reject it.");
        	}
        }
   		
   		if (cryptoHelper.isCompressed(data)) {
   			sLogger.debug("Message is compressed - will attempt to decompress it.");
   			data = cryptoHelper.decompress(data);
   			sLogger.debug("Message is decompressed.");
   		}
   		
   		return data;
    }

    @Override
    public void send(TransferContext context) throws ProtocolException {
        As2ProtocolSettings settings = (As2ProtocolSettings) getSettings();
        try {
        	String fullTarget = (String) context.get(TransportConstants.CONTEXT_FULL_TARGET);
        	Path workFile = Paths.get(fullTarget);
        	
        	FileSystemUtils.checkAs2SizeRestrictions(workFile);
        	
        	As2Message message = new As2Message(settings);
        	message.generateMimeData(fullTarget);
        	message.writeMimeDataToFile(workFile);
            
            HttpClientUtils.sendPostRequest(settings, workFile, message.getHeaders());
        }  catch (Exception e) {
            sLogger.error("An error has occured while packaging As2 message. " + e.getMessage(), e);
            throw new ProtocolException(e.getMessage(), e);
        }
        
    }
	
}
