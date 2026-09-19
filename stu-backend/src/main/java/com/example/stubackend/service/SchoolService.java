package com.example.stubackend.service;

import com.example.stubackend.security.CurrentUser;
import com.example.stubackend.web.ApiException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.SpringBootVersion;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
  private final EventHub events;
  private final StringRedisTemplate redis;
  private final DataSource dataSource;

  public SchoolService(
      JdbcTemplate db, EventHub events, StringRedisTemplate redis, DataSource dataSource) {
    this.db = db;
    this.events = events;
    this.redis = redis;
    this.dataSource = dataSource;
  }

  public void verifySession(CurrentUser u) {
    Map<String, Object> a = one("select role,status,token_version from account where id=?", u.id());
    if (!u.role().equals(a.get("role"))
        || ((Number) a.get("token_version")).intValue() != u.tokenVersion())
      throw new ApiException(401, "登录已失效");
    if ("FROZEN".equals(a.get("status"))) throw new ApiException(403, "账号已被冻结");
  }

  public Map<String, Object> login(String username, String password, String role) {
    Map<String, Object> a = one("select * from account where username=?", username);
    if (!role.equals(a.get("role")) || !bcrypt.matches(password, (String) a.get("password_hash")))
      throw new ApiException(401, "用户名、密码或角色不正确");
    if ("FROZEN".equals(a.get("status"))) throw new ApiException(403, "账号已被冻结");
    return user(a);
  }

  public Map<String, Object> account(long id) {
    return user(one("select * from account where id=?", id));
  }

  public int tokenVersion(long id) {
    return ((Number) one("select token_version from account where id=?", id).get("token_version"))
        .intValue();
  }

  public void changePassword(CurrentUser u, String oldP, String newP) {
    if (newP == null || newP.length() < 6) throw new ApiException(400, "新密码至少 6 位");
    Map<String, Object> a = one("select password_hash from account where id=?", u.id());
    if (!bcrypt.matches(oldP, (String) a.get("password_hash")))
      throw new ApiException(400, "旧密码不正确");
    db.update(
        "update account set password_hash=?,token_version=token_version+1 where id=?",
        bcrypt.encode(newP),
        u.id());
    events.disconnectAccount(u.id());
  }

  public void logout(CurrentUser u) {
    db.update("update account set token_version=token_version+1 where id=?", u.id());
    events.disconnectAccount(u.id());
  }

  public Map<String, Object> students(int page, int size, String keyword, String status) {
    String w = " from student s join account a on a.id=s.account_id where 1=1";
    List<Object> p = new ArrayList<>();
    if (keyword != null && !keyword.isBlank()) {
      w += " and (s.student_no like ? or a.name like ?)";
      p.add("%" + keyword + "%");
      p.add("%" + keyword + "%");
    }
    if (status != null && !status.isBlank()) {
      w += " and a.status=?";
      p.add(status);
    }
    long total = db.queryForObject("select count(*)" + w, Long.class, p.toArray());
    p.add((page - 1) * size);
    p.add(size);
    return Map.of(
        "records",
        studentRows(
            "select s.*,a.name,a.phone,a.status " + w + " order by s.id desc limit ?,?",
            p.toArray()),
        "total",
        total);
  }

  @Transactional
  public Map<String, Object> saveStudent(Map<String, Object> x, Long id) {
    if (id == null) {
      String no = opt(x, "studentNo", "");
      if (no.isBlank()) no = "S" + System.currentTimeMillis();
      String username = opt(x, "username", no);
      long aid =
          insert(
              "insert into account(username,password_hash,name,role,status,department,phone)"
                  + " values(?,?,?,'STUDENT','ENABLED',?,?)",
              username,
              bcrypt.encode("123456"),
              required(x, "name"),
              opt(x, "department", ""),
              opt(x, "phone", ""));
      long sid =
          insert(
              "insert into"
                  + " student(account_id,student_no,gender,department,major,class_name,enrollment_year,required_credits)"
                  + " values(?,?,?,?,?,?,?,?)",
              aid,
              no,
              opt(x, "gender", ""),
              opt(x, "department", ""),
              opt(x, "major", ""),
              opt(x, "className", ""),
              num(x, "enrollmentYear", 2024),
              num(x, "requiredCredits", 140));
      return studentRow(sid);
    }
    Map<String, Object> s = one("select account_id from student where id=?", id);
    db.update(
        "update account set name=?,phone=?,department=? where id=?",
        required(x, "name"),
        opt(x, "phone", ""),
        opt(x, "department", ""),
        s.get("account_id"));
    db.update(
        "update student set"
            + " gender=?,department=?,major=?,class_name=?,enrollment_year=?,required_credits=?"
            + " where id=?",
        opt(x, "gender", ""),
        opt(x, "department", ""),
        opt(x, "major", ""),
        opt(x, "className", ""),
        num(x, "enrollmentYear", 2024),
        num(x, "requiredCredits", 140),
        id);
    return studentRow(id);
  }

  public void setStudentStatus(long id, String status) {
    if (!List.of("ENABLED", "FROZEN", "SUSPENDED").contains(status))
      throw new ApiException(400, "无效状态");
    long a =
        ((Number) one("select account_id from student where id=?", id).get("account_id"))
            .longValue();
    db.update("update account set status=?,token_version=token_version+1 where id=?", status, a);
    events.disconnectAccount(a);
  }

  public Map<String, Object> staff(int page, int size, String keyword) {
    String q =
        "select id,username,name,role,department,phone,photo,status from account where"
            + " role<>'STUDENT'"
            + (keyword == null || keyword.isBlank() ? "" : " and (name like ? or username like ?)");
    Object[] p =
        keyword == null || keyword.isBlank()
            ? new Object[] {}
            : new Object[] {"%" + keyword + "%", "%" + keyword + "%"};
    long n = db.queryForObject("select count(*) from (" + q + ") x", Long.class, p);
    List<Map<String, Object>> rows =
        rows(q + " order by id limit ?,?", append(p, (page - 1) * size, size));
    return Map.of("records", rows, "total", n);
  }

  @Transactional
  public Map<String, Object> saveStaff(Map<String, Object> x, Long id) {
    if (id == null) {
      String role = opt(x, "role", "TEACHER");
      if (!List.of("ADMIN", "TEACHER").contains(role)) throw new ApiException(400, "教职工角色无效");
      long nid =
          insert(
              "insert into account(username,password_hash,name,role,status,department,phone,photo)"
                  + " values(?,?,?,?,'ENABLED',?,?,?)",
              required(x, "username"),
              bcrypt.encode("123456"),
              required(x, "name"),
              role,
              opt(x, "department", ""),
              opt(x, "phone", ""),
              opt(x, "photo", null));
      return account(nid);
    }
    db.update(
        "update account set name=?,department=?,phone=?,photo=? where id=?",
        required(x, "name"),
        opt(x, "department", ""),
        opt(x, "phone", ""),
        opt(x, "photo", null),
        id);
    return account(id);
  }

  public void setStaffStatus(long id, String status) {
    if (!List.of("ENABLED", "FROZEN").contains(status)) throw new ApiException(400, "无效状态");
    db.update(
        "update account set status=?,token_version=token_version+1 where id=? and role<>'STUDENT'",
        status,
        id);
    events.disconnectAccount(id);
  }

  public void reset(long accountId) {
    db.update(
        "update account set password_hash=?,token_version=token_version+1 where id=?",
        bcrypt.encode("123456"),
        accountId);
    events.disconnectAccount(accountId);
  }

  public void resetStudent(long studentId) {
    reset(
        ((Number) one("select account_id from student where id=?", studentId).get("account_id"))
            .longValue());
  }

  public List<Map<String, Object>> teachers() {
    return rows(
        "select id,username,name,department from account where role='TEACHER' and"
            + " status='ENABLED'");
  }

  public List<Map<String, Object>> courses(
      CurrentUser u, String keyword, String semester, String status, boolean mine) {
    String q =
        "select c.*,a.name teacher_name from course c join account a on a.id=c.teacher_id where"
            + " 1=1";
    List<Object> p = new ArrayList<>();
    if (u.is("TEACHER")) {
      q += " and c.teacher_id=?";
      p.add(u.id());
    } else if (u.is("STUDENT")) {
      // 停开课程仍要让学生看到，才能保留已选课程和历史成绩的上下文。
      q += " and c.status in ('PUBLISHED','CLOSED')";
    }
    if (mine && u.is("STUDENT")) {
      q +=
          " and exists(select 1 from enrollment e join student s on s.id=e.student_id where"
              + " e.course_id=c.id and s.account_id=? and e.status='ENROLLED')";
      p.add(u.id());
    }
    if (keyword != null && !keyword.isBlank()) {
      q += " and (c.name like ? or c.code like ?)";
      p.add("%" + keyword + "%");
      p.add("%" + keyword + "%");
    }
    if (semester != null && !semester.isBlank()) {
      q += " and c.semester=?";
      p.add(semester);
    }
    if (status != null && !status.isBlank()) {
      if (!List.of("UNPUBLISHED", "PUBLISHED", "CLOSED").contains(status)) {
        throw new ApiException(400, "课程状态无效");
      }
      q += " and c.status=?";
      p.add(status);
    }
    List<Map<String, Object>> out = courseRows(q + " order by c.id desc", p.toArray());
    if (u.is("STUDENT"))
      for (Map<String, Object> c : out) {
        List<Map<String, Object>> e =
            rows(
                "select e.status from enrollment e join student s on s.id=e.student_id where"
                    + " s.account_id=? and e.course_id=?",
                u.id(),
                c.get("id"));
        c.put("myEnrollmentStatus", e.isEmpty() ? null : e.get(0).get("status"));
      }
    return out;
  }

  @Transactional
  public Map<String, Object> saveCourse(CurrentUser u, Map<String, Object> x, Long id) {
    if (id == null) {
      double credit = decimal(x, "credit", 1);
      int hours = num(x, "hours", 16);
      int capacity = num(x, "capacity", 30);
      validateCourseNumbers(credit, hours, capacity);
      long teacher = u.is("TEACHER") ? u.id() : longNum(x, "teacherId");
      validateTeacher(teacher);
      long nid =
          insert(
              "insert into"
                  + " course(code,name,teacher_id,credit,hours,semester,schedule_text,location,capacity,cover_url,description)"
                  + " values(?,?,?,?,?,?,?,?,?,?,?)",
              required(x, "code"),
              required(x, "name"),
              teacher,
              credit,
              hours,
              required(x, "semester"),
              opt(x, "schedule", ""),
              opt(x, "location", ""),
              capacity,
              opt(x, "coverUrl", null),
              opt(x, "description", null));
      after("refresh", Map.of("courseId", nid));
      return course(nid);
    }
    // 编辑与发布/选课共用课程行锁，避免前端同时操作时绕过状态和容量约束。
    Map<String, Object> c = one("select * from course where id=? for update", id);
    owner(u, c);
    if (!"UNPUBLISHED".equals(c.get("status"))) {
      updateCourseDescription(x, id, c);
      after("refresh", Map.of("courseId", id));
      return course(id);
    }

    double credit = decimal(x, "credit", number(c, "credit").doubleValue());
    int hours = num(x, "hours", number(c, "hours").intValue());
    int capacity = num(x, "capacity", number(c, "capacity").intValue());
    validateCourseNumbers(credit, hours, capacity);
    long teacher = courseTeacher(u, x, c);
    validateTeacher(teacher);
    db.update(
        "update course set"
            + " code=?,name=?,teacher_id=?,credit=?,hours=?,semester=?,schedule_text=?,location=?,capacity=?,cover_url=?,description=?"
            + " where id=?",
        required(x, "code"),
        required(x, "name"),
        teacher,
        credit,
        hours,
        required(x, "semester"),
        opt(x, "schedule", ""),
        opt(x, "location", ""),
        capacity,
        opt(x, "coverUrl", null),
        opt(x, "description", null),
        id);
    after("refresh", Map.of("courseId", id));
    return course(id);
  }

  @Transactional
  public void publishCourse(CurrentUser u, long id) {
    Map<String, Object> c = one("select * from course where id=? for update", id);
    owner(u, c);
    if (!"UNPUBLISHED".equals(c.get("status"))) throw new ApiException(400, "课程已发布");
    db.update("update course set status='PUBLISHED' where id=?", id);
    after("refresh", Map.of("courseId", id));
  }

  @Transactional
  public void deleteCourse(CurrentUser u, long id) {
    Map<String, Object> c = one("select * from course where id=? for update", id);
    owner(u, c);
    if (!"UNPUBLISHED".equals(c.get("status"))) {
      throw new ApiException(400, "只有未发布课程可以删除");
    }
    long enrollments = db.queryForObject("select count(*) from enrollment where course_id=?", Long.class, id);
    long grades = db.queryForObject("select count(*) from grade where course_id=?", Long.class, id);
    long media = db.queryForObject("select count(*) from media where course_id=?", Long.class, id);
    if (enrollments + grades + media > 0) {
      throw new ApiException(400, "课程已有选课、成绩或资料关联，不能删除");
    }
    db.update("delete from course where id=?", id);
    after("refresh", Map.of("courseId", id));
  }

  @Transactional
  public void closeCourse(CurrentUser u, long id) {
    Map<String, Object> c = one("select * from course where id=? for update", id);
    owner(u, c);
    if (!"PUBLISHED".equals(c.get("status"))) {
      throw new ApiException(400, "只有已发布课程可以停止选课");
    }
    db.update("update course set status='CLOSED' where id=?", id);
    after("refresh", Map.of("courseId", id));
  }

  @Transactional
  public void reopenCourse(CurrentUser u, long id) {
    Map<String, Object> c = one("select * from course where id=? for update", id);
    owner(u, c);
    if (!"CLOSED".equals(c.get("status"))) {
      throw new ApiException(400, "课程当前不是停开状态");
    }
    if (!"DRAFT".equals(c.get("grade_status"))) {
      throw new ApiException(400, "成绩已进入流程，不能重新开放选课");
    }
    db.update("update course set status='PUBLISHED' where id=?", id);
    after("refresh", Map.of("courseId", id));
  }

  @Transactional
  public void enroll(CurrentUser u, long courseId, boolean withdraw) {
    if (!u.is("STUDENT")) throw new ApiException(403, "仅学生可选退课");
    Map<String, Object> s = one("select * from student where account_id=?", u.id());
    Map<String, Object> c = one("select * from course where id=? for update", courseId);
    List<Map<String, Object>> es =
        rows(
            "select * from enrollment where course_id=? and student_id=? for update",
            courseId,
            s.get("id"));
    if (withdraw) {
      // 停开只阻止新增选课；在成绩草稿期仍允许已选学生退出。
      if (!List.of("PUBLISHED", "CLOSED").contains(c.get("status"))) {
        throw new ApiException(400, "课程当前不能退课");
      }
      if (es.isEmpty() || !"ENROLLED".equals(es.get(0).get("status")))
        throw new ApiException(400, "没有可退的选课记录");
      if (!"DRAFT".equals(c.get("grade_status"))) throw new ApiException(400, "成绩已提交，不能退课");
      db.update("update enrollment set status='WITHDRAWN' where id=?", es.get(0).get("id"));
      db.update("update course set enrolled=enrolled-1 where id=?", courseId);
    } else {
      if (!"PUBLISHED".equals(c.get("status"))) throw new ApiException(400, "课程当前不能选课");
      if ("SUSPENDED".equals(one("select status from account where id=?", u.id()).get("status")))
        throw new ApiException(403, "停学学生不能选课");
      if (!"DRAFT".equals(c.get("grade_status"))) throw new ApiException(400, "成绩已进入流程，不能新增选课");
      if (!es.isEmpty() && "ENROLLED".equals(es.get(0).get("status")))
        throw new ApiException(400, "不可重复选课");
      if (((Number) c.get("enrolled")).intValue() >= ((Number) c.get("capacity")).intValue())
        throw new ApiException(400, "课程容量已满");
      if (es.isEmpty())
        db.update(
            "insert into enrollment(course_id,student_id,status) values(?,?,'ENROLLED')",
            courseId,
            s.get("id"));
      else db.update("update enrollment set status='ENROLLED' where id=?", es.get(0).get("id"));
      db.update("update course set enrolled=enrolled+1 where id=?", courseId);
    }
    after("refresh", Map.of("courseId", courseId));
  }

  public List<Map<String, Object>> enrollments(long account) {
    List<Map<String, Object>> data =
        rows(
            "select e.id,e.course_id,c.name course_name,a.name"
                + " teacher_name,c.semester,c.credit,c.schedule_text schedule,c.location,e.status"
                + " from enrollment e join student s on s.id=e.student_id join course c on"
                + " c.id=e.course_id join account a on a.id=c.teacher_id where s.account_id=? order"
                + " by e.updated_at desc",
            account);
    for (Map<String, Object> row : data) {
      rename(row, "course_id", "courseId");
      rename(row, "course_name", "courseName");
      rename(row, "teacher_name", "teacherName");
    }
    return data;
  }

  private void owner(CurrentUser u, Map<String, Object> c) {
    if (!u.is("ADMIN")
        && (!u.is("TEACHER") || ((Number) c.get("teacher_id")).longValue() != u.id()))
      throw new ApiException(403, "无权管理该课程");
  }

  public Map<String, Object> grades(CurrentUser u, long courseId) {
    owner(u, one("select * from course where id=?", courseId));
    Map<String, Object> c = course(courseId);
    List<Map<String, Object>> r =
        rows(
            "select g.*,s.id student_id,s.student_no,a.name student_name from enrollment e join"
                + " student s on s.id=e.student_id join account a on a.id=s.account_id left join"
                + " grade g on g.course_id=e.course_id and g.student_id=e.student_id where"
                + " e.course_id=? and e.status='ENROLLED' order by s.student_no",
            courseId);
    for (Map<String, Object> x : r) {
      rename(x, "student_id", "studentId");
      rename(x, "student_no", "studentNo");
      rename(x, "student_name", "studentName");
      rename(x, "usual_score", "usualScore");
      rename(x, "midterm_score", "midtermScore");
      rename(x, "final_score", "finalScore");
      rename(x, "exam_status", "examStatus");
      x.remove("course_id");
      x.remove("published_applied");
    }
    return Map.of("course", c, "records", r);
  }

  @Transactional
  public void saveGrades(CurrentUser u, long courseId, Map<String, Object> x) {
    Map<String, Object> c = one("select * from course where id=? for update", courseId);
    owner(u, c);
    if (!"DRAFT".equals(c.get("grade_status"))) throw new ApiException(400, "成绩已进入流程，不能编辑");
    double w1 = decimal(x, "usualWeight", 0),
        w2 = decimal(x, "midtermWeight", 0),
        w3 = decimal(x, "finalWeight", 0);
    if (w1 < 0
        || w2 < 0
        || w3 < 0
        || w1 > 100
        || w2 > 100
        || w3 > 100
        || Math.abs(w1 + w2 + w3 - 100) > 0.001)
      throw new ApiException(400, "成绩权重必须在 0 到 100，且总和为 100");
    Object raw = x.get("records");
    if (!(raw instanceof List<?> list)) throw new ApiException(400, "成绩记录不能为空");
    db.update(
        "update course set usual_weight=?,midterm_weight=?,final_weight=? where id=?",
        w1,
        w2,
        w3,
        courseId);
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) throw new ApiException(400, "成绩记录格式错误");
      Map<String, Object> r = new HashMap<>();
      m.forEach((k, v) -> r.put(String.valueOf(k), v));
      long sid = longNum(r, "studentId");
      if (db.queryForObject(
              "select count(*) from enrollment where course_id=? and student_id=? and"
                  + " status='ENROLLED'",
              Integer.class,
              courseId,
              sid)
          == 0) throw new ApiException(400, "学生不在本课程名单中");
      String exam = opt(r, "examStatus", "NORMAL");
      if (!List.of("NORMAL", "ABSENT", "DEFERRED", "CHEATING").contains(exam))
        throw new ApiException(400, "考试状态无效");
      Double a = nullableScore(r, "usualScore"),
          b = nullableScore(r, "midtermScore"),
          d = nullableScore(r, "finalScore");
      if ("NORMAL".equals(exam) && (a == null || b == null || d == null))
        throw new ApiException(400, "正常考试必须录入完整分数");
      double score =
          "CHEATING".equals(exam)
              ? 0
              : Math.round(
                      ((a == null ? 0 : a) * w1
                              + (b == null ? 0 : b) * w2
                              + (d == null ? 0 : d) * w3)
                          / 100
                          * 100.0)
                  / 100.0;
      int existing =
          db.queryForObject(
              "select count(*) from grade where course_id=? and student_id=?",
              Integer.class,
              courseId,
              sid);
      if (existing == 0)
        db.update(
            "insert into"
                + " grade(course_id,student_id,usual_score,midterm_score,final_score,score,exam_status)"
                + " values(?,?,?,?,?,?,?)",
            courseId,
            sid,
            a,
            b,
            d,
            score,
            exam);
      else
        db.update(
            "update grade set usual_score=?,midterm_score=?,final_score=?,score=?,exam_status=?"
                + " where course_id=? and student_id=?",
            a,
            b,
            d,
            score,
            exam,
            courseId,
            sid);
    }
    after("refresh", Map.of("courseId", courseId));
  }

  @Transactional
  public void gradeTransition(CurrentUser u, long courseId, String action, String reason) {
    Map<String, Object> c = one("select * from course where id=? for update", courseId);
    if ("submit".equals(action)) {
      owner(u, c);
      if (!"DRAFT".equals(c.get("grade_status"))) throw new ApiException(400, "当前成绩状态不可提交");
      int enrolled =
          db.queryForObject(
              "select count(*) from enrollment where course_id=? and status='ENROLLED'",
              Integer.class,
              courseId);
      int completed =
          db.queryForObject(
              "select count(*) from grade g join enrollment e on e.course_id=g.course_id and"
                  + " e.student_id=g.student_id where g.course_id=? and e.status='ENROLLED' and"
                  + " g.exam_status in ('NORMAL','ABSENT','DEFERRED','CHEATING')",
              Integer.class,
              courseId);
      if (enrolled == 0 || completed != enrolled)
        throw new ApiException(400, "至少一名在选学生且每名学生均须完成成绩录入后才能提交");
      db.update("update course set grade_status='SUBMITTED' where id=?", courseId);
      notifyAccount(1, "成绩待审核", "《" + c.get("name") + "》成绩已提交审核");
    } else if ("approve".equals(action)) {
      require(u, "ADMIN");
      if (!"SUBMITTED".equals(c.get("grade_status"))) throw new ApiException(400, "当前成绩状态不可审核");
      db.update("update course set grade_status='APPROVED' where id=?", courseId);
    } else if ("reject".equals(action)) {
      require(u, "ADMIN");
      if (!"SUBMITTED".equals(c.get("grade_status"))) throw new ApiException(400, "当前成绩状态不可退回");
      db.update("update course set grade_status='DRAFT' where id=?", courseId);
      notifyAccount(
          ((Number) c.get("teacher_id")).longValue(),
          "成绩已退回",
          reason == null || reason.isBlank() ? "请修改后重新提交" : reason);
    } else if ("publish".equals(action)) {
      require(u, "ADMIN");
      if ("PUBLISHED".equals(c.get("grade_status"))) return;
      if (!"APPROVED".equals(c.get("grade_status"))) throw new ApiException(400, "成绩尚未审核通过");
      db.update("update course set grade_status='PUBLISHED' where id=?", courseId);
      List<Map<String, Object>> gs =
          rows(
              "select g.*,s.account_id from grade g join student s on s.id=g.student_id join"
                  + " enrollment e on e.course_id=g.course_id and e.student_id=g.student_id where"
                  + " g.course_id=? and e.status='ENROLLED' for update",
              courseId);
      for (Map<String, Object> g : gs)
        if (!Boolean.TRUE.equals(g.get("published_applied"))) {
          double score = ((Number) g.get("score")).doubleValue(),
              credit = ((Number) c.get("credit")).doubleValue();
          db.queryForObject(
              "select id from student where id=? for update", Long.class, g.get("student_id"));
          db.update(
              "update student set earned_credits=earned_credits+?,gpa=coalesce((select"
                  + " round(sum((case when gr.score<60 then 0 else"
                  + " least(4,1+floor((gr.score-60)/10))"
                  + " end)*co.credit)/nullif(sum(co.credit),0),2) from grade gr join course co on"
                  + " co.id=gr.course_id where gr.student_id=student.id and"
                  + " co.grade_status='PUBLISHED' and gr.exam_status='NORMAL'),0) where id=?",
              "NORMAL".equals(g.get("exam_status")) && score >= 60 ? credit : 0,
              g.get("student_id"));
          db.update("update grade set published_applied=true where id=?", g.get("id"));
          notifyAccount(
              ((Number) g.get("account_id")).longValue(), "成绩已发布", "《" + c.get("name") + "》成绩已发布");
        }
    }
    after("refresh", Map.of("courseId", courseId));
  }

  public Map<String, Object> myGrades(long account, String semester) {
    String w =
        " from grade g join student s on s.id=g.student_id join course c on c.id=g.course_id where"
            + " s.account_id=? and c.grade_status='PUBLISHED'";
    List<Object> p = new ArrayList<>(List.of(account));
    if (semester != null && !semester.isBlank()) {
      w += " and c.semester=?";
      p.add(semester);
    }
    List<Map<String, Object>> rs =
        rows(
            "select c.name course_name,c.semester,c.credit,g.score,g.exam_status" + w, p.toArray());
    for (Map<String, Object> row : rs) {
      rename(row, "course_name", "courseName");
      rename(row, "exam_status", "examStatus");
    }
    Map<String, Object> st =
        one("select earned_credits,required_credits,gpa from student where account_id=?", account);
    return Map.of(
        "records",
        rs,
        "earnedCredits",
        st.get("earned_credits"),
        "requiredCredits",
        st.get("required_credits"),
        "gpa",
        st.get("gpa"));
  }

  public Map<String, Object> dashboard(CurrentUser u, boolean redisUp) {
    List<Map<String, Object>> stats = new ArrayList<>();
    if (u.is("ADMIN")) {
      stats.add(stat("在校学生", db.queryForObject("select count(*) from student", Long.class), "人"));
      stats.add(
          stat(
              "开设课程",
              db.queryForObject("select count(*) from course where status='PUBLISHED'", Long.class),
              "门"));
    } else if (u.is("TEACHER")) {
      stats.add(
          stat(
              "我的课程",
              db.queryForObject(
                  "select count(*) from course where teacher_id=?", Long.class, u.id()),
              "门"));
      stats.add(
          stat(
              "待审核成绩",
              db.queryForObject(
                  "select count(*) from course where teacher_id=? and grade_status='SUBMITTED'",
                  Long.class,
                  u.id()),
              "门"));
    } else {
      Map<String, Object> s =
          one("select earned_credits,gpa from student where account_id=?", u.id());
      stats.add(stat("已修学分", s.get("earned_credits"), ""));
      stats.add(stat("平均绩点", s.get("gpa"), ""));
    }
    List<Map<String, Object>> creditsByDepartment = List.of();
    List<Map<String, Object>> gradeDistribution;
    List<Map<String, Object>> courseEnrollment;
    // 不按别名分组，避免 MySQL 将 name 误解析为 course.name；兼容 ONLY_FULL_GROUP_BY。
    String scoreBucket =
        "case when g.score>=90 then '90-100' when g.score>=80 then '80-89' when g.score>=70"
            + " then '70-79' when g.score>=60 then '60-69' else '不及格' end";
    String distributionSql =
        "select "
            + scoreBucket
            + " name,count(*) value from grade g join course c on c.id=g.course_id where"
            + " c.grade_status='PUBLISHED'";
    if (u.is("ADMIN")) {
      creditsByDepartment =
          rows(
              "select s.department name,coalesce(sum(s.earned_credits),0) value from student s"
                  + " group by s.department");
      gradeDistribution = rows(distributionSql + " group by " + scoreBucket);
      courseEnrollment =
          rows(
              "select name,enrolled value,capacity from course where status='PUBLISHED' order by"
                  + " enrolled desc limit 8");
    } else if (u.is("TEACHER")) {
      gradeDistribution =
          rows(distributionSql + " and c.teacher_id=? group by " + scoreBucket, u.id());
      courseEnrollment =
          rows(
              "select name,enrolled value,capacity from course where teacher_id=? order by enrolled"
                  + " desc limit 8",
              u.id());
    } else {
      gradeDistribution =
          rows(
              distributionSql
                  + " and exists(select 1 from student s where s.id=g.student_id and"
                  + " s.account_id=?) group by "
                  + scoreBucket,
              u.id());
      courseEnrollment = List.of();
    }
    return Map.of(
        "stats",
        stats,
        "creditsByDepartment",
        creditsByDepartment,
        "gradeDistribution",
        gradeDistribution,
        "courseEnrollment",
        courseEnrollment,
        "recentActivities",
        notifications(u.id()),
        "cache",
        Map.of("backend", redisUp ? "UP" : "DOWN", "hit", false, "ttlSeconds", 60));
  }

  public List<Map<String, Object>> notifications(long id) {
    return rows(
        "select id,title,content,created_at createdAt, is_read `read` from notification where"
            + " account_id=? order by created_at desc limit 20",
        id);
  }

  public void readNotification(CurrentUser u, long id) {
    if (db.update("update notification set is_read=true where id=? and account_id=?", id, u.id())
        == 0) throw new ApiException(404, "通知不存在");
  }

  @Transactional
  public void notifyAccount(long aid, String title, String content) {
    long id =
        insert(
            "insert into notification(account_id,title,content) values(?,?,?)",
            aid,
            title,
            content);
    after("notification", Map.of("id", id, "accountId", aid, "title", title));
  }

  private void after(String event, Object data) {
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              public void afterCommit() {
                try {
                  // 只能在事务提交后驱逐缓存：回滚的选课或成绩不能让页面读到不存在的变化。
                  Set<String> keys = redis.keys("stu_manage:dashboard:*");
                  if (keys != null && !keys.isEmpty()) redis.delete(keys);
                } catch (Exception ignored) {
                }
                events.send(event, data);
              }
            });
  }

  public Map<String, Object> systemStatus() {
    Map<String, Object> database = new LinkedHashMap<>();
    try (var connection = dataSource.getConnection()) {
      var metadata = connection.getMetaData();
      database.put("status", "UP");
      database.put("product", metadata.getDatabaseProductName());
      database.put("version", metadata.getDatabaseProductVersion());
    } catch (Exception ignored) {
      database.put("status", "DOWN");
    }

    Map<String, Object> redisStatus = new LinkedHashMap<>();
    try {
      String reply =
          redis.execute(
              (org.springframework.data.redis.core.RedisCallback<String>)
                  connection -> connection.ping());
      redisStatus.put("status", "PONG".equalsIgnoreCase(reply) ? "UP" : "DOWN");
    } catch (Exception ignored) {
      redisStatus.put("status", "DOWN");
    }

    return Map.of(
        "database",
        database,
        "redis",
        redisStatus,
        "app",
        Map.of(
            "javaVersion",
            System.getProperty("java.version"),
            "springBootVersion",
            SpringBootVersion.getVersion()));
  }

  private void updateCourseDescription(Map<String, Object> x, long id, Map<String, Object> course) {
    rejectLockedCourseChanges(x, course);
    db.update(
        "update course set location=?,description=?,cover_url=? where id=?",
        valueOrCurrent(x, "location", course.get("location")),
        valueOrCurrent(x, "description", course.get("description")),
        valueOrCurrent(x, "coverUrl", course.get("cover_url")),
        id);
  }

  private void rejectLockedCourseChanges(Map<String, Object> x, Map<String, Object> course) {
    Map<String, Object> locked =
        Map.of(
            "code", course.get("code"),
            "name", course.get("name"),
            "teacherId", course.get("teacher_id"),
            "credit", course.get("credit"),
            "hours", course.get("hours"),
            "semester", course.get("semester"),
            "schedule", course.get("schedule_text"),
            "capacity", course.get("capacity"));
    for (Map.Entry<String, Object> entry : locked.entrySet()) {
      if (x.containsKey(entry.getKey()) && !sameValue(x.get(entry.getKey()), entry.getValue())) {
        throw new ApiException(400, "课程已发布，不能修改" + entry.getKey());
      }
    }
  }

  private boolean sameValue(Object requested, Object stored) {
    if (requested == null || stored == null) return requested == stored;
    if (requested instanceof Number || stored instanceof Number) {
      try {
        return new java.math.BigDecimal(String.valueOf(requested))
                .compareTo(new java.math.BigDecimal(String.valueOf(stored)))
            == 0;
      } catch (NumberFormatException ignored) {
        return false;
      }
    }
    return String.valueOf(requested).equals(String.valueOf(stored));
  }

  private Object valueOrCurrent(Map<String, Object> x, String key, Object current) {
    return x.containsKey(key) ? x.get(key) : current;
  }

  private long courseTeacher(CurrentUser u, Map<String, Object> x, Map<String, Object> course) {
    long current = number(course, "teacher_id").longValue();
    if (u.is("TEACHER")) {
      if (x.containsKey("teacherId") && longNum(x, "teacherId") != current) {
        throw new ApiException(403, "教师不能转让本人课程");
      }
      return current;
    }
    return x.containsKey("teacherId") ? longNum(x, "teacherId") : current;
  }

  private void validateTeacher(long teacherId) {
    Map<String, Object> account = one("select role,status from account where id=?", teacherId);
    if (!"TEACHER".equals(account.get("role")) || !"ENABLED".equals(account.get("status"))) {
      throw new ApiException(400, "授课教师无效");
    }
  }

  private void validateCourseNumbers(double credit, int hours, int capacity) {
    if (credit <= 0 || hours <= 0 || capacity <= 0) {
      throw new ApiException(400, "学分、课时和容量必须为正数");
    }
  }

  private Number number(Map<String, Object> x, String key) {
    Object value = x.get(key);
    if (!(value instanceof Number)) throw new ApiException(400, key + " 必须是数字");
    return (Number) value;
  }

  private Map<String, Object> stat(String label, Object value, String suffix) {
    return Map.of("label", label, "value", value, "suffix", suffix);
  }

  private double point(double score) {
    return score < 60 ? 0 : Math.min(4, 1 + Math.floor((score - 60) / 10));
  }

  private Double nullableScore(Map<String, Object> x, String k) {
    if (x.get(k) == null || String.valueOf(x.get(k)).isBlank()) return null;
    double d = Double.parseDouble(String.valueOf(x.get(k)));
    if (d < 0 || d > 100) throw new ApiException(400, "分数必须在 0 到 100");
    return d;
  }

  private Map<String, Object> user(Map<String, Object> a) {
    Map<String, Object> r = new LinkedHashMap<>();
    for (String k :
        List.of("id", "username", "name", "role", "status", "department", "phone", "photo"))
      r.put(camel(k), a.get(k));
    return r;
  }

  private Map<String, Object> course(long id) {
    return courseRows(
            "select c.*,a.name teacher_name from course c join account a on a.id=c.teacher_id where"
                + " c.id=?",
            id)
        .get(0);
  }

  private Map<String, Object> studentRow(long id) {
    return studentRows(
            "select s.*,a.name,a.phone,a.status from student s join account a on a.id=s.account_id"
                + " where s.id=?",
            id)
        .get(0);
  }

  private List<Map<String, Object>> courseRows(String sql, Object... p) {
    List<Map<String, Object>> r = rows(sql, p);
    for (Map<String, Object> x : r) {
      rename(x, "teacher_id", "teacherId");
      rename(x, "teacher_name", "teacherName");
      rename(x, "schedule_text", "schedule");
      rename(x, "cover_url", "coverUrl");
      rename(x, "grade_status", "gradeStatus");
      rename(x, "usual_weight", "usualWeight");
      rename(x, "midterm_weight", "midtermWeight");
      rename(x, "final_weight", "finalWeight");
    }
    return r;
  }

  private List<Map<String, Object>> studentRows(String sql, Object... p) {
    List<Map<String, Object>> r = rows(sql, p);
    for (Map<String, Object> x : r) {
      rename(x, "student_no", "studentNo");
      rename(x, "class_name", "className");
      rename(x, "enrollment_year", "enrollmentYear");
      rename(x, "earned_credits", "earnedCredits");
      rename(x, "required_credits", "requiredCredits");
      x.remove("account_id");
    }
    return r;
  }

  private List<Map<String, Object>> rows(String sql, Object... p) {
    return db.query(
        sql,
        (rs, n) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
            m.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
          return m;
        },
        p);
  }

  private Map<String, Object> one(String sql, Object... p) {
    List<Map<String, Object>> r = rows(sql, p);
    if (r.isEmpty()) throw new ApiException(404, "数据不存在");
    return r.get(0);
  }

  private long insert(String sql, Object... p) {
    KeyHolder h = new GeneratedKeyHolder();
    db.update(
        c -> {
          PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < p.length; i++) s.setObject(i + 1, p[i]);
          return s;
        },
        h);
    return h.getKey().longValue();
  }

  private void require(CurrentUser u, String role) {
    if (!u.is(role)) throw new ApiException(403, "权限不足");
  }

  private String required(Map<String, Object> x, String k) {
    String s = opt(x, k, null);
    if (s == null || s.isBlank()) throw new ApiException(400, k + " 不能为空");
    return s.trim();
  }

  private String opt(Map<String, Object> x, String k, String d) {
    Object v = x.get(k);
    return v == null ? d : String.valueOf(v);
  }

  private int num(Map<String, Object> x, String k, int d) {
    Object v = x.get(k);
    return v == null ? d : Integer.parseInt(String.valueOf(v));
  }

  private long longNum(Map<String, Object> x, String k) {
    try {
      return Long.parseLong(required(x, k));
    } catch (NumberFormatException e) {
      throw new ApiException(400, k + " 必须是数字");
    }
  }

  private double decimal(Map<String, Object> x, String k, double d) {
    Object v = x.get(k);
    return v == null ? d : Double.parseDouble(String.valueOf(v));
  }

  private Object[] append(Object[] a, Object... b) {
    Object[] r = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, r, a.length, b.length);
    return r;
  }

  private void rename(Map<String, Object> m, String old, String now) {
    m.put(now, m.remove(old));
  }

  private String camel(String s) {
    return s;
  }
}
