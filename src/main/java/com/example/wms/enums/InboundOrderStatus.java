package com.example.wms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InboundOrderStatus {

    DRAFT(0, "草稿"),
    WAIT_RECEIVE(1, "待收货"),
    PART_RECEIVED(2, "部分收货"),
    RECEIVED(3, "已收货"),
    PART_PUTAWAY(4, "部分上架"),
    COMPLETED(5, "已完成"),
    CANCELED(6, "已取消");

    private final Integer code;
    private final String description;
}
