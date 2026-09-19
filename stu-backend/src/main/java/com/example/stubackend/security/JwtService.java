package com.example.stubackend.security;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final long TOKEN_SECONDS = 12 * 60 * 60;

  private final SecretKey key;

  public JwtService(@Value("${app.jwt-secret:}") String configuredSecret) {
    String secret = configuredSecret;
    if (secret == null || secret.isBlank()) {
      secret = loadOrCreateLocalSecret();
    }
    key = Keys.hmacShaKeyFor(secret.getBytes());
  }

  /**
   * 便携版首次运行生成本地密钥，之后始终复用；否则重启会让全部已登录用户无故失效。
   * 生产环境可以用 app.jwt-secret 环境变量接管，密钥不会写进源码或接口。
   */
  private String loadOrCreateLocalSecret() {
    Path localSecret = Path.of("jwt-secret.local");
    try {
      if (Files.exists(localSecret)) {
        return Files.readString(localSecret).trim();
      }
      byte[] bytes = new byte[48];
      new SecureRandom().nextBytes(bytes);
      String generated = Base64.getEncoder().encodeToString(bytes);
      Files.writeString(localSecret, generated);
      return generated;
    } catch (IOException e) {
      throw new IllegalStateException("无法初始化 JWT 密钥", e);
    }
  }

  /** Token 内保存账号版本，服务端递增版本即可立即废止旧 token。 */
  public String issue(long id, String role, int version) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(id))
        .claim("role", role)
        .claim("ver", version)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(TOKEN_SECONDS)))
        .signWith(key)
        .compact();
  }

  public CurrentUser parse(String token) {
    Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return new CurrentUser(
        Long.parseLong(claims.getSubject()),
        claims.get("role", String.class),
        claims.get("ver", Integer.class));
  }
}
