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

}
