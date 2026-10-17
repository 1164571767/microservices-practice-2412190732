/**
 * 共享内核：统一错误契约、HTTP 异常映射与通用技术能力（如幂等）。不含任何业务模型与业务数据。
 *
 * <p>声明为开放模块（{@link
 * org.springframework.modulith.ApplicationModule.Type#OPEN}），使各业务模块可经其子包复用这些内核类型；作为交换，
 * 本模块不依赖任何业务模块，也不拥有业务数据。
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.zjgsu.sfy.common;

import org.springframework.modulith.ApplicationModule;
