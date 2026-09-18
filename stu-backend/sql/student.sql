create table student
(
    id          int auto_increment comment '主键'
        primary key,
    name        varchar(255)  null,
    grade       varchar(255)  null,
    phone       varchar(255)  null,
    earnCredits int default 0 null
);

INSERT INTO stu.student (id, name, grade, phone, earnCredits) VALUES (1, 'w5', '2025', '10086', 10);
INSERT INTO stu.student (id, name, grade, phone, earnCredits) VALUES (2, 'l4', '2025', '10087', 11);
