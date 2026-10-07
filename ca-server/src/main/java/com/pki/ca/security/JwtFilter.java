package com.pki.ca.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class JwtFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();


        if(path.endsWith("/auth/getToken") || path.contains("/ws/") || path.contains("/ocsp")){
            chain.doFilter(request, response);
            return;
        }

        //בודק את הכותרות
        String authHeader = httpRequest.getHeader("Authorization");

        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            logger.warn("Missing or invalid Authorization header from {}", httpRequest.getRemoteAddr());
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }

        String token = authHeader.substring(7);

        if(!jwtUtil.validateToken(token)){
            logger.warn("Invalid token {}", httpRequest.getRemoteAddr());
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
            return;
        }

        //לוקח את זהות החותם המאומתת ומעביר אותה באטריביוט לקונטרולרים
        httpRequest.setAttribute("authEmail", jwtUtil.extractEmail(token));
        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
