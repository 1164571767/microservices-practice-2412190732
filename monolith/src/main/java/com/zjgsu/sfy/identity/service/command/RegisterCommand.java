package com.zjgsu.sfy.identity.service.command;

public record RegisterCommand(String username, String phone, String password, String displayName) {}
