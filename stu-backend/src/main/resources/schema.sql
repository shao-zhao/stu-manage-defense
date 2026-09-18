CREATE TABLE IF NOT EXISTS account (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 name VARCHAR(64) NOT NULL, role VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 department VARCHAR(80), phone VARCHAR(30), photo VARCHAR(255), token_version INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS student (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, account_id BIGINT NOT NULL UNIQUE, student_no VARCHAR(32) NOT NULL UNIQUE,
 gender VARCHAR(8), department VARCHAR(80), major VARCHAR(80), class_name VARCHAR(80), enrollment_year INT,
 earned_credits DECIMAL(8,2) NOT NULL DEFAULT 0, required_credits DECIMAL(8,2) NOT NULL DEFAULT 140,
 gpa DECIMAL(4,2) NOT NULL DEFAULT 0, CONSTRAINT fk_student_account FOREIGN KEY(account_id) REFERENCES account(id)
);
CREATE TABLE IF NOT EXISTS course (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL, teacher_id BIGINT NOT NULL,
 credit DECIMAL(4,1) NOT NULL, hours INT NOT NULL, semester VARCHAR(32) NOT NULL, schedule_text VARCHAR(100), location VARCHAR(100),
 capacity INT NOT NULL, enrolled INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'UNPUBLISHED',
 cover_url VARCHAR(255), description TEXT, grade_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', usual_weight DECIMAL(5,2) NOT NULL DEFAULT 30, midterm_weight DECIMAL(5,2) NOT NULL DEFAULT 30, final_weight DECIMAL(5,2) NOT NULL DEFAULT 40, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_course_teacher FOREIGN KEY(teacher_id) REFERENCES account(id)
);
CREATE TABLE IF NOT EXISTS enrollment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, course_id BIGINT NOT NULL, student_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ENROLLED',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_enroll_course_student(course_id,student_id), CONSTRAINT fk_enroll_course FOREIGN KEY(course_id) REFERENCES course(id), CONSTRAINT fk_enroll_student FOREIGN KEY(student_id) REFERENCES student(id)
);
CREATE TABLE IF NOT EXISTS grade (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, course_id BIGINT NOT NULL, student_id BIGINT NOT NULL, usual_score DECIMAL(5,2), midterm_score DECIMAL(5,2), final_score DECIMAL(5,2), score DECIMAL(5,2),
 exam_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL', published_applied BOOLEAN NOT NULL DEFAULT FALSE, UNIQUE KEY uk_grade_course_student(course_id,student_id),
 CONSTRAINT fk_grade_course FOREIGN KEY(course_id) REFERENCES course(id), CONSTRAINT fk_grade_student FOREIGN KEY(student_id) REFERENCES student(id)
);
CREATE TABLE IF NOT EXISTS notification (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, account_id BIGINT NOT NULL, title VARCHAR(150) NOT NULL, content VARCHAR(500) NOT NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_notification_account FOREIGN KEY(account_id) REFERENCES account(id)
);
CREATE TABLE IF NOT EXISTS media (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, owner_id BIGINT NOT NULL, title VARCHAR(150) NOT NULL, url VARCHAR(255) NOT NULL, kind VARCHAR(16) NOT NULL, course_id BIGINT,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_media_owner FOREIGN KEY(owner_id) REFERENCES account(id)
);
INSERT IGNORE INTO account(id,username,password_hash,name,role,status,department,phone) VALUES
 (1,'admin','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','教务管理员','ADMIN','ENABLED','教务处','13800000001'),
 (2,'teacher01','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','王老师','TEACHER','ENABLED','计算机学院','13800000002'),
 (3,'student01','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','李同学','STUDENT','ENABLED','计算机学院','13800000003');
INSERT IGNORE INTO student(id,account_id,student_no,gender,department,major,class_name,enrollment_year) VALUES (1,3,'20260001','男','计算机学院','软件工程','软件2401',2024);
INSERT IGNORE INTO course(id,code,name,teacher_id,credit,hours,semester,schedule_text,location,capacity,status,description) VALUES (1,'SE101','Java 程序设计',2,3.0,48,'2026-2027-1','周一 1-2 节','A201',40,'PUBLISHED','面向对象程序设计基础课程');
