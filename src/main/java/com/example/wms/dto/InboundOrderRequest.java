package com.example.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class InboundOrderRequest {

    private Long warehouseId;
    private Long createdBy;
    private Integer status;
    private String remark;
    private List<InboundOrderItemRequest> items;

    @Data
    public static class InboundOrderItemRequest {
        private Long skuId;
        private Integer planQty;
    }
}
