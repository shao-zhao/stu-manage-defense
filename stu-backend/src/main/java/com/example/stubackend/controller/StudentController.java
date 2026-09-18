package com.example.stubackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.stubackend.entity.Student;
import com.example.stubackend.mapper.StudentMapper;
import com.example.stubackend.model.Result;
import com.example.stubackend.model.StudentQC;
import com.example.stubackend.model.StudentModifyDto;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    @PostMapping("/simpleQuery")
    public Result<List<Student>> simpleQuery(){
        Student student1 = new Student();
        student1.setId(11);
        student1.setName("张三");
        Student student2 = new Student();
        student2.setId(113);
        student2.setName("张三1");
        ArrayList<Student> students = new ArrayList<>();
        students.add(student2);
        students.add(student1);
        return Result.success(students);
    }
    //依赖注入
    @Autowired
    private StudentMapper studentMapper;
    @PostMapping("/query")
    public Result<List<Student>> query(){
        List<Student> students = studentMapper.selectList(null);
        return Result.success(students);
    }
    @PostMapping("/queryEX")
    public Result<List<Student>> queryEx(@RequestBody StudentQC studentQC){
        System.out.println("查询条件"+studentQC);
        QueryWrapper<Student> wrapper = new QueryWrapper<>();
        if(StringUtils.hasText(studentQC.getNameLike())){
            wrapper.like("name",studentQC.getNameLike());
        }
        if(StringUtils.hasText(studentQC.getPhoneStart())){
            wrapper.likeRight("phone",studentQC.getPhoneStart());
        }
        List<Student> students = studentMapper.selectList(wrapper);
        return Result.success(students);
    }
    @GetMapping("/findById/{id}")
    public Result<Student> findById(@PathVariable("id") Integer id){
        Student student = studentMapper.selectById(id);
        if(student == null){
            return Result.fail("待查看的数据不存在");
        }
        return Result.success(student);
    }

    @DeleteMapping("/removeById/{id}")
    public Result<Integer> removeById(@PathVariable("id") Integer id){
        int i = studentMapper.deleteById(id);
        return Result.success(i);
    }

    // 新增
    // post 新增 put 修改 get查看 delete 删除
    @PostMapping("/insert")
    public Result<Student> insert(@RequestBody Student student) {
        student.setId(0);
        student.setEarnCredits(0);
        System.out.println("将要存储"+student);
        int insert = studentMapper.insert(student);
        if(insert == 0){
            Result.fail("新增失败");
        }
        return Result.success(student);
    }

    // 第一节：根据主键修改基本信息。前端校验方便使用，后端校验保护数据。
    @PutMapping("/modify")
    public Result<Student> modify(@RequestBody StudentModifyDto studentModifyDto) {
        Student student =  studentMapper.selectById(studentModifyDto.getId());
        if(student == null){
            Result.fail("修改的不存在");
        }
        BeanUtils.copyProperties(studentModifyDto,student);
        int i = studentMapper.updateById(student);
        return Result.success(student);
    }
}
