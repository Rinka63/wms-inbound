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

    /**
     * 仓库状态：0停用，1启用
     */
    private Integer warehouseStatus;

    /**
     * 创建时间
     */
    private String gmtCreate;

    /**
     * 更新时间
     */
    private String gmtModified;

}