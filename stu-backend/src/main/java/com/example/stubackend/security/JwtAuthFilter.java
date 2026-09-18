package com.example.stubackend.security;

import com.example.stubackend.service.SchoolService;
import com.example.stubackend.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final SchoolService school;
  private final ObjectMapper json;

  public JwtAuthFilter(JwtService jwt, SchoolService school, ObjectMapper json) {
    this.jwt = jwt;
    this.school = school;
    this.json = json;
  }

  protected boolean shouldNotFilter(HttpServletRequest r) {
    String p = r.getRequestURI();
    return "OPTIONS".equals(r.getMethod())
        || !p.startsWith("/api/")
        || p.equals("/api/auth/login")
        || p.startsWith("/static/");
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      String h = req.getHeader("Authorization");
      if (h == null || !h.startsWith("Bearer ")) throw new ApiException(401, "请先登录");
      CurrentUser u = jwt.parse(h.substring(7));
      school.verifySession(u);
      req.setAttribute("user", u);
    } catch (ApiException e) {
      write(res, e.status(), e.getMessage());
      return;
    } catch (Exception e) {
      write(res, 401, "登录已失效");
      return;
    }
    chain.doFilter(req, res);
  }

  private void write(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("code", 505);
    body.put("message", message);
    body.put("data", null);
    json.writeValue(res.getWriter(), body);
  }
}
