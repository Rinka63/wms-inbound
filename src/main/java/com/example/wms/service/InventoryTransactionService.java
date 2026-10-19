package com.example.wms.service;

import com.example.wms.dto.InventoryTransactionResponse;
import com.example.wms.mapper.InventoryTransactionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryTransactionService {

    @Autowired
    private InventoryTransactionMapper mapper;

    /**
     * 查询库存流水总数
     */
    public long count(Long warehouseId, String skuCode, String direction) {
        return mapper.count(warehouseId, skuCode, direction);
    }

    /**
     * 查询库存流水分页列表
     */
    public List<InventoryTransactionResponse> listPage(
        Long warehouseId,
        String skuCode,
        String direction,
        int page,
        int size
    ) {
        long offset = (page - 1L) * size;
        return mapper.selectPage(warehouseId, skuCode, direction, offset, size);
    }
}
