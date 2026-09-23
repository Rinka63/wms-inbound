package com.example.wms.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InboundOrder {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 入库单号
     */
    private String inboundOrderNo;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 入库类型
     *
     * 1：采购入库
     * 2：退货入库
     * 3：调拨入库
     * 4：其他入库
     */
    private Integer inboundType;

    /**
     * 入库状态
     *
     * 0：草稿
     * 1：待收货
     * 2：部分收货
     * 3：已收货
     * 4：部分上架
     * 5：已完成
     * 6：已取消
     */
    private Integer status;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 备注
     */
    private String remark;
}
