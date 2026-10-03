package com.zjgsu.sfy;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  static final ApplicationModules modules = ApplicationModules.of(MonolithApplication.class);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
