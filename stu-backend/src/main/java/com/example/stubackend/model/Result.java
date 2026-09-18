package com.example.stubackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
//统一返回
@Data
@AllArgsConstructor
public class Result<T> {
    private T data;
    private String message;
    // 业务逻辑编码
    // code 202 正常 505 异常
    private Integer code;
    // 使用方便开启静态方法
    public static <M> Result<M> success(M data){
        return new Result<>(data,"success",202);
    }
    public static <M> Result<M> fail(String message){
        return new Result<>(null,message,505);
    }
}
