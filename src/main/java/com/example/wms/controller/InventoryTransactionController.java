package com.example.wms.controller;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.InventoryTransactionRequest;
import com.example.wms.dto.InventoryTransactionResponse;
import com.example.wms.service.InventoryTransactionService;
import com.example.wms.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/transactions")
@RequiredArgsConstructor
public class InventoryTransactionController {

    private final InventoryTransactionService service;

    /**
     * 获取库存流水分页列表
     * 参考 Inventory.vue 实现：后端分页，返回 PageResultVO
     * 前端自行实现时间过滤（参考 ReceiptOrder.vue）
     */
    @GetMapping
    public PageResultVO<InventoryTransactionResponse> list(InventoryTransactionRequest request) {
        // 参数校验和默认值处理
        if (request.getPage() == null || request.getPage() < 1) {
            request.setPage(1);
        }
        if (request.getSize() == null || !Arrays.asList(10, 20, 50).contains(request.getSize())) {
            request.setSize(10);
        }
        if (request.getDirection() == null || request.getDirection().trim().isEmpty()) {
            request.setDirection("all");
        }

        // 规范化 skuCode
        String skuCode = StringUtils.trimToNull(request.getSkuCode());
        if (skuCode != null && skuCode.length() > 64) {
            throw new BusinessException("SKU编码不能超过64字符");
        }

        long total = service.count(request.getWarehouseId(), skuCode, request.getDirection());
        List<InventoryTransactionResponse> records = service.listPage(
            request.getWarehouseId(),
            skuCode,
            request.getDirection(),
            request.getPage(),
            request.getSize()
        );

        return new PageResultVO<>(total, records);
    }
}
