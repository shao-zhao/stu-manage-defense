package com.example.stubackend.controller;

import com.example.stubackend.model.Result;
import com.example.stubackend.security.CurrentUser;
import com.example.stubackend.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/api")
public class OperationsController {
  private final Path upload;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public OperationsController(
      @Value("${upload.path}") String upload, JdbcTemplate db, ObjectMapper json) {
    this.upload = Path.of(upload).toAbsolutePath();
    this.db = db;
    this.json = json;
  }

  private CurrentUser user(HttpServletRequest r) {
    return (CurrentUser) r.getAttribute("user");
  }

  private void admin(HttpServletRequest r) {
    if (!user(r).is("ADMIN")) throw new ApiException(403, "仅管理员可操作");
  }

  @PostMapping("/files")
  @ResponseBody
  public Result<Map<String, Object>> upload(
      HttpServletRequest r, @RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new ApiException(400, "请选择文件");
    String original = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
    String ext =
        original.lastIndexOf('.') >= 0
            ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT)
            : "";
    String type = Optional.ofNullable(file.getContentType()).orElse("");
    String kind = type.startsWith("image/") ? "image" : type.startsWith("video/") ? "video" : null;
    if (kind == null) throw new ApiException(400, "仅支持图片或视频");
    if ("image".equals(kind)
        && (!Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp").contains(ext)
            || ImageIO.read(file.getInputStream()) == null))
      throw new ApiException(400, "图片格式或内容无效");
    if ("video".equals(kind) && !Set.of(".mp4", ".webm", ".mov").contains(ext))
      throw new ApiException(400, "视频格式无效");
    if ("video".equals(kind) && !hasVideoSignature(file, ext))
      throw new ApiException(400, "视频格式或内容无效");
    long max = "image".equals(kind) ? 10 * 1024 * 1024L : 50 * 1024 * 1024L;
    if (file.getSize() > max) throw new ApiException(400, "文件过大");
    Files.createDirectories(upload);
    String name = UUID.randomUUID() + ext;
    try (InputStream in = file.getInputStream()) {
      Files.copy(in, upload.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
    return Result.success(
        Map.of("url", "/static/" + name, "name", original, "kind", kind, "size", file.getSize()));
  }

  @GetMapping("/media")
  @ResponseBody
  public Result<List<Map<String, Object>>> media() {
    return Result.success(
        db.query(
            "select m.id,m.owner_id ownerId,m.title,m.url,m.kind,m.course_id courseId,m.created_at"
                + " createdAt,a.name author from media m join account a on a.id=m.owner_id order by"
                + " m.id desc",
            (rs, n) -> {
              Map<String, Object> x = new LinkedHashMap<>();
              for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                x.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
              return x;
            }));
  }

  @PostMapping("/media")
  @ResponseBody
  @Transactional
  public Result<Void> addMedia(HttpServletRequest r, @RequestBody Map<String, Object> x) {
    String kind = String.valueOf(x.get("kind"));
    if (!List.of("image", "video").contains(kind)) throw new ApiException(400, "资料类型无效");
    Object requestedCourseId = x.get("courseId");
    Long courseId = requestedCourseId == null ? null : parseCourseId(requestedCourseId);
    if (courseId != null) {
      // 与课程删除共用行锁，防止删除检查通过后并发插入一条孤立资料。
      List<Map<String, Object>> courses =
          db.queryForList("select teacher_id from course where id=? for update", courseId);
      if (courses.isEmpty()) throw new ApiException(404, "关联课程不存在");
      if (!user(r).is("ADMIN")
          && (!user(r).is("TEACHER")
              || ((Number) courses.get(0).get("teacher_id")).longValue() != user(r).id())) {
        throw new ApiException(403, "仅任课教师或管理员可关联课程资料");
      }
    }
    db.update(
        "insert into media(owner_id,title,url,kind,course_id) values(?,?,?,?,?)",
        user(r).id(),
        required(x, "title"),
        required(x, "url"),
        kind,
        courseId);
    return Result.success(null);
  }

  private long parseCourseId(Object value) {
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      throw new ApiException(400, "courseId 必须是数字");
    }
  }

  @DeleteMapping("/media/{id}")
  @ResponseBody
  public Result<Void> deleteMedia(HttpServletRequest r, @PathVariable long id) {
    int n =
        user(r).is("ADMIN")
            ? db.update("delete from media where id=?", id)
            : db.update("delete from media where id=? and owner_id=?", id, user(r).id());
    if (n == 0) throw new ApiException(404, "资料不存在或无权删除");
    return Result.success(null);
  }

  @GetMapping("/weather")
  @ResponseBody
  public Result<Map<String, Object>> weather() {
    try {
      HttpRequest q =
          HttpRequest.newBuilder(
                  URI.create(
                      "https://api.open-meteo.com/v1/forecast?latitude=39.9042&longitude=116.4074&current=temperature_2m,weather_code,wind_speed_10m&timezone=Asia%2FShanghai"))
              .timeout(Duration.ofSeconds(4))
              .GET()
              .build();
      String body = HttpClient.newHttpClient().send(q, HttpResponse.BodyHandlers.ofString()).body();
      JsonNode current = json.readTree(body).path("current");
      if (current.isMissingNode()) throw new IOException();
      Map<String, Object> x = new LinkedHashMap<>();
      x.put("available", true);
      x.put("city", "北京");
      x.put("temperature", current.path("temperature_2m").asDouble());
      x.put("weatherCode", current.path("weather_code").asInt());
      x.put("windSpeed", current.path("wind_speed_10m").asDouble());
      x.put("observedAt", current.path("time").asText());
      x.put("source", "Open-Meteo");
      x.put("message", "");
      return Result.success(x);
    } catch (Exception e) {
      return Result.success(
          Map.of(
              "available",
              false,
              "city",
              "北京",
              "temperature",
              0,
              "weatherCode",
              0,
              "windSpeed",
              0,
              "observedAt",
              "",
              "source",
              "Open-Meteo",
              "message",
              "天气服务暂时不可用"));
    }
  }

  @GetMapping("/students/template")
  public void template(HttpServletRequest r, HttpServletResponse res) throws IOException {
    admin(r);
    writeWorkbook(
        res,
        "students-template.xlsx",
        Collections.singletonList(new String[] {"学号", "姓名", "性别", "手机号", "学院", "专业", "班级", "入学年份"}),
        "学生导入模板");
  }

  @GetMapping("/students/export")
  public void export(HttpServletRequest r, HttpServletResponse res) throws IOException {
    admin(r);
    List<String[]> data = new ArrayList<>();
    data.add(new String[] {"学号", "姓名", "性别", "手机号", "学院", "专业", "班级", "入学年份", "状态", "已修学分", "GPA"});
    db.query(
        "select"
            + " s.student_no,a.name,s.gender,a.phone,s.department,s.major,s.class_name,s.enrollment_year,a.status,s.earned_credits,s.gpa"
            + " from student s join account a on a.id=s.account_id order by s.student_no",
        rs -> {
          data.add(
              new String[] {
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                String.valueOf(rs.getInt(8)),
                rs.getString(9),
                String.valueOf(rs.getBigDecimal(10)),
                String.valueOf(rs.getBigDecimal(11))
              });
        });
    writeWorkbook(res, "students.xlsx", data, "学生列表");
  }

  @PostMapping("/students/import")
  @ResponseBody
  @Transactional
  public Result<Map<String, Object>> importStudents(
      HttpServletRequest r, @RequestParam("file") MultipartFile file) throws IOException {
    admin(r);
    if (file.getSize() > 2 * 1024 * 1024) throw new ApiException(400, "导入文件过大");
    int count = 0;
    try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
      Sheet sh = wb.getSheetAt(0);
      if (sh.getLastRowNum() > 500) throw new ApiException(400, "最多导入 500 行");
      for (int i = 1; i <= sh.getLastRowNum(); i++) {
        Row row = sh.getRow(i);
        if (row == null || cell(row, 0).isBlank()) continue;
        String no = cell(row, 0), name = cell(row, 1);
        if (name.isBlank()) throw new ApiException(400, "第 " + (i + 1) + " 行姓名不能为空");
        if (db.queryForObject("select count(*) from student where student_no=?", Integer.class, no)
            > 0) throw new ApiException(400, "学号重复：" + no);
        String username = no;
        db.update(
            "insert into account(username,password_hash,name,role,status,department,phone)"
                + " values(?,?,?,'STUDENT','ENABLED',?,?)",
            username,
            bcrypt.encode("123456"),
            name,
            cell(row, 4),
            cell(row, 3));
        Long aid =
            db.queryForObject("select id from account where username=?", Long.class, username);
        db.update(
            "insert into"
                + " student(account_id,student_no,gender,department,major,class_name,enrollment_year)"
                + " values(?,?,?,?,?,?,?)",
            aid,
            no,
            cell(row, 2),
            cell(row, 4),
            cell(row, 5),
            cell(row, 6),
            Integer.parseInt(cell(row, 7).isBlank() ? "2024" : cell(row, 7)));
        count++;
      }
    }
    return Result.success(Map.of("imported", count));
  }

  private void writeWorkbook(
      HttpServletResponse res, String name, List<String[]> data, String sheet) throws IOException {
    res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name);
    res.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    try (Workbook wb = new XSSFWorkbook()) {
      Sheet s = wb.createSheet(sheet);
      for (int i = 0; i < data.size(); i++) {
        Row r = s.createRow(i);
        for (int j = 0; j < data.get(i).length; j++) r.createCell(j).setCellValue(data.get(i)[j]);
      }
      for (int i = 0; i < (data.isEmpty() ? 0 : data.get(0).length); i++) s.autoSizeColumn(i);
      wb.write(res.getOutputStream());
    }
  }

  private String cell(Row r, int i) {
    Cell c = r.getCell(i);
    if (c == null) return "";
    c.setCellType(CellType.STRING);
    return c.getStringCellValue().trim();
  }

  private boolean hasVideoSignature(MultipartFile file, String extension) throws IOException {
    try (InputStream stream = file.getInputStream()) {
      byte[] header = stream.readNBytes(16);
      if (".webm".equals(extension)) {
        return header.length >= 4
            && header[0] == 0x1A
            && header[1] == 0x45
            && header[2] == (byte) 0xDF
            && header[3] == (byte) 0xA3;
      }
      return header.length >= 12
          && header[4] == 'f'
          && header[5] == 't'
          && header[6] == 'y'
          && header[7] == 'p';
    }
  }

  private String required(Map<String, Object> x, String k) {
    Object v = x.get(k);
    if (v == null || String.valueOf(v).isBlank()) throw new ApiException(400, k + "不能为空");
    return String.valueOf(v);
  }
}
