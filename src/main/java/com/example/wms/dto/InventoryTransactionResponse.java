package com.example.wms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水响应数据
 */
@Data
public class InventoryTransactionResponse {
    /**
     * 流水 ID
     */
    private Long id;

    /**
     * 库存 ID
     */
    private Long inventoryId;

    /**
     * 仓库 ID
     */
    private Long warehouseId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 库位 ID
     */
    private Long locationId;

    /**
     * 变动数量（正数为增加，负数为减少）
     */
    private BigDecimal changeQty;

    /**
     * 变动前库存
     */
    private BigDecimal beforeQty;

    /**
     * 变动后库存
     */
    private BigDecimal afterQty;

    /**
     * 变动时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gmtCreate;
}
