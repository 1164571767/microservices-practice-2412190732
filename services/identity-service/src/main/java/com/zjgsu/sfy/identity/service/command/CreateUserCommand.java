package com.zjgsu.sfy.identity.service.command;

import com.zjgsu.sfy.identity.common.Role;
import java.util.Set;

public record CreateUserCommand(
    String username, String phone, String password, String displayName, Set<Role> roles) {}
