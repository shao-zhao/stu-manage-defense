package com.example.stubackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.stubackend.entity.Staff;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StaffMapper extends BaseMapper<Staff> {
}
