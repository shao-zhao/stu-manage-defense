package com.example.stubackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 生产包把前端 dist 放在 classpath:/static。Vue 路由由浏览器解释，刷新时必须回到入口页；
 * API、上传文件和 assets 则交给各自的处理器，缺失资源不会被伪装成 HTML 成功响应。
 */
@Controller
public class SpaController {
  @GetMapping("/")
  public String index() {
    return "forward:/index.html";
  }

  @GetMapping("/{route:^(?!api|actuator|static|assets)[^.]+}")
  public String topLevelRoute() {
    return "forward:/index.html";
  }

  @GetMapping("/{route:^(?!api|actuator|static|assets)[^.]+}/**")
  public String nestedRoute() {
    return "forward:/index.html";
  }
}
