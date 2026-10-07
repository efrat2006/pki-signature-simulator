package com.authentisign.desktop.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SecurityQuestionsClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_URL = "https://localhost:8080/api/ca";
    private static final String QUESTIONS_URL = BASE_URL + "/securityQuestions";
    private static final String ANSWERS_URL = BASE_URL + "/securityAnswers";
    private static final String SIGNER_URL = BASE_URL + "/signer";

    //קבלת כל שאלות האבטחה
    public List<String> fetchAllQuestions() {
        return getStringList(QUESTIONS_URL + "/getAllSecurityQuestions");
    }


    //הרשמה עם שאלות אבטחה
    public boolean registerWithQuestions(String firstName, String lastName, String email,
                                         String birthDate,
                                         byte[] idCardFront,
                                         byte[] idCardBack,
                                         Map<String, String> answers) {
        try {
            URL url = new URL(SIGNER_URL + "/registerWithQuestions");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + AuthClient.getToken());
            connection.setDoOutput(true);

            String idCardFrontBase64 = Base64.getEncoder().encodeToString(idCardFront);
            String idCardBackBase64 = Base64.getEncoder().encodeToString(idCardBack);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("firstName", firstName);
            requestBody.put("lastName", "מני");
            requestBody.put("email", email);
            requestBody.put("birthDate", birthDate);
            requestBody.put("idCardFrontBase64", idCardFrontBase64);
            requestBody.put("idCardBackBase64", idCardBackBase64);
            requestBody.put("answers", answers);

            String json = objectMapper.writeValueAsString(requestBody);
            System.out.println("Sending: " + json);

            // שלח
            OutputStream os = connection.getOutputStream();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == 201 || responseCode == 200) {
                System.out.println("Registration successful!");
                //  קרא את התגובה
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    System.out.println("Server Response: " + response.toString());
                }
                return true;
            } else {
                System.err.println("Registration failed!");
                try (BufferedReader errorIn = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorIn.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    System.err.println("Error Response: " + errorResponse.toString());
                }
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    //שליפת השאלות שהמשתמש בחר
    public List<String> fetchUserQuestions(String email) {
        try {
            String q = URLEncoder.encode(email, StandardCharsets.UTF_8);
            return getStringList(QUESTIONS_URL + "/user?email=" + q);
        } catch (Exception e) {
            System.out.println("Error" + e.getMessage());
            return new ArrayList<>();
        }
    }


    //אימות תשובות
    public boolean verifyAnswers(String email, Map<String, String> answers) {
        try {
            URL url = new URL(BASE_URL + "/securityAnswers/verify");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + AuthClient.getToken());

            String json = buildAnswersJson(email, answers);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            System.err.println("אימות תשובות - שגיאה: " + e);
            return false;
        }
    }

    //בנייית JSON מהתשובות
    private String buildAnswersJson(String email, Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"email\":\"").append(esc(email)).append("\",\"answers\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : answers.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private List<String> getStringList(String endpoint) {
        List<String> result = new ArrayList<>();
        try {
            URL url = new URL(endpoint);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            connection.setRequestProperty("Authorization", "Bearer " + AuthClient.getToken());

            int code = connection.getResponseCode();
            System.out.println("GET " + endpoint + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) sb.append(line);
            }
            Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(sb.toString());
            while (m.find()) result.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}