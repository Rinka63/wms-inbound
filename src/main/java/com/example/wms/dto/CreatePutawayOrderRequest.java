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

/**
 * 上架单
 */
@Data
public class CreatePutawayOrderRequest {

    @NotNull(message = "缺少来源入库单")
    private Long inboundOrderId;

    @NotNull(message = "缺少上架人")
    private Long operatorId;

    /**
     * 只在新建时写入单头；继续上架时忽略
     */
    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

    /**
     * 只包含本次数量大于 0 的行
     */
    @NotEmpty(message = "缺少上架明细")
    @Valid
    private List<Item> items;


    @Data
    public static class Item {

        @NotNull(message = "缺少入库明细ID")
        private Long inboundOrderItemId;

        @NotNull(message = "请选择目标库位")
        private Long locationId;

        /**
         * 前端以字符串传入，Jackson 直接转 BigDecimal，不丢精度
         */
        @NotNull(message = "缺少上架数量")
        @DecimalMin(value = "0", inclusive = false, message = "上架数量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "上架数量最多 15 位整数、3 位小数")
        private BigDecimal putawayQty;
    }
}
