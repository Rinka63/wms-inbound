package com.example.wms.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PutawayOrderResponse {

    private Long id;

    private String putawayOrderNo;

    private Long receiptOrderId;

    private String receiptOrderNo;

    private Long inboundOrderId;

    private String inboundOrderNo;

    private Long warehouseId;

    private String warehouseName;

    /**
     * 对应 putaway_order.putaway_status
     */
    private Integer status;

    /**
     * 同一 SKU 可多次登记， Service 中计算 lines 去重后的 skuId 个数
     */
    private Integer skuCount;

    /**
     * Service 中计算 lines 的 putawayQty 求和
     */
    private BigDecimal totalQty;

    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime putawayTime;

    private String remark;

    private List<Line> lines = new ArrayList<>();


    @Data
    public static class Line {

        private Long id;

        /**
         * 分组用，挂到对应单头
         */
        private Long putawayOrderId;

        private Long inboundOrderItemId;

        private Long skuId;

        private Long locationId;

        private String locationCode;

        private BigDecimal putawayQty;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime createdTime;
    }
}

