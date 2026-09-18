package com.example.stubackend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(request.getMethod().equals("OPTIONS")){
            return true;
        }
        System.out.println("拦截账户了");
        System.out.println(request.getRequestURI());
        System.out.println(request.getHeader("Authorization"));
        //放行
        String token = request.getHeader("Authorization");
        if(!StringUtils.hasText(token)){
            throw  new RuntimeException("没有登录");
        }

        int i = token.indexOf(":");
        String uid = token.substring(i+1);
        System.out.println(uid);
        //request.setAttribute("UID",uid);
        RequestContextHolder.currentRequestAttributes()
                .setAttribute("UID",uid,0);
        return true;
    }

    public static Integer getCurrentUID(){
       String uid = (String)RequestContextHolder
               .currentRequestAttributes()
               .getAttribute("UID",0);
       return Integer.valueOf(uid);
    }
}
