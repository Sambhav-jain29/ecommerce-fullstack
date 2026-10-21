package com.Sambhav.ecommerce.service;

import java.util.Date;


import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationTime = 1000 * 60 * 60; // 1 hour

    public JwtService() {
        String secret = System.getenv("JWT_SECRET");

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not configured");
        }

        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, String email) {
        Claims claims = extractClaims(token);

        return email.equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }
}

// JWT-->
// We'll use JWT (JSON Web Token) so our React frontend can authenticate with
// the Spring Boot backend on later requests.
// The flow will be:

// 1. User logs in
// Email and password

// 2. Spring Boot verifies credentials
// UserRepository + BCrypt

// 3. Backend issues a signed JWT
// The token contains identity-related claims and an expiry

// 4. Client sends the token with later requests
// Authorization: Bearer <escape>token</escape>

// 5. Backend validates the token
// Allows or rejects access based on authentication and permissions

// Interview concept: Why JWT instead of returning user details alone?
// Returning user details doesn't prove that subsequent requests come from an
// authenticated user. A signed JWT gives the backend a way to verify the
// client's authentication on each request, provided the token is valid and
// unexpired.means that the backend can trust that the request is coming from an
// authenticated user, without needing to check the user's credentials again.
// This is especially useful in stateless applications where the server does not
// maintain session state for each user. The JWT serves as a self-contained
// token that carries the necessary information to authenticate and authorize
// the user, allowing for secure and efficient communication between the client
// and server.
// A JWT is signed, not encrypted by default. We must not put passwords or other
// secrets inside it.