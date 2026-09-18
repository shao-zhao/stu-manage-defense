package com.example.stubackend.model;
//data transfer boject

import lombok.Data;

@Data
public class StudentModifyDto {
    private Integer id;
    private String name;
    private String grade;
    private String phone;
}
