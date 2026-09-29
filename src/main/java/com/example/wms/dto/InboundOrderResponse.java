package com.example.wms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InboundOrderResponse {

    private Long id;

    private String inboundOrderNo;

    private Long warehouseId;

    private String warehouseName;

    private Integer inboundType;

    private Integer status;

    private BigDecimal planQty;

    private BigDecimal receivedQty;

    private BigDecimal putawayQty;

    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedTime;

    private String remark;
}