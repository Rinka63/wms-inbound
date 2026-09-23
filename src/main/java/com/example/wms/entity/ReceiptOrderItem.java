package com.example.wms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReceiptOrderItem {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 收货单ID
     */
    private Long receiptOrderId;

    /**
     * 入库单明细ID
     */
    private Long inboundOrderItemId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 本次收货数量
     */
    private BigDecimal receivedQty;

    /**
     * 合格数量
     */
    private BigDecimal qualifiedQty;

    /**
     * 破损数量
     */
    private BigDecimal damagedQty;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
