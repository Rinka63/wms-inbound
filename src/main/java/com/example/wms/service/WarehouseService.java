package com.example.wms.service;

import com.example.wms.event.WarehouseQueryEvent;
import com.example.wms.kafka.WarehouseEventProducer;
import com.example.wms.vo.WarehouseLocationVO;
import com.example.wms.vo.WarehouseVO;
import com.example.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class WarehouseService {


    private final WarehouseMapper warehouseMapper;
    private final WarehouseEventProducer eventProducer;


    public List<WarehouseVO> listAvailable(){
        long startTime = System.currentTimeMillis();

        // 查询仓库列表
        List<WarehouseVO> warehouses = warehouseMapper.selectWarehouseList();

        long duration = System.currentTimeMillis() - startTime;

        // 发送 Kafka 事件
        WarehouseQueryEvent event = WarehouseQueryEvent.builder()
                .queryTime(LocalDateTime.now())
                .warehouseCount(warehouses.size())
                .duration(duration)
                .source("API")
                .build();

        eventProducer.sendQueryEvent(event);

        return warehouses;

    }

    public List<WarehouseLocationVO> listEnabledLocations(Long warehouseId){
        return warehouseMapper.selectEnabledLocations(warehouseId);
    }

    /**
     * 查询所有启用库位
     */
    public List<WarehouseLocationVO> listAllEnabledLocations() {
        return warehouseMapper.selectEnabledLocations(null);
    }

}