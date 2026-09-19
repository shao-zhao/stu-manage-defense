package com.example.stubackend.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 仅便携演示的 demo profile 使用。固定编码只在首次缺失时插入，绝不覆盖已有账号、课程或成绩。
 */
@Component
@Profile("demo")
public class DemoDataInitializer implements CommandLineRunner {
  private final JdbcTemplate db;
  private final TransactionTemplate transaction;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public DemoDataInitializer(JdbcTemplate db, TransactionTemplate transaction) {
    this.db = db;
    this.transaction = transaction;
  }

  @Override
  public void run(String... args) {
    transaction.executeWithoutResult(ignored -> seed());
  }

  private void seed() {
    // marker 与演示数据同一事务提交：用户删掉演示课程后，重启不会把它“补回来”。
    if (db.update("insert ignore into demo_seed_marker(marker) values('v2-demo')") == 0) {
      return;
    }
    long teacherOne = account("teacher01");
    long teacherTwo = ensureTeacher("demo-teacher02", "陈老师", "数学学院");
    long teacherThree = ensureTeacher("demo-teacher03", "周老师", "经管学院");

    long studentOne = studentId("student01");
    long studentTwo = ensureStudent("demo-student02", "演示学生二", "20269002", "计算机学院");
    long studentThree = ensureStudent("demo-student03", "演示学生三", "20269003", "数学学院");
    long studentFour = ensureStudent("demo-student04", "演示学生四", "20269004", "经管学院");
    long studentFive = ensureStudent("demo-student05", "演示学生五", "20269005", "计算机学院");
    long studentSix = ensureStudent("demo-student06", "演示学生六", "20269006", "数学学院");

    long published =
        createCourseIfMissing(
            "DEMO-DATA-2026",
            "数据结构与算法",
            teacherOne,
            "PUBLISHED",
            "PUBLISHED",
            "真实已发布成绩，供学分、绩点和成绩分布演示");
    if (published > 0) {
      addPublishedGrades(published, List.of(studentOne, studentTwo, studentThree));
    }

    long submitted =
        createCourseIfMissing(
            "DEMO-NET-2026",
            "计算机网络",
            teacherOne,
            "PUBLISHED",
            "SUBMITTED",
            "教师已提交，等待管理员审核的真实流程数据");
    if (submitted > 0) {
      addSubmittedGrades(submitted, List.of(studentFive, studentSix));
    }

    createCourseIfMissing(
        "DEMO-CALC-2026", "高等数学", teacherTwo, "CLOSED", "DRAFT", "停开课程仍会保留给学生查看");
    createCourseIfMissing(
        "DEMO-DRAFT-2026", "管理学原理", teacherThree, "UNPUBLISHED", "DRAFT", "无关联未发布课程，可演示完整编辑与删除");
  }

  private long ensureTeacher(String username, String name, String department) {
    db.update(
        "insert ignore into account(username,password_hash,name,role,status,department) values(?,?,?,'TEACHER','ENABLED',?)",
        username,
        bcrypt.encode("123456"),
        name,
        department);
    return account(username);
  }

  private long ensureStudent(String username, String name, String studentNo, String department) {
    db.update(
        "insert ignore into account(username,password_hash,name,role,status,department) values(?,?,?,'STUDENT','ENABLED',?)",
        username,
        bcrypt.encode("123456"),
        name,
        department);
    long accountId = account(username);
    db.update(
        "insert ignore into student(account_id,student_no,department,major,class_name,enrollment_year) values(?,?,?,'演示专业','演示班',2026)",
        accountId,
        studentNo,
        department);
    return db.queryForObject("select id from student where account_id=?", Long.class, accountId);
  }

  /** Returns the new course id, or zero when a user already has the fixed demo record. */
  private long createCourseIfMissing(
      String code,
      String name,
      long teacherId,
      String status,
      String gradeStatus,
      String description) {
    Long existing =
        db.query("select id from course where code=?", (rs, row) -> rs.getLong(1), code)
            .stream()
            .findFirst()
            .orElse(null);
    if (existing != null) return 0;
    db.update(
        "insert into course(code,name,teacher_id,credit,hours,semester,schedule_text,location,capacity,status,grade_status,description,enrolled) values(?,?,?,3,48,'2026-2027-1','周三 3-4 节','演示教学楼',40,?,?,?,0)",
        code,
        name,
        teacherId,
        status,
        gradeStatus,
        description);
    return db.queryForObject("select id from course where code=?", Long.class, code);
  }

  private void addPublishedGrades(long courseId, List<Long> students) {
    int[] scores = {92, 83, 58};
    for (int index = 0; index < students.size(); index++) {
      long studentId = students.get(index);
      int score = scores[index];
      db.update("insert into enrollment(course_id,student_id,status) values(?,?,'ENROLLED')", courseId, studentId);
      db.update(
          "insert into grade(course_id,student_id,usual_score,midterm_score,final_score,score,exam_status,published_applied) values(?,?,?,?,?,?, 'NORMAL',true)",
          courseId,
          studentId,
          score,
          score,
          score,
          score);
      if (score >= 60) {
        // 新建的演示成绩实际计入学分，重启时因固定课程已存在而不会重复累计。
        db.update("update student set earned_credits=earned_credits+3 where id=?", studentId);
      }
      db.update(
          "update student set gpa=(select coalesce(sum(case when g.score>=60 then (case when g.score>=90 then 4 when g.score>=80 then 3 when g.score>=70 then 2 else 1 end)*c.credit else 0 end)/nullif(sum(c.credit),0),0) from grade g join course c on c.id=g.course_id where g.student_id=? and g.exam_status='NORMAL' and c.grade_status='PUBLISHED') where id=?",
          studentId,
          studentId);
    }
    db.update("update course set enrolled=? where id=?", students.size(), courseId);
  }

  private void addSubmittedGrades(long courseId, List<Long> students) {
    for (int index = 0; index < students.size(); index++) {
      long studentId = students.get(index);
      int score = index == 0 ? 88 : 76;
      db.update("insert into enrollment(course_id,student_id,status) values(?,?,'ENROLLED')", courseId, studentId);
      db.update(
          "insert into grade(course_id,student_id,usual_score,midterm_score,final_score,score,exam_status) values(?,?,?,?,?,?, 'NORMAL')",
          courseId,
          studentId,
          score,
          score,
          score,
          score);
    }
    db.update("update course set enrolled=? where id=?", students.size(), courseId);
  }

  private long account(String username) {
    return db.queryForObject("select id from account where username=?", Long.class, username);
  }

  private long studentId(String username) {
    return db.queryForObject(
        "select s.id from student s join account a on a.id=s.account_id where a.username=?",
        Long.class,
        username);
  }
}
