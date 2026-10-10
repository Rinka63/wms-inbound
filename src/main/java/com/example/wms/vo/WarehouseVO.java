package com.example.wms.vo;

import lombok.Data;

@Data
public class WarehouseVO {

    /**
     * 仓库ID
     */
    private Long id;

    /**
     * 仓库编号
     */
    private String warehouseNo;

    /**
     * 仓库名称
     */
    private String warehouseName;

}