package com.example.wms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PutawayOrderStatus {

    PUTTING_AWAY(1, "上架中"),
    COMPLETED(2, "已完成");

    private final Integer code;
    private final String description;
}
