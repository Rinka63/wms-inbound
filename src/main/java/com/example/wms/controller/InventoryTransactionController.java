package com.example.wms.controller;

import com.example.wms.dto.InventoryTransactionRequest;
import com.example.wms.dto.InventoryTransactionResponse;
import com.example.wms.service.InventoryTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/transactions")
public class InventoryTransactionController {

    @Autowired
    private InventoryTransactionService service;

    /**
     * 获取库存流水分页列表
     * 参考 Inventory.vue 实现：后端分页，返回 {total, records}
     * 前端自行实现时间过滤（参考 ReceiptOrder.vue）
     */
    @GetMapping
    public Map<String, Object> list(InventoryTransactionRequest request) {
        // 参数校验和默认值处理
        if (request.getPage() == null || request.getPage() < 1) {
            request.setPage(1);
        }
        if (request.getSize() == null || request.getSize() < 1) {
            request.setSize(10);
        }
        if (request.getDirection() == null || request.getDirection().trim().isEmpty()) {
            request.setDirection("all");
        }

        // 限制 size 范围
        int size = Math.max(1, Math.min(100, request.getSize()));
        request.setSize(size);

        long total = service.count(request.getWarehouseId(), request.getSkuId(), request.getDirection());
        List<InventoryTransactionResponse> records = service.listPage(
            request.getWarehouseId(),
            request.getSkuId(),
            request.getDirection(),
            request.getPage(),
            request.getSize()
        );

        return Map.of(
            "total", total,
            "records", records
        );
    }
}
