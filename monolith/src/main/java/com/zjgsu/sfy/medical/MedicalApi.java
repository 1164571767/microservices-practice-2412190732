package com.zjgsu.sfy.medical;

import java.util.List;
import java.util.UUID;

/** medical 模块对外 API。其他模块只能经此只读获取病历索引，禁止直接访问其表。 */
public interface MedicalApi {

  /** 返回某只宠物的病历索引（按就诊时间倒序）；无记录返回空列表。 */
  List<MedicalRecordSummary> findRecordsByPet(UUID petId);
}
