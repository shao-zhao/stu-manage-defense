package com.example.stubackend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Repairs only the historical placeholder hash; later user password changes remain untouched. */
@Component
public class SeedPasswordMigrator implements CommandLineRunner {
    private static final String PLACEHOLDER = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final JdbcTemplate db;
    public SeedPasswordMigrator(JdbcTemplate db) { this.db = db; }
    @Override public void run(String... args) {
        db.update("update account set password_hash=? where password_hash=?",
                new BCryptPasswordEncoder().encode("123456"), PLACEHOLDER);
    }
}
