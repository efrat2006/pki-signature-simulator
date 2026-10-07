package com.pki.ca.controllers;

import com.pki.ca.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ca/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/getToken")
    public ResponseEntity<?> getToken(@RequestBody Map<String, String> request){
        String email = request.get("email");

        if(email==null || email.isBlank()){
            return  ResponseEntity.badRequest().body("EMAIL_REQUIRED");
        }

        String token = jwtUtil.generateToken(email);
        return ResponseEntity.ok(Map.of("token", token));
    }

}
