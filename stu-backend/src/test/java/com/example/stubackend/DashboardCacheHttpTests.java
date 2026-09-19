package com.example.stubackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the browser-facing cache path with a real notification LocalDateTime payload. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardCacheHttpTests {
  private static final String CACHE_KEY = "stu_manage:dashboard:1:0";

  @Autowired JdbcTemplate db;
  @Autowired StringRedisTemplate redis;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;

  @Test
  void dashboardCachesNotificationTimestampAndReportsRealHitAndTtl() {
    assumeTrue(redisAvailable(), "本机 Redis 未运行时跳过 HTTP 缓存集成测试");
    String title = "缓存时间测试" + System.nanoTime();
    redis.delete(CACHE_KEY);
    db.update("insert into notification(account_id,title,content) values(1,?,?)", title, "测试通知时间序列化");
    try {
      String token = loginToken();
      Map<String, Object> first = dashboard(token);
      Map<String, Object> second = dashboard(token);
      Map<String, Object> firstCache = map(first.get("cache"));
      Map<String, Object> secondCache = map(second.get("cache"));

      assertEquals("UP", firstCache.get("backend"));
      assertFalse((Boolean) firstCache.get("hit"));
      assertEquals("UP", secondCache.get("backend"));
      assertTrue((Boolean) secondCache.get("hit"));
      assertEquals(first.get("generatedAt"), second.get("generatedAt"));
      assertTrue(((Number) secondCache.get("ttlSeconds")).longValue() >= 0);
    } finally {
      db.update("delete from notification where account_id=1 and title=?", title);
      redis.delete(CACHE_KEY);
    }
  }

  private String loginToken() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri("/api/auth/login"))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of("username", "admin", "password", "123456", "role", "ADMIN"))))
              .build();
      Map<String, Object> login = response(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()));
      return (String) map(login.get("data")).get("token");
    } catch (Exception e) {
      throw new AssertionError("管理员登录失败", e);
    }
  }

  private Map<String, Object> dashboard(String token) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(uri("/api/dashboard"))
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();
      Map<String, Object> response =
          response(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()));
      assertEquals(202, ((Number) response.get("code")).intValue());
      return map(response.get("data"));
    } catch (Exception e) {
      throw new AssertionError("Dashboard 请求失败", e);
    }
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> response(HttpResponse<String> response) throws Exception {
    assertEquals(200, response.statusCode());
    return json.readValue(response.body(), Map.class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    assertNotNull(value);
    return (Map<String, Object>) value;
  }

  private boolean redisAvailable() {
    try {
      return "PONG".equals(
          redis.execute(
              (org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()));
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}
