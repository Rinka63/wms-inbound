package com.example.wms.entity;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LocationStockItem {
    /**
     * 仓库ID（用于分组匹配）
     */
    private Long warehouseId;

    /**
     * SKU ID（用于分组匹配）
     */
    private Long skuId;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 库位库存数量
     */
    private BigDecimal quantity;
}
