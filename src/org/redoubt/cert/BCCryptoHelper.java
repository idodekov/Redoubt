package org.redoubt.cert;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.RecipientId;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMECompressedGenerator;
import org.bouncycastle.mail.smime.SMIMECompressedParser;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedParser;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedParser;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.bouncycastle.operator.OutputCompressor;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.util.encoders.Base64;
import org.redoubt.api.configuration.ICryptoHelper;
import org.redoubt.protocol.ProtocolException;
import org.redoubt.protocol.as2.As2HeaderDictionary;

public class BCCryptoHelper implements ICryptoHelper {
	
	public void init() {
        Security.addProvider(new BouncyCastleProvider());

        MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed");
        CommandMap.setDefaultCommandMap(mc);
    }

    public void deinit() {
    }
	
    public boolean isEncrypted(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        if (baseType.equalsIgnoreCase("application/pkcs7-mime")) {
            String smimeType = contentType.getParameter("smime-type");

            return ((smimeType != null) && smimeType.equalsIgnoreCase("enveloped-data"));
        }

        return false;
    }

    public boolean isSigned(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        return baseType.equalsIgnoreCase("multipart/signed");
    }
    
    public boolean isCompressed(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        if (baseType.equalsIgnoreCase("application/pkcs7-mime")) {
            String smimeType = contentType.getParameter("smime-type");

            return ((smimeType != null) && smimeType.equalsIgnoreCase("compressed-data"));
        }

        return false;
    }

    public String calculateMIC(Path file, String digestAlg) throws Exception {
    	String micAlg = null;
    	
    	if(DIGEST_SHA1.equals(digestAlg)) {
        	micAlg = "sha1";
        } else if(DIGEST_MD5.equals(digestAlg)) {
        	micAlg = "md5";
        } else {
			throw new ProtocolException("Unknown digest algorithm [" + digestAlg + "]. Message will not be processed.");
		}
    	
    	MessageDigest md = MessageDigest.getInstance(micAlg, BouncyCastleProvider.PROVIDER_NAME);
    	try (InputStream is = Files.newInputStream(file)) {
    	  DigestInputStream dis = new DigestInputStream(is, md);
    	  
    	  /* Read stream to EOF */
    	  byte[] buf = new byte[4096];

          while (dis.read(buf) >= 0) {
          }
    	}
    	byte[] digest = md.digest();
    	String micString = new String(Base64.encode(digest));
        StringBuffer micResult = new StringBuffer(micString);
        micResult.append(", ").append(micAlg);
    	
        return micResult.toString();
    }

    public MimeBodyPart decrypt(MimeBodyPart part, X509Certificate cert, PrivateKey key) throws Exception {
        // Make sure the data is encrypted
        if (!isEncrypted(part)) {
            throw new GeneralSecurityException("Content-Type indicates data isn't encrypted");
        }

        // Get the recipient object for decryption
        RecipientId recId = new JceKeyTransRecipientId(cert);
        
        SMIMEEnvelopedParser parser = new SMIMEEnvelopedParser(part);

        RecipientInformationStore recipients = parser.getRecipientInfos();
        RecipientInformation recipient = recipients.get(recId);

        if (recipient == null) {
            throw new GeneralSecurityException("Certificate does not match part signature");
        }
        
        MimeBodyPart res = SMIMEUtil.toMimeBodyPart(recipient.getContentStream(new JceKeyTransEnvelopedRecipient(key).setProvider(BouncyCastleProvider.PROVIDER_NAME)));

        return res;
    }
    
    public MimeBodyPart encrypt(MimeBodyPart part, X509Certificate x509Cert, String algorithm) throws Exception {
        ASN1ObjectIdentifier encAlg = null;
        
        if(CRYPT_RC2.equals(algorithm)) {
        	encAlg = CMSAlgorithm.RC2_CBC;
        } else if(CRYPT_3DES.equals(algorithm)) {
        	encAlg = CMSAlgorithm.DES_EDE3_CBC;
        } else if(CRYPT_CAST5.equals(algorithm)) {
        	encAlg = CMSAlgorithm.CAST5_CBC;
        } else if(CRYPT_IDEA.equals(algorithm)) { 
        	encAlg = CMSAlgorithm.IDEA_CBC;
        } else {
			throw new ProtocolException("Unknown encryption algorithm [" + algorithm + "]. Message will not be processed.");
		}

        SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
        gen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(x509Cert).setProvider(BouncyCastleProvider.PROVIDER_NAME));
        gen.setContentTransferEncoding(As2HeaderDictionary.TRANSFER_ENCODING_BINARY);
        MimeBodyPart encData = gen.generate(part, new JceCMSContentEncryptorBuilder(encAlg).setProvider(BouncyCastleProvider.PROVIDER_NAME).build());
        
        return encData;
    }

    public MimeBodyPart sign(MimeBodyPart part, X509Certificate cert, PrivateKey key, String digest) throws Exception {
        String digestAlg = null;
        
        if(DIGEST_SHA1.equals(digest)) {
        	digestAlg = "SHA1withRSA";
        } else if(DIGEST_MD5.equals(digest)) {
        	digestAlg = "MD5withRSA";
        } else {
			throw new ProtocolException("Unknown digest algorithm [" + digest + "]. Message will not be processed.");
		}
        
        List<X509Certificate> certList = new ArrayList<X509Certificate>();

        certList.add(cert);

        @SuppressWarnings("rawtypes")
		Store certs = new JcaCertStore(certList);
        
        ASN1EncodableVector         signedAttrs = new ASN1EncodableVector();
        SMIMECapabilityVector       caps = new SMIMECapabilityVector();

        caps.addCapability(SMIMECapability.dES_EDE3_CBC);
        caps.addCapability(SMIMECapability.rC2_CBC, 128);
        caps.addCapability(SMIMECapability.dES_CBC);
        
        signedAttrs.add(new SMIMECapabilitiesAttribute(caps));
        
        IssuerAndSerialNumber issAndSer = new IssuerAndSerialNumber(new X500Name(cert.getIssuerDN().getName()), cert.getSerialNumber());
        signedAttrs.add(new SMIMEEncryptionKeyPreferenceAttribute(issAndSer));
        
        SMIMESignedGenerator gen = new SMIMESignedGenerator();
        gen.setContentTransferEncoding(As2HeaderDictionary.TRANSFER_ENCODING_BINARY);
        gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).
        		setSignedAttributeGenerator(new AttributeTable(signedAttrs)).build(digestAlg, key, cert));
        gen.addCertificates(certs);
        MimeMultipart mm = gen.generate(part);

        MimeBodyPart tempBody = new MimeBodyPart();
        tempBody.setContent(mm);
        tempBody.setHeader(As2HeaderDictionary.CONTENT_TYPE, mm.getContentType());

        return tempBody;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
	public MimeBodyPart verify(MimeBodyPart part, X509Certificate cert) throws Exception {
        // Make sure the data is signed
        if (!isSigned(part)) {
            throw new GeneralSecurityException("Content-Type indicates data isn't signed");
        }
        
        MimeMultipart mainParts = (MimeMultipart) part.getContent();
        SMIMESignedParser parser = new SMIMESignedParser(new JcaDigestCalculatorProviderBuilder().build(), mainParts);
        Store certs = parser.getCertificates();
        
        SignerInformationStore signers = parser.getSignerInfos();

        Collection<SignerInformation> signersCollection = signers.getSigners();
        Iterator<SignerInformation> it = signersCollection.iterator();
        
        while (it.hasNext()) {
        	SignerInformation signer = (SignerInformation)it.next();
            Collection certCollection = certs.getMatches(signer.getSID());

            Iterator certIt = certCollection.iterator();
            X509Certificate resolvedCert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate((X509CertificateHolder)certIt.next());
            
            if(!resolvedCert.equals(cert)) {
            	throw new SignatureException("Message is signed with a certificate, that is not configured for this party!");
            }
            
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build(resolvedCert))) {
            	throw new SignatureException("Signature verification failed!");
            }
        }
        
        SMIMESigned signedPart = new SMIMESigned(mainParts);
        return signedPart.getContent();
    }

    protected InputStream trimCRLFPrefix(byte[] data) {
        ByteArrayInputStream bIn = new ByteArrayInputStream(data);

        int scanPos = 0;
        int len = data.length;

        while (scanPos < (len - 1)) {
            if (new String(data, scanPos, 2).equals("\r\n")) {
                bIn.read();
                bIn.read();
                scanPos += 2;
            } else {
                return bIn;
            }
        }

        return bIn;
    }

	@Override
	public MimeBodyPart compress(MimeBodyPart part, String alg) throws Exception {
		OutputCompressor compressor = null;
		
		if(COMPRESS_ZLIB.equals(alg)) {
			compressor = new ZlibCompressor();
		} else {
			throw new ProtocolException("Unknown compression algorithm [" + alg + "]. Message will not be processed.");
		}
		
		SMIMECompressedGenerator  gen = new SMIMECompressedGenerator();
    	gen.setContentTransferEncoding(As2HeaderDictionary.TRANSFER_ENCODING_BINARY);
    	MimeBodyPart dataBP = gen.generate(part, compressor);
		return dataBP;
	}

	@Override
	public MimeBodyPart decompress(MimeBodyPart part) throws Exception {
		SMIMECompressedParser parser = new SMIMECompressedParser(part);
        MimeBodyPart res = SMIMEUtil.toMimeBodyPart(parser.getContent(new ZlibExpanderProvider()));
        return res;
	}

}