package com.example.wms.service;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.InventoryRequest;
import com.example.wms.entity.LocationStockItem;
import com.example.wms.mapper.InventoryMapper;
import com.example.wms.vo.InventoryVO;
import com.example.wms.vo.PageResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void queryPage_shouldReturnPageResult_whenValidRequest() {
        // 准备测试数据
        InventoryRequest request = new InventoryRequest();
        request.setWarehouseId(1L);
        request.setSkuCode("SKU001");
        request.setPage(1);
        request.setSize(10);

        // Mock mapper 返回数据
        when(inventoryMapper.countGroups(1L, "SKU001")).thenReturn(1L);

        InventoryVO inventoryVO = new InventoryVO();
        inventoryVO.setWarehouseId(1L);
        inventoryVO.setSkuId(100L);
        inventoryVO.setSkuCode("SKU001");
        inventoryVO.setSkuName("商品001");
        inventoryVO.setTotalQuantity(new BigDecimal("50"));

        when(inventoryMapper.selectGroupPage(1L, "SKU001", 0, 10))
                .thenReturn(List.of(inventoryVO));

        LocationStockItem locationItem = new LocationStockItem();
        locationItem.setWarehouseId(1L);
        locationItem.setSkuId(100L);
        locationItem.setLocationId(10L);
        locationItem.setLocationCode("A01-01");
        locationItem.setQuantity(new BigDecimal("50"));

        when(inventoryMapper.selectLocations(anyList()))
                .thenReturn(List.of(locationItem));

        // 执行测试
        PageResultVO<InventoryVO> result = inventoryService.queryPage(request);

        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());

        InventoryVO resultVO = result.getRecords().get(0);
        assertEquals("SKU001", resultVO.getSkuCode());
        assertEquals(1, resultVO.getLocations().size());
        assertEquals("A01-01", resultVO.getLocations().get(0).getLocationCode());

        // 验证 mapper 调用
        verify(inventoryMapper).countGroups(1L, "SKU001");
        verify(inventoryMapper).selectGroupPage(1L, "SKU001", 0, 10);
        verify(inventoryMapper).selectLocations(anyList());
    }

    @Test
    void queryPage_shouldThrowException_whenInvalidPageSize() {
        // 准备非法参数
        InventoryRequest request = new InventoryRequest();
        request.setPage(1);
        request.setSize(100); // 不在 10/20/50 范围内

        // 执行并验证异常
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> inventoryService.queryPage(request)
        );

        assertEquals("每页条数仅支持 10/20/50", exception.getMessage());

        // 验证没有调用 mapper
        verify(inventoryMapper, never()).countGroups(anyLong(), anyString());
    }
}
