package com.zjgsu.sfy.identity.repository;

import com.zjgsu.sfy.identity.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByUsername(String username);

  Optional<User> findByPhone(String phone);

  boolean existsByUsername(String username);

  boolean existsByPhone(String phone);
}
