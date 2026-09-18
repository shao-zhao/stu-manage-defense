package com.example.stubackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.stubackend.entity.Staff;
import com.example.stubackend.mapper.StaffMapper;
import com.example.stubackend.model.LoginDto;
import com.example.stubackend.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/login")
public class LoginController {
    @Autowired
    private StaffMapper staffMapper;
    @PostMapping("/manger")
    public Result<String> mangerLogin(@RequestBody LoginDto loginDto){
        System.out.println("后端收到的"+loginDto);
        QueryWrapper<Staff> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username",loginDto.getUsername());
        Staff staff = staffMapper.selectOne(queryWrapper);
        if(staff == null){
            return Result.fail("用户名不正确");
        }
        // 暗文密码要加密 Bcrypt加密
        if(!loginDto.getPassword().equals(staff.getPassword())){
            return Result.fail("密码不正确");
        }

        return Result.success("JWT-TOKEN-UID:"+staff.getId());
    }

}
