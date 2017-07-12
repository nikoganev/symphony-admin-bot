package com.symphony.adminbot.model.signup.setup;

import com.symphony.adminbot.config.BotConfig;
import com.symphony.adminbot.model.signup.PartnerState;
import com.symphony.clients.AttachmentsClient;
import com.symphony.clients.SecurityClient;
import com.symphony.adminbot.commons.BotConstants;
import com.symphony.adminbot.util.file.FileUtil;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.symphonyoss.symphony.pod.invoker.ApiException;
import org.symphonyoss.symphony.pod.model.CompanyCert;
import org.symphonyoss.symphony.pod.model.CompanyCertAttributes;
import org.symphonyoss.symphony.pod.model.CompanyCertStatus;
import org.symphonyoss.symphony.pod.model.CompanyCertType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;

/**
 * Created by nick.tarsillo on 7/2/17.
 */
public class PartnerCompanyCertSetup {
  private static final Logger LOG = LoggerFactory.getLogger(PartnerCompanyCertSetup.class);

  private AttachmentsClient attachmentsClient;
  private SecurityClient securityClient;

  private int botId = -1;

  public PartnerCompanyCertSetup(SecurityClient securityClient, AttachmentsClient attachmentsClient){
    this.securityClient = securityClient;
    this.attachmentsClient = attachmentsClient;
  }

  /**
   * Generates cert and registers it on the pod.
   * @param name name for the cert
   * @param password password for the cert
   * @return the common name of the cert
   */
  public String generateAndRegisterCert(String name, String password) {
    try {
      String commonName = getCommonName(name);
      KeyPair keys = createKeyPair("RSA", 2048);

      X509Certificate certificate = generateCertificate(
          commonName, keys, BotConstants.VALID_DURATION);
      writeCert(commonName, certificate, keys,
          System.getProperty(BotConfig.P12_DIR), password.toCharArray());

      CompanyCert companyCert = new CompanyCert();
      companyCert.setPem(convertCertificateToPEM(certificate));

      CompanyCertAttributes companyCertAttributes = new CompanyCertAttributes();
      companyCertAttributes.setName(BotConstants.CERT_NAME);

      CompanyCertStatus status = new CompanyCertStatus();
      status.setType(CompanyCertStatus.TypeEnum.TRUSTED);
      companyCertAttributes.setStatus(status);

      CompanyCertType certType = new CompanyCertType();
      certType.setType(CompanyCertType.TypeEnum.USERSIGNING);
      companyCertAttributes.setType(certType);
      companyCert.attributes(companyCertAttributes);

      securityClient.createCert(companyCert);

      return commonName;
    } catch (NoSuchProviderException | IOException | CertificateException |
        NoSuchAlgorithmException | ApiException e) {
      LOG.error("Error occurred when creating welcome package: ", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  /**
   * Uploads certs as a zip attachment to partner IM
   * @param partnerState the current state of the partner in the sign up process
   */
  public void uploadCerts(PartnerState partnerState){
    try {
      String path = System.getProperty(BotConfig.P12_DIR);
      String outputPath = path + partnerState.getPartner().getEmail() + ".zip";
      Set<String> certPaths = new HashSet<>();
      certPaths.add(path + partnerState.getPartnerSignUpForm().getBotEmail().split("@")[0] + ".pkcs12");
      certPaths.add(path + partnerState.getPartnerSignUpForm().getAppName() + ".pkcs12");
      File zip = FileUtil.zipFiles(outputPath, certPaths);

      Set<File> attachments = new HashSet<>();
      attachments.add(zip);

      partnerState.setCertAttachmentInfo(attachmentsClient.uploadAttachments(partnerState.getPartnerIM(), attachments));
    } catch (Exception e){
      LOG.error("Error occurred when uploading attachments: ", e);
      throw new InternalServerErrorException(BotConstants.INTERNAL_ERROR);
    }
  }

  /**
   * Gets a default bot username
   * @return a default bot username
   */
  public String getDefaultBotUsername() {
    try {
      String path = System.getProperty(BotConfig.BOT_SIGNUP_ID);
      if (botId == -1) {
        botId = Integer.parseInt(FileUtil.readFile(path));
      } else {
        botId += 1;
        FileUtil.writeFile("" + botId, path);
      }
    } catch (IOException e) {
      LOG.error("Failed to retrieve bot sign up id: ", e);
    }

    return BotConstants.BOT_USERNAME + botId;
  }

  /**
   * Converts name to common name (camelCase)
   * @param name the name to convert
   * @return converted name
   */
  private String getCommonName(String name){
    String commonName;
    if (StringUtils.isNotBlank(name)) {
      commonName = WordUtils.capitalize(name);
      commonName = commonName.replaceFirst("" + name.charAt(0),
          "" + Character.toLowerCase(name.charAt(0)));
      commonName = commonName.replaceAll(" ", "");
    } else {
      throw new BadRequestException("Name cannot be blank!");
    }

    return commonName;
  }

  /**
   * Converts cert to PEM key
   * @param signedCertificate the cert to convert
   * @return the PEM
   */
  private String convertCertificateToPEM(X509Certificate signedCertificate) throws
      IOException {
    StringWriter signedCertificatePEMDataStringWriter = new StringWriter();
    JcaPEMWriter pemWriter = new JcaPEMWriter(signedCertificatePEMDataStringWriter);
    pemWriter.writeObject(signedCertificate);
    pemWriter.close();
    return signedCertificatePEMDataStringWriter.toString();
  }

  /**
   * Generate an X509 cert for use as the keystore cert chain
   * @return the cert
   */
  private X509Certificate generateCertificate(String name, KeyPair keys, int validDuration)
      throws CertificateException {
    X509Certificate cert;

    // backdate the start date by a day
    Calendar start = Calendar.getInstance();
    start.add(Calendar.DATE, -1);
    java.util.Date startDate = start.getTime();

    // what is the end date for this cert's validity?
    Calendar end = Calendar.getInstance();
    end.add(Calendar.DATE, validDuration);
    java.util.Date endDate = end.getTime();

    try {
      X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
          new X500Principal("CN=" + name),
          BigInteger.ONE,
          startDate, endDate,
          new X500Principal("CN=" + name),
          keys.getPublic());

      AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA");
      AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
      AsymmetricKeyParameter keyParam = PrivateKeyFactory.createKey(keys.getPrivate().getEncoded());
      ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(keyParam);
      X509CertificateHolder certHolder = certBuilder.build(sigGen);

      // now lets convert this thing back to a regular old java cert
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      InputStream certIs = new ByteArrayInputStream(certHolder.getEncoded());
      cert = (X509Certificate) cf.generateCertificate(certIs);
      certIs.close();
    } catch(CertificateException ce) {
      LOG.error("CertificateException creating or validating X509 certificate for user: " + ce);
      throw new CertificateException("Cert generation failed.");
    } catch(Exception ex) {
      LOG.error("Unknown exception creating or validating X509 certificate for user : " + ex);
      throw new InternalError("Internal error.");
    }

    return cert;
  }

  /**
   * Writes cert to file
   * @param alias the alias to save the cert as
   * @param certificate the certificate to save
   * @param keys the key pair for the cert
   * @param path the path to save the cert to
   * @param password the cert password
   */
  private void writeCert(String alias, Certificate certificate, KeyPair keys, String path, char[] password){
    Certificate[] outChain = {certificate};
    try {
      KeyStore outStore = KeyStore.getInstance("PKCS12");
      outStore.load(null, password);
      outStore.setKeyEntry(alias, keys.getPrivate(), password, outChain);
      OutputStream outputStream = new FileOutputStream(path + alias + ".pkcs12");
      outStore.store(outputStream, password);
      outputStream.flush();
      outputStream.close();
    } catch (KeyStoreException e) {
      e.printStackTrace();
    } catch (CertificateException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates a key pair
   * @param encryptionType the encryption type
   * @param byteCount the byte count
   * @return a new key pair
   */
  private KeyPair createKeyPair(String encryptionType, int byteCount)
      throws NoSuchProviderException, NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
        encryptionType, BouncyCastleProvider.PROVIDER_NAME);
    keyPairGenerator.initialize(byteCount);
    KeyPair keyPair = keyPairGenerator.genKeyPair();
    return keyPair;
  }

}
