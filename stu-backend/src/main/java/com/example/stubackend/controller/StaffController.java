package com.example.stubackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.stubackend.entity.Staff;
import com.example.stubackend.interceptor.AuthInterceptor;
import com.example.stubackend.mapper.StaffMapper;
import com.example.stubackend.model.Result;
import com.example.stubackend.model.StaffQC;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffMapper staffMapper;

    public StaffController(StaffMapper staffMapper) {
        this.staffMapper = staffMapper;
    }

    // 分页查询 先写不分页 再升级为分页 然后测试

    @GetMapping("/current")
    public Result<Staff> current(){
        Integer currentUID = AuthInterceptor.getCurrentUID();
        Staff staff = staffMapper.selectById(currentUID);
        return Result.success(staff);
    }

    @PostMapping("/query")
    public Result<Page<Staff>> query(@RequestBody StaffQC staffQC){
        Page<Staff> page = Page.of(staffQC.getPageNum(), staffQC.getPageSize());
        QueryWrapper<Staff> queryWrapper = new QueryWrapper<>();
        if(StringUtils.hasText(staffQC.getNameLike()))
        {
            queryWrapper.like("username",staffQC.getNameLike());

        }
        Page<Staff> staffPage =staffMapper.selectPage(page,queryWrapper);
        return Result.success(staffPage);
    }
    @PostMapping("/insert")
    public Result<Staff> insert(@RequestBody Staff staff){
        staff.setId(0);
        staff.setPassword("123456");
        int insert = staffMapper.insert(staff);
        return  Result.success(staff);
    }

}
