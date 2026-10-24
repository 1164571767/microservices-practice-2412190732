package com.zjgsu.sfy.identity.service.command;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import java.util.Set;

public record UpdateEmployeeCommand(
    long version,
    String displayName,
    String phone,
    Set<Role> roles,
    UserStatus status,
    String reason) {}
