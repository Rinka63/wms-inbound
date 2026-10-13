package com.example.wms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReceiptOrderStatus {

    UNFINISHED(0, "未完成"),
    COMPLETED(1, "已完成");

    private final Integer code;
    private final String description;
}