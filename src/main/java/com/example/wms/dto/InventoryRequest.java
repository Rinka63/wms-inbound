package com.example.wms.dto;

import lombok.Data;

/**
 * 库存查询请求参数
 */
@Data
public class InventoryRequest {

    /**
     * 仓库ID（可选，null表示全部仓库）
     */
    private Long warehouseId;

    /**
     * SKU编码（精确匹配，最长64字符）
     */
    private String skuCode;

    /**
     * 页码（从1开始，默认1）
     */
    private Integer page = 1;

    /**
     * 每页条数（仅允许10/20/50，默认10）
     */
    private Integer size = 10;
}
