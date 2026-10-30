package com.zjgsu.sfy.identity.repository;

import com.zjgsu.sfy.identity.common.Role;
import com.zjgsu.sfy.identity.common.UserStatus;
import com.zjgsu.sfy.identity.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findLockedById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.username = :username")
  Optional<User> findLockedByUsername(@Param("username") String username);

  @Query(
      value = "select id from identity_management_guard where id = 1 for update",
      nativeQuery = true)
  Long lockManagement();

  @Query("select count(u) from User u where u.status = :status and :role member of u.roles")
  long countByStatusAndRole(@Param("status") UserStatus status, @Param("role") Role role);

  @Query(
      "select u from User u where exists (select r from User e join e.roles r where e.id = u.id and r <> com.zjgsu.sfy.identity.common.Role.OWNER) "
          + "and (:status is null or u.status = :status) and (:role is null or :role member of u.roles) "
          + "and (lower(u.username) like :query escape '!' or lower(u.displayName) like :query escape '!' or u.phone like :query escape '!')")
  Page<User> findEmployees(
      @Param("query") String query,
      @Param("status") UserStatus status,
      @Param("role") Role role,
      Pageable pageable);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "roles")
  List<User> findByIdIn(Collection<UUID> ids);

  Optional<User> findByUsername(String username);

  Optional<User> findByPhone(String phone);

  boolean existsByUsername(String username);

  boolean existsByPhone(String phone);

  /** 按角色批量查询启用用户 id，供通知等模块按角色广播（不加载角色集合以外的字段）。 */
  @Query("select distinct u.id from User u join u.roles r where u.status = :status and r in :roles")
  List<UUID> findIdsByStatusAndRoles(
      @Param("status") UserStatus status, @Param("roles") Collection<Role> roles);
}
