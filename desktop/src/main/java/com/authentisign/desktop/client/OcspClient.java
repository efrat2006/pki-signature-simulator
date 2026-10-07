package com.authentisign.desktop.client;
//
//import com.authentisign.desktop.security.OCSP.OCSPRequestBuilder;
//import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
//import org.bouncycastle.cert.ocsp.*;
//
//public class OCSPController {
//
//    public void validateResponse(byte[] responseByte) throws Exception {
//
//        OCSPResp response = new OCSPResp(responseByte);
//
//        if (response.getStatus() != OCSPRespBuilder.SUCCESSFUL) {
//            throw new Exception("OCSP response not successful");
//        }
//
//        BasicOCSPResp basicResp = (BasicOCSPResp) response.getResponseObject();
//        SingleResp[] responses = basicResp.getResponses();
//        if (responses.length != 0) {
//            throw new Exception("No responses found in OCSP response");
//        }
//        //אימות המספר האקראי כדי לוודא זאת התגובה לפונקציה שלנו
//        byte[] responseNonce = basicResp.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce).getExtnValue().getEncoded();
//
//        CertificateStatus status = singleResp.getCertStatus();
//        if(status == CertificateStatus.GOOD) {
//            System.out.println("The certificate is good");
//        } else if(status instanceof RevokedStatus) {
//            System.out.println("The certificate is revoked");
//        } else{
//            System.out.println("The certificate status is not known");
//        }
//
//
//    }
//}







import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class OcspClient {

    private static final Logger logger = LoggerFactory.getLogger(OcspClient.class);
    private static final int TIMEOUT_MS = 5000;

   //שולחת בקשת OCSP
    public byte[] post(String responderUrl, byte[] encodedRequest) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(responderUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/ocsp-request");
            connection.setRequestProperty("Accept", "application/ocsp-response");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(encodedRequest);
            }

            if (connection.getResponseCode() != 200) {
                logger.warn("OCSP responder HTTP {}", connection.getResponseCode());
                return null;
            }

            try (InputStream is = connection.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, read);
                }
                return bos.toByteArray();
            }
        } catch (Exception e) {
            logger.warn("OCSP request to {} failed: {}", responderUrl, e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}