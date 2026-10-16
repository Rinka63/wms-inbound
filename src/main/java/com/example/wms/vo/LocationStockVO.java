package com.example.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 库位库存
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationStockVO {
    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 库位库存数量
     * 注意：必须序列化为字符串
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal quantity;
}
