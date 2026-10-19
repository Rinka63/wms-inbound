package com.example.wms.dto;

import lombok.Data;

/**
 * 库存流水查询请求参数
 */
@Data
public class InventoryTransactionRequest {
    /**
     * 仓库 ID（可选）
     */
    private Long warehouseId;

    /**
     * SKU ID（可选）
     */
    private Long skuId;

    /**
     * 变动方向：all(全部) / increase(增加) / decrease(减少)
     */
    private String direction = "all";

    /**
     * 页码，从 1 开始
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 10;
}
