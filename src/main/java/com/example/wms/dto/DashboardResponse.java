package com.example.wms.dto;

import lombok.Data;


@Data
public class DashboardResponse {

    private Long userId;
    private String userName;

    private Long warehouseId;
    private String warehouseNo;
    private String warehouseName;

    /**
     * dashboard统计数据
     */
    private Long pendingReceiptCount;
    private Long partialReceiptCount;
    private Long pendingPutawayCount;
    private Long inventorySkuCount;

    /**
     * 入库任务
     */
    private Long totalTaskCount;
    private Long completedTaskCount;

    private Integer completionRate;

}
