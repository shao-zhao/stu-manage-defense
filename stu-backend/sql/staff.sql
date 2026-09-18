create table staff
(
    id       int auto_increment
        primary key,
    username varchar(255) null,
    password varchar(255) null,
    photo    varchar(255) null
);

INSERT INTO stu.staff (id, username, password, photo) VALUES (1, 'admin', '123456', null);
INSERT INTO stu.staff (id, username, password, photo) VALUES (2, 'root', '123456', null);
