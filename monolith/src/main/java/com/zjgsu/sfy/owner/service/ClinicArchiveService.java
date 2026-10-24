package com.zjgsu.sfy.owner.service;

import com.zjgsu.sfy.common.error.*;
import com.zjgsu.sfy.common.query.LikePatterns;
import com.zjgsu.sfy.identity.*;
import com.zjgsu.sfy.owner.dto.resp.*;
import com.zjgsu.sfy.owner.entity.*;
import com.zjgsu.sfy.owner.repository.*;
import com.zjgsu.sfy.owner.service.command.*;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 员工档案用例。写事务使用 READ_COMMITTED，先锁主人再锁宠物，与预约保持同序。 Hibernate refresh 在已持锁时仍可能发出普通 SELECT；不能让 MySQL
 * 旧快照覆盖锁定查询得到的最新归档状态。
 */
@Service
public class ClinicArchiveService {
  private final OwnerRepository owners;
  private final PetRepository pets;
  private final ArchiveOperationRepository operations;
  private final IdentityApi identity;
  private final ObjectMapper json;
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ClinicArchiveService.class);
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public ClinicArchiveService(
      OwnerRepository owners,
      PetRepository pets,
      ArchiveOperationRepository operations,
      IdentityApi identity,
      ObjectMapper json,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.owners = owners;
    this.pets = pets;
    this.operations = operations;
    this.identity = identity;
    this.json = json;
    this.metrics = metrics;
  }

  public static void requireStaff(CurrentUser user) {
    if (!user.isClinicStaff()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }

  public static void requireVersion(long actual, long expected) {
    if (actual != expected)
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "档案已被其他操作更新，请重新读取后核对");
  }

  @Transactional(readOnly = true)
  public Page<Owner> clients(CurrentUser user, String query, Boolean archived, Pageable page) {
    requireStaff(user);
    return owners.search(LikePatterns.contains(query), archived, page);
  }

  @Transactional(readOnly = true)
  public Page<PetDirectoryResponse> pets(
      CurrentUser user, String query, Boolean archived, UUID ownerId, Pageable page) {
    requireStaff(user);
    Page<Pet> result = pets.search(LikePatterns.contains(query), archived, ownerId, page);
    var ownerMap =
        owners
            .findAllById(result.getContent().stream().map(Pet::getOwnerId).distinct().toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    Owner::getId, java.util.function.Function.identity()));
    return result.map(
        p -> {
          Owner o = ownerMap.get(p.getOwnerId());
          return new PetDirectoryResponse(
              PetResponse.from(p), o.getName(), o.getPhone(), o.isArchived());
        });
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Owner createClient(CurrentUser user, UpsertOwnerCommand c) {
    requireStaff(user);
    Owner owner = owners.saveAndFlush(Owner.createUnbound(c.name(), c.phone(), c.address()));
    audit(user, owner.getId(), "CREATE_CLIENT", null, null, OwnerResponse.from(owner));
    return owner;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Owner updateClient(CurrentUser user, UUID id, long version, UpsertOwnerCommand c) {
    requireStaff(user);
    Owner owner = lockOwner(id);
    requireVersion(owner.getVersion(), version);
    Object before = OwnerResponse.from(owner);
    owner.updateDetails(c.name(), c.phone(), c.address());
    owners.flush();
    audit(user, id, "UPDATE_CLIENT", null, before, OwnerResponse.from(owner));
    return owner;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Owner archiveClient(CurrentUser user, UUID id, long version, String reason) {
    requireStaff(user);
    Owner o = lockOwner(id);
    requireVersion(o.getVersion(), version);
    Object before = OwnerResponse.from(o);
    o.archive(user.userId(), reason);
    owners.flush();
    audit(user, id, "ARCHIVE_CLIENT", reason, before, OwnerResponse.from(o));
    return o;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Pet createPet(CurrentUser user, UUID ownerId, UpsertPetCommand c, boolean estimated) {
    requireStaff(user);
    lockOwner(ownerId).requireActive();
    Pet p = Pet.create(ownerId, c.name(), c.species(), c.birthDate(), c.weightKg());
    p.markBirthDateEstimated(estimated);
    pets.saveAndFlush(p);
    audit(user, p.getId(), "CREATE_PET", null, null, PetResponse.from(p));
    return p;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Pet updatePet(
      CurrentUser user, UUID id, long version, UpsertPetCommand c, boolean estimated) {
    requireStaff(user);
    Pet p = lockActivePet(id);
    requireVersion(p.getVersion(), version);
    Object before = PetResponse.from(p);
    p.updateDetails(c.name(), c.species(), c.birthDate(), c.weightKg());
    p.markBirthDateEstimated(estimated);
    pets.flush();
    audit(user, id, "UPDATE_PET", null, before, PetResponse.from(p));
    return p;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Pet archivePet(CurrentUser user, UUID id, long version, String reason) {
    requireStaff(user);
    Pet p = lockActivePet(id);
    requireVersion(p.getVersion(), version);
    Object before = PetResponse.from(p);
    p.archive(user.userId(), reason);
    pets.flush();
    audit(user, id, "ARCHIVE_PET", reason, before, PetResponse.from(p));
    return p;
  }

  @Transactional(readOnly = true)
  public BindingCandidateResponse candidate(CurrentUser user, String username) {
    requireAdmin(user);
    return BindingCandidateResponse.from(bindingUser(username));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Owner bind(CurrentUser user, UUID id, long version, String username, UUID expected) {
    requireAdmin(user);
    Owner o = lockOwner(id);
    requireVersion(o.getVersion(), version);
    Object before = OwnerResponse.from(o);
    UserProfile account = bindingUser(username);
    if (!account.id().equals(expected))
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION);
    if (owners.findByUserId(account.id()).isPresent())
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该账号已有主人档案，不能重复绑定");
    o.bind(account.id());
    try {
      owners.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "账号已被其他档案绑定");
    }
    audit(user, id, "BIND_ACCOUNT", "管理员已核对身份", before, OwnerResponse.from(o));
    return o;
  }

  private UserProfile bindingUser(String username) {
    UserProfile u =
        identity
            .findUserByUsername(username)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未找到该登录账号"));
    if (!u.isActiveOwner())
      throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只能绑定有效的 OWNER 账号");
    return u;
  }

  private static void requireAdmin(CurrentUser user) {
    if (!user.isAdmin()) throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
  }

  @Transactional(readOnly = true)
  public Page<ArchiveOperation> operations(CurrentUser user, UUID id, boolean pet, Pageable page) {
    requireStaff(user);
    if (pet) {
      pets.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    } else {
      owners.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    return operations.findByTargetId(id, page);
  }

  Owner lockOwner(UUID id) {
    Owner owner =
        owners.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    entityManager.refresh(owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    return owner;
  }

  Pet lockActivePet(UUID id) {
    UUID ownerId =
        pets.findOwnerId(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    lockOwner(ownerId).requireActive();
    Pet p =
        pets.lockById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    entityManager.refresh(p, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    p.requireActive();
    return p;
  }

  private void audit(
      CurrentUser user, UUID target, String action, String reason, Object before, Object after) {
    operations.save(
        ArchiveOperation.record(
            target,
            action,
            user.userId(),
            user.username(),
            reason,
            before == null ? null : json.writeValueAsString(before),
            json.writeValueAsString(after)));
    // Publish telemetry only after commit; rollback must not count as a successful operation.
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                metrics.counter("petcare.owner.operations", "action", action).increment();
                log.info(
                    "owner.operation action={} targetId={} actorId={}",
                    action,
                    target,
                    user.userId());
              }
            });
  }
}
