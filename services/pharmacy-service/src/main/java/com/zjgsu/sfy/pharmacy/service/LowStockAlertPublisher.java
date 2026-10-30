package com.zjgsu.sfy.pharmacy.service;

import com.zjgsu.sfy.pharmacy.LowStockDetected;
import com.zjgsu.sfy.pharmacy.entity.Drug;
import com.zjgsu.sfy.pharmacy.entity.Inventory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 刷新库存的低库存告警状态并在「首次跌破阈值」时发布 {@link LowStockDetected}。
 *
 * <p>同一段低库存期间只发布一次；补货回到阈值以上后自动复位，下一次跌破会再次发布。
 */
@Component
public class LowStockAlertPublisher {

  private final ApplicationEventPublisher events;

  public LowStockAlertPublisher(ApplicationEventPublisher events) {
    this.events = events;
  }

  public boolean refreshAndPublish(Drug drug, Inventory inventory) {
    if (!inventory.refreshLowStockAlert(drug.getLowStockThreshold())) {
      return false;
    }
    events.publishEvent(
        new LowStockDetected(
            drug.getId(),
            drug.getName(),
            drug.getSpecification(),
            inventory.getQuantity(),
            drug.getLowStockThreshold(),
            inventory.getLowStockNotifiedAt()));
    return true;
  }
}
