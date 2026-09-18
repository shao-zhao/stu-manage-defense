package com.example.stubackend.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  /** Runs before the JWT filter so browser clients can read 401/403 responses. */
  @Bean
  FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
    CorsConfiguration cors = new CorsConfiguration();
    cors.addAllowedOriginPattern("*");
    cors.addAllowedHeader("*");
    cors.addAllowedMethod("*");
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    FilterRegistrationBean<CorsFilter> registration =
        new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  // 解决跨域问题
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        // 前端地址
        .allowedOriginPatterns("*")
        // 允许的请求方式
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        // 允许所有请求头
        .allowedHeaders("*")
        // 是否允许携带 Cookie
        .allowCredentials(true)
        // 预检请求缓存时间
        .maxAge(3600);
  }

  @Value("${upload.path}")
  private String uploadPath;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {

    registry
        .addResourceHandler("/static/**")
        .addResourceLocations(Path.of(uploadPath).toAbsolutePath().toUri().toString());
  }
}
