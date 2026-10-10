package com.example.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateInboundOrderRequest {

    @NotNull(message = "请选择仓库")
    private Long warehouseId;

    @NotNull(message = "缺少创建人")
    private Long createdBy;

    @NotNull(message = "缺少入库单状态")
    private Integer status;

    @Size(max = 500, message = "备注最多 500 字")
    private String remark;

    @NotEmpty(message = "请至少添加一条 SKU 明细")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "SKU ID 不能为空")
        private Long skuId;

        @NotNull(message = "计划入库数量不能为空")
        private BigDecimal planQty;
    }
}
