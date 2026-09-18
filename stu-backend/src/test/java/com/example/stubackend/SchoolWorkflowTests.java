package com.example.stubackend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.stubackend.security.CurrentUser;
import com.example.stubackend.service.SchoolService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Exercises database transactions without starting the unavailable local HTTP listener. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class SchoolWorkflowTests {
  private static final CurrentUser ADMIN = new CurrentUser(1, "ADMIN", 0);
  private static final CurrentUser TEACHER = new CurrentUser(2, "TEACHER", 0);
  private static final CurrentUser STUDENT = new CurrentUser(3, "STUDENT", 0);

  @Autowired SchoolService school;
  @Autowired JdbcTemplate db;

  @Test
  void enrollmentCanWithdrawAndReEnrollBeforeGradesSubmit() {
    long courseId = createCourse();
    school.enroll(STUDENT, courseId, false);
    school.enroll(STUDENT, courseId, true);
    school.enroll(STUDENT, courseId, false);

    assertEquals(
        "ENROLLED",
        db.queryForObject(
            "select e.status from enrollment e join student s on s.id=e.student_id where"
                + " e.course_id=? and s.account_id=3",
            String.class,
            courseId));
  }

  @Test
  void absentGradePublishesWithoutCreditsAndRepeatedPublishIsIdempotent() {
    long courseId = createCourse();
    school.enroll(STUDENT, courseId, false);
    long studentId = db.queryForObject("select id from student where account_id=3", Long.class);
    Map<String, Object> grade = new java.util.LinkedHashMap<>();
    grade.put("studentId", studentId);
    grade.put("usualScore", 0);
    grade.put("midtermScore", 0);
    grade.put("finalScore", 0);
    grade.put("examStatus", "ABSENT");
    BigDecimal before =
        db.queryForObject(
            "select earned_credits from student where id=?", BigDecimal.class, studentId);
    school.saveGrades(
        TEACHER,
        courseId,
        Map.of(
            "usualWeight", 30, "midtermWeight", 30, "finalWeight", 40, "records", List.of(grade)));
    school.gradeTransition(TEACHER, courseId, "submit", null);
    school.gradeTransition(ADMIN, courseId, "approve", null);
    school.gradeTransition(ADMIN, courseId, "publish", null);
    BigDecimal first =
        db.queryForObject(
            "select earned_credits from student where id=?", BigDecimal.class, studentId);
    school.gradeTransition(ADMIN, courseId, "publish", null);
    BigDecimal second =
        db.queryForObject(
            "select earned_credits from student where id=?", BigDecimal.class, studentId);

    assertEquals(before.setScale(2), first.setScale(2));
    assertEquals(first, second);
  }

  private long createCourse() {
    String code = "TEST" + System.nanoTime();
    db.update(
        "insert into"
            + " course(code,name,teacher_id,credit,hours,semester,schedule_text,location,capacity,status)"
            + " values(?,?,2,3,48,'2099-1','周一','测试教室',5,'PUBLISHED')",
        code,
        "事务测试课程");
    return db.queryForObject("select id from course where code=?", Long.class, code);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void capacityLockAllowsOnlyOneConcurrentEnrollment() throws Exception {
    long courseId = createCourseWithCapacity(1);
    long first = createStudent("capacity-a");
    long second = createStudent("capacity-b");
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> enrollFirst =
          () -> {
            try {
              school.enroll(new CurrentUser(first, "STUDENT", 0), courseId, false);
              return true;
            } catch (RuntimeException ignored) {
              return false;
            }
          };
      Callable<Boolean> enrollSecond =
          () -> {
            try {
              school.enroll(new CurrentUser(second, "STUDENT", 0), courseId, false);
              return true;
            } catch (RuntimeException ignored) {
              return false;
            }
          };
      Future<Boolean> one = pool.submit(enrollFirst);
      Future<Boolean> two = pool.submit(enrollSecond);
      assertEquals(1, (one.get() ? 1 : 0) + (two.get() ? 1 : 0));
      assertEquals(
          1, db.queryForObject("select enrolled from course where id=?", Integer.class, courseId));
    } finally {
      pool.shutdownNow();
      cleanupCourseAndStudents(courseId, first, second);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void gpaUsesPassedAndFailedNormalCoursesInItsDenominator() {
    long account = createStudent("gpa-student");
    long passed = createCourseWithCapacity(2);
    long failed = createCourseWithCapacity(2);
    try {
      long studentId =
          db.queryForObject("select id from student where account_id=?", Long.class, account);
      CurrentUser student = new CurrentUser(account, "STUDENT", 0);
      publishGrade(student, studentId, passed, 80);
      publishGrade(student, studentId, failed, 50);
      assertEquals(
          new BigDecimal("1.50"),
          db.queryForObject("select gpa from student where id=?", BigDecimal.class, studentId));
    } finally {
      cleanupCourseAndStudents(passed);
      cleanupCourseAndStudents(failed, account);
    }
  }

  private long createCourseWithCapacity(int capacity) {
    String code = "TEST" + System.nanoTime();
    db.update(
        "insert into"
            + " course(code,name,teacher_id,credit,hours,semester,schedule_text,location,capacity,status)"
            + " values(?,?,2,3,48,'2099-1','周一','测试教室',?,'PUBLISHED')",
        code,
        "事务测试课程",
        capacity);
    return db.queryForObject("select id from course where code=?", Long.class, code);
  }

  private long createStudent(String prefix) {
    String username = prefix + System.nanoTime();
    db.update(
        "insert into account(username,password_hash,name,role,status,token_version)"
            + " values(?,'unused','测试学生','STUDENT','ENABLED',0)",
        username);
    long account =
        db.queryForObject("select id from account where username=?", Long.class, username);
    db.update(
        "insert into student(account_id,student_no,department,major,class_name,enrollment_year)"
            + " values(?,?, '测试学院','测试专业','测试班',2099)",
        account,
        "N" + account);
    return account;
  }

  private void publishGrade(CurrentUser student, long studentId, long courseId, int score) {
    school.enroll(student, courseId, false);
    Map<String, Object> grade = new java.util.LinkedHashMap<>();
    grade.put("studentId", studentId);
    grade.put("usualScore", score);
    grade.put("midtermScore", score);
    grade.put("finalScore", score);
    grade.put("examStatus", "NORMAL");
    school.saveGrades(
        TEACHER,
        courseId,
        Map.of(
            "usualWeight", 30, "midtermWeight", 30, "finalWeight", 40, "records", List.of(grade)));
    school.gradeTransition(TEACHER, courseId, "submit", null);
    school.gradeTransition(ADMIN, courseId, "approve", null);
    school.gradeTransition(ADMIN, courseId, "publish", null);
  }

  private void cleanupCourseAndStudents(long courseId, long... accounts) {
    db.update("delete from grade where course_id=?", courseId);
    db.update("delete from enrollment where course_id=?", courseId);
    db.update("delete from course where id=?", courseId);
    for (long account : accounts) {
      db.update("delete from notification where account_id=?", account);
      db.update("delete from student where account_id=?", account);
      db.update("delete from account where id=?", account);
    }
  }
}
