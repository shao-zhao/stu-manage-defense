package com.example.stubackend.security;

public record CurrentUser(long id, String role, int tokenVersion) {
    public boolean is(String... roles) { for (String role : roles) if (this.role.equals(role)) return true; return false; }
}
