package com.example.wms.service;

import com.example.wms.dto.InboundOrderRequest;
import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.mapper.InboundOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 入库单业务逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class InboundOrderServiceTest {

    @Mock
    private InboundOrderMapper inboundOrderMapper;

    @InjectMocks
    private InboundOrderService inboundOrderService;

    @Test
    void createInboundOrderShouldSucceedWithValidRequest() {
        // 准备有效请求
        InboundOrderRequest request = new InboundOrderRequest();
        request.setWarehouseId(1L);
        request.setCreatedBy(10L);
        request.setRemark("测试入库");

        InboundOrderRequest.InboundOrderItemRequest item =
                new InboundOrderRequest.InboundOrderItemRequest();
        item.setSkuId(100L);
        item.setPlanQty(50);
        request.setItems(List.of(item));

        // Mock mapper 插入入库单头行为
        when(inboundOrderMapper.insertInboundOrder(
                anyLong(), anyString(), anyInt(), anyInt(), anyLong(), nullable(String.class)))
                .thenReturn(1);

        // Mock 获取最后插入的 ID
        when(inboundOrderMapper.selectLastInsertId()).thenReturn(999L);

        // Mock 插入明细行为：返回插入的行数
        when(inboundOrderMapper.insertInboundOrderItems(anyLong(), anyList()))
                .thenReturn(1);

        // 执行创建
        InboundOrderResponse response =
                inboundOrderService.createInboundOrder(request);

        // 验证返回结果
        assertNotNull(response);
        assertEquals(999L, response.getId());
        assertNotNull(response.getInboundOrderNo());
        assertTrue(response.getInboundOrderNo().startsWith("IB"));
        assertEquals(1L, response.getWarehouseId());

        // 验证 mapper 调用
        verify(inboundOrderMapper).insertInboundOrder(
                eq(1L),              // warehouseId
                anyString(),         // inboundOrderNo
                eq(1),               // inboundType
                eq(1),               // status (默认值)
                eq(10L),             // createdBy
                eq("测试入库")        // remark
        );
        verify(inboundOrderMapper).selectLastInsertId();
        verify(inboundOrderMapper).insertInboundOrderItems(eq(999L), anyList());
    }

    @Test
    void createInboundOrderShouldThrowExceptionWhenItemsEmpty() {
        // 准备明细为空的请求
        InboundOrderRequest request = new InboundOrderRequest();
        request.setWarehouseId(1L);
        request.setCreatedBy(10L);
        request.setItems(Collections.emptyList());

        // 执行并验证异常
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inboundOrderService.createInboundOrder(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("入库单明细不能为空"));

        // 验证未调用 mapper
        verify(inboundOrderMapper, never()).insertInboundOrder(
                anyLong(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
        verify(inboundOrderMapper, never()).insertInboundOrderItems(anyLong(), anyList());
    }

    @Test
    void createInboundOrderShouldThrowExceptionWhenWarehouseIdInvalid() {
        // 准备仓库 ID 非法的请求（边界值 0）
        InboundOrderRequest request = new InboundOrderRequest();
        request.setWarehouseId(0L);
        request.setCreatedBy(10L);

        InboundOrderRequest.InboundOrderItemRequest item =
                new InboundOrderRequest.InboundOrderItemRequest();
        item.setSkuId(100L);
        item.setPlanQty(50);
        request.setItems(List.of(item));

        // 执行并验证异常
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inboundOrderService.createInboundOrder(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("仓库ID不正确"));

        // 验证未调用 mapper
        verify(inboundOrderMapper, never()).insertInboundOrder(
                anyLong(), anyString(), anyInt(), anyInt(), anyLong(), anyString());
    }
}