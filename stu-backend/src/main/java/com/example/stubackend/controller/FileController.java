package com.example.stubackend.controller;

import com.example.stubackend.model.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
public class FileController {
    @Value("${upload.path}")
    private  String uploadPath;

    @PostMapping("/uploadOne")
    public Result<String> upload(@RequestParam("file")MultipartFile multipartFile){
        String originalFilename= multipartFile.getOriginalFilename();
        System.out.println("原始名字"+originalFilename);
        int i = originalFilename.lastIndexOf(".");
        String substring = originalFilename.substring(i);
        System.out.println("截取的内容"+ substring);
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString()+substring;

        Path path = Path.of(uploadPath,fileName);
        System.out.println("保存成"+path);
        try {
            multipartFile.transferTo(path);
        } catch (IOException e) {
            Result.fail(e.getMessage());
        }
        //第三步修改为静态资源访问路径
        String url = "http://localhost:9090/static/"+fileName;
        return Result.success(url);
    }
}
