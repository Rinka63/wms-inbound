package com.example.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateReceiptOrderRequest {

    @NotNull(message = "缺少来源入库单")
    private Long inboundOrderId;

    @NotNull(message = "缺少仓库")
    private Long warehouseId;

    @NotNull(message = "缺少收货人")
    private Long receiverId;

    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

    @NotEmpty(message = "缺少收货明细")
    @Valid
    private List<Item> items;


    @Data
    public static class Item {

        @NotNull(message = "缺少入库明细ID")
        private Long inboundOrderItemId;

        @NotNull(message = "缺少SKU")
        private Long skuId;

        /**
         * 可以为 0；前端以字符串传入，Jackson 直接转 BigDecimal，不丢精度
         */
        @NotNull(message = "缺少收货数量")
        @DecimalMin(value = "0", message = "收货数量不能为负数")
        @Digits(integer = 15, fraction = 3, message = "收货数量最多 15 位整数、3 位小数")
        private BigDecimal receivedQty;
    }
}