package com.example.wms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PutawayOrderItem {

    private Long id;

    private Long putawayOrderId;

    private Long inboundOrderItemId;

    private Long receiptOrderItemId;

    private Long skuId;

    private Long locationId;

    private BigDecimal putawayQty;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
