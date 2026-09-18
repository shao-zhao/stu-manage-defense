package com.example.stubackend.model;

import lombok.Data;

@Data
public class StaffQC {
    private Integer pageNum;
    private Integer pageSize;

    private String nameLike;
}
