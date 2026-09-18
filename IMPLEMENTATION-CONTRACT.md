# 学生管理系统答辩版 实现约定

2026-09-19 用户授权：基于现有项目完成完整答辩版，优雅美观、简单易懂，新建 GitHub 私有仓库，阶段性提交。GPT-6 统筹，GPT-5.6 Terra 实现。原项目和 Git 历史已备份到 D:/codex/backups/stu-manage-20260919-before-upgrade。不要重置或删除原 Git 仓库。原 stu 数据库不改；新版本使用独立 stu_manage 数据库。

## 范围和结束条件

管理员/教学秘书、教师、学生三角色真实登录；学生/教职工账号管理、冻结、重置密码；课程创建编辑发布（发布后锁定）；学生选课/退课与容量校验；成绩草稿、提交、审核退回/通过、发布、学分及 GPA；按角色首页、个人资料、通知、导入导出。五项扩展：ECharts、真实 Redis 缓存、SSE 实时通知、Open-Meteo 天气、图片/视频上传预览。交付 README、数据库脚本、启动方式、5 分钟演示稿、核心代码讲解及测试记录。所有用户操作需加载/错误/空状态。禁止伪造 API、缓存、图表统计或实时消息。

## 技术和风格

保留 Spring Boot 4.1.1 / Java、MyBatis-Plus / MySQL、Vue 3 / TypeScript / Vite / Element Plus。后端可降至 Java 17 兼容（现有环境也有 JDK26），前端不换 React。不引入复杂框架/泛型 CRUD 工厂。UI 学术管理清爽风格，浅灰白背景、深绿主色、清晰侧栏、内容标题、克制动效，统一 Element Plus 图标。响应式适配 1280/1440 桌面以及窄屏。中文真实业务内容。

## API 总约定（前后端按此实现，必要调整先协商）

- 所有 JSON 正常返回 `{code:202,message:'success',data:T}`，业务失败 `{code:505,message,data:null}`；未登录 HTTP401，越权 HTTP403，输入错误 HTTP400。前端统一解包为 T。Header `Authorization: Bearer <JWT>`。
- 登录 `POST /api/auth/login {username,password,role}` -> `{token,user}`；role ADMIN/TEACHER/STUDENT。`GET /api/auth/me` -> user；`POST /api/auth/logout`；`PUT /api/auth/password {oldPassword,newPassword}`。
- user `{id,username,name,role,status,department,phone,photo}`，状态 ENABLED/FROZEN/SUSPENDED（仅学生）。登录 JWT 12小时+BCrypt，禁止返回 password/hash；每次请求验证账号状态。
- `GET /api/students?page=1&size=10&keyword=&status=` -> `{records,total}`；`POST /api/students`、`PUT /api/students/{id}`；`PUT /api/students/{id}/status {status}`；`POST /api/students/{id}/reset-password`；学生无删除。字段 `{id,studentNo,name,gender,phone,department,major,className,enrollmentYear,status,earnedCredits,requiredCredits,gpa}`。创建账号可自动生成学号，初始密码123456。
- `GET /api/staff?page=1&size=10&keyword=` -> `{records,total}`；POST/PUT 同上，status/reset-password 同上。字段 `{id,username,name,role,department,phone,photo,status}`，仅ADMIN管理。`GET /api/staff/teachers` -> 简单教师列表。
- `GET /api/courses?keyword=&semester=&mine=false` -> Course[]（教师仅本人、学生仅已发布）；POST `/api/courses`、PUT `/api/courses/{id}`、POST `/api/courses/{id}/publish`。Course `{id,code,name,teacherId,teacherName,credit,hours,semester,schedule,location,capacity,enrolled,status,coverUrl,description,gradeStatus,myEnrollmentStatus}`；状态 UNPUBLISHED/PUBLISHED；gradeStatus DRAFT/SUBMITTED/APPROVED/PUBLISHED。
- `POST /api/courses/{id}/enroll`、`POST /api/courses/{id}/withdraw`；`GET /api/enrollments/mine` -> enrollment records 包括 courseId/courseName/teacherName/semester/credit/schedule/location/status。退课保留WITHDRAWN记录，可再次选课；成绩已提交后不可退课。事务+锁保护容量与重复选课。
- `GET /api/courses/{id}/grades` -> `{course,records}`，records `{id,studentId,studentNo,studentName,usualScore,midtermScore,finalScore,score,examStatus}`；`PUT /api/courses/{id}/grades {usualWeight,midtermWeight,finalWeight,records}` 三权重和100，分数0-100，examStatus NORMAL/ABSENT/DEFERRED/CHEATING。仅本人教师/管理员允许查看管理；学生只能查发布成绩。
- `POST /api/courses/{id}/grades/submit`；`POST .../approve`；`POST .../reject {reason}`；`POST .../publish`。发布事务一次性汇总成绩、学分与GPA（加权平均绩点，60及格，60→1.0、每10分+1、100→4.0，最高4.0；未及格绩点0）。重复发布不能重复加学分。
- `GET /api/grades/mine?semester=` -> `{records,earnedCredits,requiredCredits,gpa}`，records 包括 courseName,semester,credit,score,examStatus。
- `GET /api/dashboard` -> `{stats:[{label,value,suffix}],creditsByDepartment:[{name,value}],gradeDistribution:[{name,value}],courseEnrollment:[{name,value,capacity}],recentActivities:[{id,title,content,createdAt}],cache:{backend,hit,ttlSeconds}}`；按角色仅能读有权限数据，统计来自数据库。
- `GET /api/notifications` -> `{id,title,content,createdAt,read}[]`；`PUT /api/notifications/{id}/read`。`GET /api/events` SSE，通过 fetch 流带 Authorization，事件 `notification`（新增通知）、`refresh`（业务数据变化）、`heartbeat`；不要在 URL 放登录token。
- `GET /api/weather` -> `{available,city,temperature,weatherCode,windSpeed,observedAt,source,message}`，默认北京示例校园，真实 Open-Meteo 后端HTTP请求、超时控制、失败明确 unavailable，不能假数据。
- `POST /api/files` multipart file -> `{url,name,kind,size}`，kind image/video，验证类型大小并 UUID 重命名；`GET /api/media` -> 资料列表；`POST /api/media {title,url,kind,courseId}`；`DELETE /api/media/{id}` 仅所有者/管理员。资源范围可先全校教学共享，显示作者。
- 学生 Excel 导入导出 `/api/students/import` (multipart file) 和 `/api/students/export` (xlsx)，模板 `/api/students/template`。导入失败全回滚，限制行数、校验重复学号；导出不含密码。前端下载通过已认证 blob 请求。

## 交付分工

- backend agent 独占 stu-backend，实现业务/SQL/集成测试；可以加合适依赖。每阶段本地 commit，最开始先 commit 现有用户改动作为基线。不记录密码、token。
- frontend agent 独占 stu-frontend，按此API实现页面及视觉体验；安装 echarts 与需要的 Element Plus 图标，保持代码直白。保留原学习笔记；替换不再适用的旧 demo 页面可以移至 docs/archive。阶段 commit。
- delivery agent 独占根目录 scripts、docs、README、compose.yaml、.gitignore 等交付文件及 staging GitHub 发布仓库，运行环境/Redis准备。不得修改前后端源码；结合后端脚本更新启动步骤。GitHub 登录 shao-zhao 已可用，用户明确授权私有新仓库。发布前检查机密/本地路径，排除 .env、application-local.properties、uploads、node_modules、target。发布仓库整合实际代码，不能把嵌套仓库当 gitlink。
- root 统筹接口/疑点/验收，不重做代理实现；各代理每完成阶段只回报文件、commit、验证、阻塞。需要协作使用 collaboration.send_message。
