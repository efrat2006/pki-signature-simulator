package com.authentisign.desktop.client;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SignerClient {

    private static final String BASE_URL = "https://localhost:8080/api/ca";

    public boolean hasAccount(String email){
        try{
            URL url = new URL(BASE_URL + "/signer/exists?email="+email);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + AuthClient.getToken());

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            return Boolean.parseBoolean(in.readLine());
        } catch (Exception e){
            System.out.println("Error" + e.getMessage());
            return false;
        }
    }

    public boolean revokeCertificate(String email, String serialNumber, String reasonCode, String reasonText){
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + "/signer/certificate/revoke");
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + AuthClient.getToken());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);

            String jsonBody = "{"
                    + "\"email\":\"" + escapeJson(email) + "\","
                    + "\"serialNumber\":\"" + escapeJson(serialNumber) + "\","
                    + "\"reasonCode\":\"" + escapeJson(reasonCode) + "\","
                    + "\"reasonText\":\"" + escapeJson(reasonText) + "\"}";

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status == 200) {
                return true;
            }
            System.out.println("Revocation failed (HTTP " + status + "): " + readError(connection));
            return false;

        } catch (Exception e) {
            System.out.println("Error revoking certificate: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readError(HttpsURLConnection connection) {
        try (InputStream es = connection.getErrorStream()) {
            if (es == null) return "(no error body)";
            return new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(could not read error body)";
        }
    }

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
