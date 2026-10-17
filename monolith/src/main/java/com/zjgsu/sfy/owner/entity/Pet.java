package com.zjgsu.sfy.owner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 宠物档案；归属某个主人。物种/品种、年龄（以生日表达）与体重在此校验。 */
@Entity
@Table(name = "pet")
public class Pet {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 50)
  private String species;

  @Column(name = "birth_date", nullable = false)
  private LocalDate birthDate;

  @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
  private BigDecimal weightKg;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Pet() {}

  public static Pet create(
      UUID ownerId, String name, String species, LocalDate birthDate, BigDecimal weightKg) {
    Pet pet = new Pet();
    pet.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    pet.applyDetails(name, species, birthDate, weightKg);
    return pet;
  }

  public void updateDetails(String name, String species, LocalDate birthDate, BigDecimal weightKg) {
    applyDetails(name, species, birthDate, weightKg);
  }

  private void applyDetails(String name, String species, LocalDate birthDate, BigDecimal weightKg) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (species == null || species.isBlank()) {
      throw new IllegalArgumentException("species must not be blank");
    }
    if (birthDate == null) {
      throw new IllegalArgumentException("birth date must not be null");
    }
    if (birthDate.isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("birth date must not be in the future");
    }
    if (weightKg == null || weightKg.signum() <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    this.name = name.trim();
    this.species = species.trim();
    this.birthDate = birthDate;
    this.weightKg = weightKg.setScale(2, RoundingMode.HALF_UP);
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public boolean isOwnedBy(UUID candidateOwnerId) {
    return ownerId.equals(candidateOwnerId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public String getSpecies() {
    return species;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public BigDecimal getWeightKg() {
    return weightKg;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
