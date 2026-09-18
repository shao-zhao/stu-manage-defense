package com.example.stubackend.controller;

import com.example.stubackend.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/time")
public class TimeController {
    @GetMapping("/info")
    public Result<String> info(){
        System.out.println("first one project");
        return Result.success("api");
    }
    @GetMapping("/showtime")
    public Result<LocalDateTime> showTime(){
        return Result.success(LocalDateTime.now());
    }
}
