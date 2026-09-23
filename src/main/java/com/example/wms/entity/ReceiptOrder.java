package com.example.wms.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReceiptOrder {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 收货单号
     */
    private String receiptOrderNo;

    /**
     * 入库单ID
     */
    private Long inboundOrderId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 收货人ID
     */
    private Long receiverId;

    /**
     * 收货状态
     *
     * 0：草稿
     * 1：已完成
     * 2：已取消
     */
    private Integer status;

    /**
     * 实际收货时间
     */
    private LocalDateTime receivedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 备注
     */
    private String remark;
}
