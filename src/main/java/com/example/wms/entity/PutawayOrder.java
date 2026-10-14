package com.example.wms.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PutawayOrder {

    private Long id;

    private String putawayOrderNo;

    private Long inboundOrderId;

    private Long receiptOrderId;

    private Long warehouseId;

    private Long operatorId;

    /**
     * 上架单状态，对应 putaway_order.putaway_status
     *
     * 1：上架中
     * 2：已完成
     */
    private Integer putawayStatus;

    private LocalDateTime putawayTime;

    private String remark;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
