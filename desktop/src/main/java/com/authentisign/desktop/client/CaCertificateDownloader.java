package com.authentisign.desktop.client;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CaCertificateDownloader {

    private static final String CA_CERT_URL = "https://localhost:8080/api/ca/ocsp/ca-certificate";

    //קבלת תעודה החתומה מהשרת
    public X509Certificate fetchCaCertificate() {
        HttpsURLConnection connection = null;
        try{
            URL url = new URL(CA_CERT_URL);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if(connection.getResponseCode() != 200){
                return null;
            }

            byte[] certBytes = connection.getInputStream().readAllBytes();

            //יוצר אובייקט שיודע לקרוא בינארי של תעודות X.509
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch(Exception e){
            return null;
        } finally{
            if(connection != null){
                connection.disconnect();
            }
        }
    }
}
