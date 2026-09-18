package com.example.stubackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${app.jwt-secret:}") String configured) {
        String secret = configured;
        if (secret == null || secret.isBlank()) {
            Path local = Path.of("jwt-secret.local");
            try { if (Files.exists(local)) secret = Files.readString(local).trim(); else { byte[] b = new byte[48]; new SecureRandom().nextBytes(b); secret = Base64.getEncoder().encodeToString(b); Files.writeString(local, secret); } }
            catch (IOException e) { throw new IllegalStateException("无法初始化 JWT 密钥", e); }
        }
        key = Keys.hmacShaKeyFor(secret.getBytes());
    }
    public String issue(long id, String role, int version) { Instant now=Instant.now(); return Jwts.builder().subject(Long.toString(id)).claim("role", role).claim("ver", version).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(43200))).signWith(key).compact(); }
    public CurrentUser parse(String token) { Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); return new CurrentUser(Long.parseLong(c.getSubject()), c.get("role",String.class), c.get("ver",Integer.class)); }
}
