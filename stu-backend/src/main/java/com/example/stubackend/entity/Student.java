package com.example.stubackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class Student {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String grade;
    private String phone;
    private Integer earnCredits;
}
