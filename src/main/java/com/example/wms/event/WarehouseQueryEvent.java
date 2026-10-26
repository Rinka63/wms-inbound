package com.example.wms.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseQueryEvent {

    /**
     * 查询时间
     */
    private LocalDateTime queryTime;

    /**
     * 返回的仓库数量
     */
    private Integer warehouseCount;

    /**
     * 查询耗时（毫秒）
     */
    private Long duration;

    /**
     * 请求来源
     */
    private String source;
}
