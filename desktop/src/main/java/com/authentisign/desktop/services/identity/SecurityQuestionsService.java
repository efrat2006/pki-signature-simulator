package com.authentisign.desktop.services.identity;

import com.authentisign.desktop.client.SecurityQuestionsClient;

import java.util.List;
import java.util.Map;

public class SecurityQuestionsService {

    private final SecurityQuestionsClient questionsClient = new SecurityQuestionsClient();

    public List<String> getAllQuestions(){
        return questionsClient.fetchAllQuestions();
    }

    public boolean registerWithQuestions(String firstName, String lastName, String email, String birthDate, byte[] idFront, byte[] idBack, Map<String, String> answers){
        return questionsClient.registerWithQuestions(firstName, lastName, email, birthDate, idFront, idBack, answers);
    }

    public List<String> fetchUserQuestions(String email) {
        return questionsClient.fetchUserQuestions(email);
    }

    public boolean verifyAnswers(String email, Map<String, String> answers) {
        return questionsClient.verifyAnswers(email, answers);
    }
}