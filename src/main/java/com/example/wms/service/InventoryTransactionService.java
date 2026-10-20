package com.example.wms.service;

import com.example.wms.annotation.CachePerformanceLog;
import com.example.wms.common.CachePerformanceAspect;
import com.example.wms.dto.InventoryTransactionResponse;
import com.example.wms.mapper.InventoryTransactionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InventoryTransactionService {

    @Autowired
    private InventoryTransactionMapper mapper;

    /**
     * 查询库存流水总数，调用该方法自动缓存结果
     */
    @CachePerformanceLog("库存流水COUNT")
    @Cacheable(
            cacheNames = "inventoryTransactions",
            keyGenerator = "transactionKeyGenerator"
    )
    public long count(Long warehouseId, String skuCode, String direction) {
        // 标记方法真正执行了（未命中缓存才会到这里）
        CachePerformanceAspect.markMethodExecuted();

        log.info("[DEBUG] count() 方法体被执行 - 这说明缓存未命中");

        long start = System.nanoTime();

        try {
            return mapper.count(warehouseId, skuCode, direction);
        } finally {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

            log.info("[InventoryTransaction] MySQL COUNT 耗时: {} ms",
                    elapsedMs);
        }
    }

    /**
     * 查询库存流水分页列表，调用方法自动缓存结果
     */
    @CachePerformanceLog("库存流水SELECT")
    @Cacheable(
            cacheNames = "inventoryTransactions",
            keyGenerator = "transactionKeyGenerator"
    )
    public List<InventoryTransactionResponse> listPage(
        Long warehouseId,
        String skuCode,
        String direction,
        int page,
        int size
    ) {
        // 标记方法真正执行了（未命中缓存才会到这里）
        CachePerformanceAspect.markMethodExecuted();

        log.info("[DEBUG] listPage() 方法体被执行 - 这说明缓存未命中, page={}", page);

        long offset = (page - 1L) * size;
        long start = System.nanoTime();

        try {
            return mapper.selectPage(
                    warehouseId, skuCode, direction, offset, size);
        } finally {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

            log.info("[InventoryTransaction] MySQL SELECT 耗时: {} ms, page={}, size={}",
                    elapsedMs, page, size);
        }
    }

    /**
     * 上架单修改后写入流水记录，调用方法清除缓存
     */
    @CacheEvict(
            cacheNames = "inventoryTransactions",
            allEntries = true  // 清除所有缓存条目
    )
    public void evictCache(Long warehouseId, String skuCode) {
        log.info("清除库存流水缓存 - warehouse: {}, sku: {}", warehouseId, skuCode);
    }
}
