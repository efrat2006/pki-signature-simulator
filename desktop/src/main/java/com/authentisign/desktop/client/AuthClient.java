package com.authentisign.desktop.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuthClient {

    private static final String BASE_URL = "https://localhost:8080/api/ca/auth";
    private static final long TOKEN_TTL_MS = 1000L * 60 * 30;

    private static String token;
    private static long issuedAtMs;

    //קבלת טוקן
    public static boolean requestToken(String email){
        try{
            System.out.println("Requesting token for: " + email);
            URL url = new URL(BASE_URL + "/getToken");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String json = "{\"email\":\""+email+"\"}";
            try (OutputStream os = connection.getOutputStream()){
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if(connection.getResponseCode() == 200){
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }

                String body  =response.toString();
                token = body.split("\"token\":\"")[1].split("\"")[0];
                issuedAtMs = System.currentTimeMillis();
                System.out.println("Token received");
                return true;
            }
            System.out.println("Token failed: " + connection.getResponseCode());
            return false;

        }
        catch(Exception e){
            System.err.println("Error requesting token: "+e.getMessage());
            System.out.println("Token request failed: ");
            return false;
        }
    }

    //קבלת הטוקן
    public static String getToken(){
        return token;
    }

    //בדיקה אם קיים טוקן קיים
    public static boolean isTokenValid(){
        return token != null && !token.isBlank()
                && (System.currentTimeMillis() - issuedAtMs) < TOKEN_TTL_MS;
    }
    //ניקוי הטוקן
    public static void clear(){
        token = null;
        issuedAtMs = 0;
    }
}
