package com.example.wms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReceiptOrderResponse {

    private Long id;

    private String receiptOrderNo;

    private Long inboundOrderId;

    private String inboundOrderNo;

    private Long warehouseId;

    private String warehouseName;

    /**
     * 对应 receipt_order.receipt_status
     */
    private Integer status;

    /**
     * Service 中计算：lines.size()
     */
    private Integer skuCount;

    /**
     * Service 中计算：lines 的 receivedQty 求和
     */
    private BigDecimal totalQty;

    private String receiverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime receivedTime;

    private String remark;

    private List<Line> lines = new ArrayList<>();


    @Data
    public static class Line {

        /**
         * 分组用，挂到对应单头
         */
        private Long receiptOrderId;

        private Long inboundOrderItemId;

        private Long skuId;

        private BigDecimal planQty;

        private BigDecimal receivedQty;
    }
}