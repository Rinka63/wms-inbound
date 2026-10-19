package com.example.wms.vo;

import lombok.Data;

@Data
public class WarehouseLocationVO {

    /**
     * 库位ID
     */
    private Long id;

    /**
     * 所属仓库ID
     */
    private Long warehouseId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 库位状态：0停用，1启用
     */
    private Integer locationStatus;

    /**
     * 创建时间
     */
    private String gmtCreate;

    /**
     * 更新时间
     */
    private String gmtModified;

}
