package com.zjgsu.sfy.owner.support;

import com.zjgsu.sfy.identity.entity.User;
import com.zjgsu.sfy.identity.support.IdentityIntegrationTest;
import com.zjgsu.sfy.owner.entity.Owner;
import com.zjgsu.sfy.owner.entity.Pet;
import com.zjgsu.sfy.owner.repository.OwnerRepository;
import com.zjgsu.sfy.owner.repository.PetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** owner 模块集成测试基类：复用 identity 的注册/登录与真实 MySQL 基座。 */
public abstract class OwnerIntegrationTest extends IdentityIntegrationTest {

  @Autowired protected OwnerRepository ownerRepository;
  @Autowired protected PetRepository petRepository;

  protected String bearer(User user) throws Exception {
    return "Bearer " + loginAccessToken(user.getUsername());
  }

  protected Owner seedOwner(User user) {
    return ownerRepository.saveAndFlush(
        Owner.create(user.getId(), "主人" + shortId(), uniquePhone(), "杭州市西湖区"));
  }

  protected Pet seedPet(Owner owner) {
    return petRepository.saveAndFlush(
        Pet.create(
            owner.getId(), "旺财", "犬", LocalDate.now().minusYears(2), new BigDecimal("5.50")));
  }

  protected String ownerBody(String name) {
    return objectMapper.writeValueAsString(
        Map.of("name", name, "phone", uniquePhone(), "address", "杭州市西湖区"));
  }

  protected String petBody(String name) {
    return objectMapper.writeValueAsString(
        Map.of(
            "name",
            name,
            "species",
            "犬",
            "birthDate",
            LocalDate.now().minusYears(2).toString(),
            "weightKg",
            5.5));
  }

  private static String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
  }
}
