package com.example.stubackend.controller;

import com.example.stubackend.model.Result;
import com.example.stubackend.security.CurrentUser;
import com.example.stubackend.security.JwtService;
import com.example.stubackend.service.EventHub;
import com.example.stubackend.service.SchoolService;
import com.example.stubackend.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {
  private static final Logger log = LoggerFactory.getLogger(ApiController.class);

  private final SchoolService school;
  private final JwtService jwt;
  private final EventHub events;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public ApiController(
      SchoolService school,
      JwtService jwt,
      EventHub events,
      StringRedisTemplate redis,
      ObjectMapper json) {
    this.school = school;
    this.jwt = jwt;
    this.events = events;
    this.redis = redis;
    this.json = json;
  }

  private CurrentUser user(HttpServletRequest r) {
    return (CurrentUser) r.getAttribute("user");
  }

  private void admin(CurrentUser u) {
    if (!u.is("ADMIN")) throw new ApiException(403, "仅管理员可操作");
  }

  @PostMapping("/auth/login")
  public Result<Map<String, Object>> login(@RequestBody Map<String, String> x) {
    Map<String, Object> u = school.login(x.get("username"), x.get("password"), x.get("role"));
    long id = ((Number) u.get("id")).longValue();
    return Result.success(
        Map.of("token", jwt.issue(id, (String) u.get("role"), school.tokenVersion(id)), "user", u));
  }

  @GetMapping("/auth/me")
  public Result<Map<String, Object>> me(HttpServletRequest r) {
    return Result.success(school.account(user(r).id()));
  }

  @PostMapping("/auth/logout")
  public Result<Void> logout(HttpServletRequest request) {
    school.logout(user(request));
    return Result.success(null);
  }

  @PutMapping("/auth/password")
  public Result<Void> password(HttpServletRequest r, @RequestBody Map<String, String> x) {
    school.changePassword(user(r), x.get("oldPassword"), x.get("newPassword"));
    return Result.success(null);
  }

  @GetMapping("/students")
  public Result<Map<String, Object>> students(
      HttpServletRequest r,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    admin(user(r));
    return Result.success(school.students(page, Math.min(size, 100), keyword, status));
  }

  @PostMapping("/students")
  public Result<Map<String, Object>> addStudent(
      HttpServletRequest r, @RequestBody Map<String, Object> x) {
    admin(user(r));
    return Result.success(school.saveStudent(x, null));
  }

  @PutMapping("/students/{id}")
  public Result<Map<String, Object>> editStudent(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> x) {
    admin(user(r));
    return Result.success(school.saveStudent(x, id));
  }

  @PutMapping("/students/{id}/status")
  public Result<Void> studentStatus(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, String> x) {
    admin(user(r));
    school.setStudentStatus(id, x.get("status"));
    return Result.success(null);
  }

  @PostMapping("/students/{id}/reset-password")
  public Result<Void> studentReset(HttpServletRequest r, @PathVariable long id) {
    admin(user(r));
    school.resetStudent(id);
    return Result.success(null);
  }

  @GetMapping("/staff")
  public Result<Map<String, Object>> staff(
      HttpServletRequest r,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) String keyword) {
    admin(user(r));
    return Result.success(school.staff(page, Math.min(size, 100), keyword));
  }

  @GetMapping("/staff/teachers")
  public Result<List<Map<String, Object>>> teachers(HttpServletRequest r) {
    if (user(r).is("STUDENT")) throw new ApiException(403, "权限不足");
    return Result.success(school.teachers());
  }

  @PostMapping("/staff")
  public Result<Map<String, Object>> addStaff(
      HttpServletRequest r, @RequestBody Map<String, Object> x) {
    admin(user(r));
    return Result.success(school.saveStaff(x, null));
  }

  @PutMapping("/staff/{id}")
  public Result<Map<String, Object>> editStaff(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> x) {
    admin(user(r));
    return Result.success(school.saveStaff(x, id));
  }

  @PutMapping("/staff/{id}/status")
  public Result<Void> staffStatus(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, String> x) {
    admin(user(r));
    school.setStaffStatus(id, x.get("status"));
    return Result.success(null);
  }

  @PostMapping("/staff/{id}/reset-password")
  public Result<Void> staffReset(HttpServletRequest r, @PathVariable long id) {
    admin(user(r));
    school.reset(id);
    return Result.success(null);
  }

  @GetMapping("/courses")
  public Result<List<Map<String, Object>>> courses(
      HttpServletRequest r,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String semester,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "false") boolean mine) {
    return Result.success(school.courses(user(r), keyword, semester, status, mine));
  }

  @PostMapping("/courses")
  public Result<Map<String, Object>> addCourse(
      HttpServletRequest r, @RequestBody Map<String, Object> x) {
    if (user(r).is("STUDENT")) throw new ApiException(403, "学生不能建课");
    return Result.success(school.saveCourse(user(r), x, null));
  }

  @PutMapping("/courses/{id}")
  public Result<Map<String, Object>> editCourse(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> x) {
    return Result.success(school.saveCourse(user(r), x, id));
  }

  @PostMapping("/courses/{id}/publish")
  public Result<Void> publishCourse(HttpServletRequest r, @PathVariable long id) {
    school.publishCourse(user(r), id);
    return Result.success(null);
  }

  @DeleteMapping("/courses/{id}")
  public Result<Void> deleteCourse(HttpServletRequest r, @PathVariable long id) {
    school.deleteCourse(user(r), id);
    return Result.success(null);
  }

  @PostMapping("/courses/{id}/close")
  public Result<Void> closeCourse(HttpServletRequest r, @PathVariable long id) {
    school.closeCourse(user(r), id);
    return Result.success(null);
  }

  @PostMapping("/courses/{id}/reopen")
  public Result<Void> reopenCourse(HttpServletRequest r, @PathVariable long id) {
    school.reopenCourse(user(r), id);
    return Result.success(null);
  }

  @PostMapping("/courses/{id}/enroll")
  public Result<Void> enroll(HttpServletRequest r, @PathVariable long id) {
    school.enroll(user(r), id, false);
    return Result.success(null);
  }

  @PostMapping("/courses/{id}/withdraw")
  public Result<Void> withdraw(HttpServletRequest r, @PathVariable long id) {
    school.enroll(user(r), id, true);
    return Result.success(null);
  }

  @GetMapping("/enrollments/mine")
  public Result<List<Map<String, Object>>> enrollments(HttpServletRequest r) {
    if (!user(r).is("STUDENT")) throw new ApiException(403, "仅学生可查看");
    return Result.success(school.enrollments(user(r).id()));
  }

  @GetMapping("/courses/{id}/grades")
  public Result<Map<String, Object>> grades(HttpServletRequest r, @PathVariable long id) {
    return Result.success(school.grades(user(r), id));
  }

  @PutMapping("/courses/{id}/grades")
  public Result<Void> saveGrades(
      HttpServletRequest r, @PathVariable long id, @RequestBody Map<String, Object> x) {
    school.saveGrades(user(r), id, x);
    return Result.success(null);
  }

  @PostMapping("/courses/{id}/grades/{action:submit|approve|reject|publish}")
  public Result<Void> transition(
      HttpServletRequest r,
      @PathVariable long id,
      @PathVariable String action,
      @RequestBody(required = false) Map<String, String> x) {
    school.gradeTransition(user(r), id, action, x == null ? null : x.get("reason"));
    return Result.success(null);
  }

  @GetMapping("/grades/mine")
  public Result<Map<String, Object>> myGrades(
      HttpServletRequest r, @RequestParam(required = false) String semester) {
    if (!user(r).is("STUDENT")) throw new ApiException(403, "仅学生可查看");
    return Result.success(school.myGrades(user(r).id(), semester));
  }

  @GetMapping("/dashboard")
  public Result<Map<String, Object>> dashboard(HttpServletRequest r) {
    CurrentUser current = user(r);
    boolean up;
    String key = "stu_manage:dashboard:" + current.id() + ":" + current.tokenVersion();
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        Map<String, Object> data = new LinkedHashMap<>(json.readValue(cached, Map.class));
        Long ttl = redis.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        data.put("cache", Map.of("backend", "UP", "hit", true, "ttlSeconds", ttl));
        return Result.success(data);
      }
      Map<String, Object> data = new LinkedHashMap<>(school.dashboard(current, true));
      data.put("generatedAt", java.time.Instant.now().toString());
      redis.opsForValue().set(key, json.writeValueAsString(data), java.time.Duration.ofSeconds(60));
      Long ttl = redis.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
      data.put("cache", Map.of("backend", "UP", "hit", false, "ttlSeconds", ttl));
      return Result.success(data);
    } catch (Exception e) {
      // Redis 故障不能阻断首页，但必须留下原因，不能把序列化错误伪装成业务成功。
      log.warn("Dashboard cache unavailable; serving a fresh database result", e);
      up = false;
    }
    Map<String, Object> data = new LinkedHashMap<>(school.dashboard(current, up));
    data.put("generatedAt", java.time.Instant.now().toString());
    return Result.success(data);
  }

  @GetMapping("/system/status")
  public Result<Map<String, Object>> systemStatus(HttpServletRequest request) {
    admin(user(request));
    return Result.success(school.systemStatus());
  }

  @GetMapping("/notifications")
  public Result<List<Map<String, Object>>> notifications(HttpServletRequest r) {
    return Result.success(school.notifications(user(r).id()));
  }

  @PutMapping("/notifications/{id}/read")
  public Result<Void> read(HttpServletRequest r, @PathVariable long id) {
    school.readNotification(user(r), id);
    return Result.success(null);
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(HttpServletRequest request) {
    return events.connect(user(request).id());
  }
}
