package com.example.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存分组查询
 */
@Data
public class InventoryVO {
    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * SKU名称
     */
    private String skuName;

    /**
     * 该仓库该SKU的库存合计
     * 注意：必须序列化为字符串，防止前端JavaScript浮点精度问题
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalQuantity;

    /**
     * 库位明细列表
     */
    private List<LocationStockVO> locations;
}
