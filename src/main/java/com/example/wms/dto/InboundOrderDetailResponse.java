package com.example.wms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InboundOrderDetailResponse {

    // =========================
    // 入库单头
    // =========================

    private Long id;

    private String inboundOrderNo;

    private Long warehouseId;

    private String warehouseName;

    private Integer inboundType;

    /**
     * 对应 inbound_order.inbound_status
     */
    private Integer status;

    private BigDecimal planQty;

    private BigDecimal receivedQty;

    private BigDecimal putawayQty;

    private Long createdBy;

    private String creatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedTime;

    private String remark;

    // =========================
    // 详情
    // =========================

    private List<Item> items = new ArrayList<>();

    private List<Receipt> receipts = new ArrayList<>();

    private List<Putaway> putaways = new ArrayList<>();

    private List<TimelineEvent> timeline = new ArrayList<>();


    @Data
    public static class Item {

        private Long id;

        private Long inboundOrderId;

        private Long skuId;

        /*
         * 当前 DDL 没有 SKU 主数据表，
         * 所以 sku / skuName 暂时无法从数据库可靠查询。
         *
         * 后面如果增加 sku 表，可以补这两个字段。
         */
        private String sku;

        private String skuName;

        private BigDecimal planQty;

        private BigDecimal receivedQty;

        private BigDecimal putawayQty;
    }


    @Data
    public static class Receipt {

        private Long id;

        private String no;

        private Integer status;

        private Long receiverId;

        private String receiver;

        /**
         * 本张收货单实际收货总数量
         */
        private BigDecimal qty;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime time;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime createdTime;

        private String remark;
    }


    @Data
    public static class Putaway {

        private Long id;

        private String no;

        private Integer status;

        private Long operatorId;

        private String operator;

        /**
         * 本张上架单实际上架总数量
         */
        private BigDecimal qty;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime time;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime createdTime;

        private String remark;
    }


    @Data
    public static class TimelineEvent {

        /**
         * CREATE / RECEIPT / PUTAWAY
         */
        private String type;

        private String title;

        private String meta;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        private LocalDateTime time;

        public TimelineEvent() {
        }

        public TimelineEvent(
                String type,
                String title,
                String meta,
                LocalDateTime time
        ) {
            this.type = type;
            this.title = title;
            this.meta = meta;
            this.time = time;
        }
    }
}