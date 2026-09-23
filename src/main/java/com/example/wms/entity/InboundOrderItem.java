package com.example.wms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InboundOrderItem {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 入库单ID
     */
    private Long inboundOrderId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 计划入库数量
     */
    private BigDecimal planQty;

    /**
     * 累计收货数量
     */
    private BigDecimal receivedQty;

    /**
     * 累计上架数量
     */
    private BigDecimal putawayQty;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
