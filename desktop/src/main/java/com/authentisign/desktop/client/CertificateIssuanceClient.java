package com.authentisign.desktop.client;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


public class CertificateIssuanceClient {

    private static final String ISSUE_URL = "https://localhost:8080/api/ca/issuance/issueCertificateWithCsr";

    //בקשת קבלת התעודה  מהשרת
    public X509Certificate requestCertificate(String email, String csrPem) throws Exception {
        String token = AuthClient.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No auth token available - login first (AuthClient.requestToken).");
        }

        HttpsURLConnection connection = null;
        try {
            URL url = new URL(ISSUE_URL);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            String jsonBody = "{\"email\":\"" + escapeJson(email) + "\","
                    + "\"csrPem\":\"" + escapeJson(csrPem) + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status != 200) {
                String err = readError(connection);
                throw new RuntimeException("Certificate issuance failed (HTTP " + status + "): " + err);
            }

            byte[] certBytes;
            try (InputStream is = connection.getInputStream()) {
                certBytes = is.readAllBytes();
            }
            if (certBytes.length == 0) {
                throw new RuntimeException("Server returned an empty certificate.");
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // קריאת גוף השגיאה שהשרת שולח
    private String readError(HttpsURLConnection connection) {
        try (InputStream es = connection.getErrorStream()) {
            if (es == null)
                return "(no error body)";
            return new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(could not read error body: " + e.getMessage() + ")";
        }
    }

    //התאמת הטקסט לJSON, בPEM יש שורות ותווים מיוחדים שגורמים שגיאות בJSON
    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}