package com.example.stubackend;

import com.example.stubackend.controller.StudentController;
import com.example.stubackend.entity.Student;
import com.example.stubackend.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 使用真实接口和本地 MySQL，每次测试自动回滚，不保留测试学生。
@SpringBootTest
@Transactional
class StudentUpdateTests {
    @Autowired StudentController controller;
    @Autowired StudentMapper mapper;
    MockMvc mvc;
    Student student;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        student = new Student();
        student.setName("修改功能测试学生");
        student.setGrade("2025");
        student.setPhone("10086");
        student.setEarnCredits(11);
        mapper.insert(student);
    }

    @Test
    void updatesBasicFieldsButNeverCreditsEvenWhenClientSendsThem() throws Exception {
        String body = """
                {"id":%d,"name":" 修改后的姓名 ","grade":"计科1班","phone":"","earnCredits":999}
                """.formatted(student.getId());
        // 重复保存相同内容也应成功。
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/student/update").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(202));
        }
        Student saved = mapper.selectById(student.getId());
        assertEquals("修改后的姓名", saved.getName());
        assertEquals("计科1班", saved.getGrade());
        assertEquals("", saved.getPhone());
        assertEquals(11, saved.getEarnCredits());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"grade\":\"2025\"}",
            "{\"name\":\"测试\",\"grade\":\" \"}",
            "{\"grade\":\"2025\"}"
    })
    void rejectsInvalidFieldsWithoutChangingStudent(String fields) throws Exception {
        String body = "{\"id\":" + student.getId() + "," + fields.substring(1);
        mvc.perform(put("/api/student/update").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(505));
        assertEquals(student, mapper.selectById(student.getId()));
    }

    @Test
    void rejectsLongValues() throws Exception {
        for (String field : new String[]{"name", "grade", "phone"}) {
            String name = field.equals("name") ? "a".repeat(256) : "测试";
            String grade = field.equals("grade") ? "a".repeat(256) : "2025";
            String phone = field.equals("phone") ? "1".repeat(256) : "10086";
            String body = """
                    {"id":%d,"name":"%s","grade":"%s","phone":"%s"}
                    """.formatted(student.getId(), name, grade, phone);
            mvc.perform(put("/api/student/update").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(505));
        }
        assertEquals(student, mapper.selectById(student.getId()));
    }

    @Test
    void rejectsMissingIdAndNonexistentStudent() throws Exception {
        mvc.perform(put("/api/student/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"grade\":\"2025\"}"))
                .andExpect(jsonPath("$.code").value(505));
        // 仅删除本测试事务刚创建的行，确保测试一个确定不存在的主键。
        mapper.deleteById(student.getId());
        mvc.perform(put("/api/student/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + student.getId() + ",\"name\":\"测试\",\"grade\":\"2025\"}"))
                .andExpect(jsonPath("$.code").value(505));
    }
}
