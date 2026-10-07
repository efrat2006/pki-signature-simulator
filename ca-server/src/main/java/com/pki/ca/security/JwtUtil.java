package com.pki.ca.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final long EXPIRATION = 1000*60*30;

    //יצירת טוקן חדש
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() +  EXPIRATION))
                .signWith(key)
                //בניית הטוקן
                .compact();
    }

    //שליפת האימייל מתוך הטוקן
    public String extractEmail(String token){
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                //פורס את הטוקן
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    //בודק אם הטוקן תקין
    public boolean validateToken(String token) {
        try{
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
                    return true;
        } catch(Exception e){
            return false;
        }
    }

}
