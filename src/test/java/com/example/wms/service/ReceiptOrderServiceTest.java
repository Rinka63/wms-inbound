package com.example.wms.service;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.CreateReceiptOrderRequest;
import com.example.wms.dto.CreateReceiptOrderResponse;
import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.entity.ReceiptOrder;
import com.example.wms.entity.ReceiptOrderItem;
import com.example.wms.enums.InboundOrderStatus;
import com.example.wms.enums.ReceiptOrderStatus;
import com.example.wms.mapper.InboundOrderMapper;
import com.example.wms.mapper.ReceiptOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 收货单业务逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReceiptOrderServiceTest {

    @Mock
    private ReceiptOrderMapper receiptOrderMapper;

    @Mock
    private InboundOrderMapper inboundOrderMapper;

    @InjectMocks
    private ReceiptOrderService receiptOrderService;

    @Test
    void createReceiptOrderShouldCompleteInboundWhenFullyReceived() {
        // 准备请求：计划 100，收货 100（全量收货）
        CreateReceiptOrderRequest request = new CreateReceiptOrderRequest();
        request.setInboundOrderId(1L);
        request.setWarehouseId(10L);
        request.setReceiverId(5L);
        request.setRemark("全量收货");

        CreateReceiptOrderRequest.Item item = new CreateReceiptOrderRequest.Item();
        item.setInboundOrderItemId(100L);
        item.setSkuId(200L);
        item.setReceivedQty(new BigDecimal("100"));
        request.setItems(List.of(item));

        // Mock 入库单状态为待收货
        when(inboundOrderMapper.selectInboundStatusForUpdate(1L))
                .thenReturn(InboundOrderStatus.WAIT_RECEIVE.getCode());

        // Mock 无已有收货单
        when(inboundOrderMapper.selectReceiptRecords(1L))
                .thenReturn(Collections.emptyList());

        // Mock 计划数量合计为 100
        InboundOrderDetailResponse detail = new InboundOrderDetailResponse();
        detail.setPlanQty(new BigDecimal("100"));
        when(inboundOrderMapper.selectInboundOrderDetail(1L))
                .thenReturn(detail);

        // Mock insertReceiptOrder 行为：模拟 MyBatis 设置主键
        doAnswer(invocation -> {
            ReceiptOrder order = invocation.getArgument(0);
            order.setId(888L);
            return null;
        }).when(receiptOrderMapper).insertReceiptOrder(any(ReceiptOrder.class));

        // 执行收货
        CreateReceiptOrderResponse response =
                receiptOrderService.createReceiptOrder(request);

        // 验证返回结果
        assertNotNull(response);
        assertNotNull(response.getReceiptOrderNo());
        assertTrue(response.getReceiptOrderNo().startsWith("RC"));

        // 验证收货单头插入（状态应为已完成）
        ArgumentCaptor<ReceiptOrder> orderCaptor =
                ArgumentCaptor.forClass(ReceiptOrder.class);
        verify(receiptOrderMapper).insertReceiptOrder(orderCaptor.capture());
        ReceiptOrder capturedOrder = orderCaptor.getValue();
        assertEquals(ReceiptOrderStatus.COMPLETED.getCode(),
                capturedOrder.getReceiptStatus());
        assertNotNull(capturedOrder.getReceivedTime());

        // 验证收货明细插入
        verify(receiptOrderMapper).insertReceiptOrderItems(anyList());

        // 验证累加收货数量（传入模拟的主键 888L）
        verify(receiptOrderMapper).addInboundItemReceivedQty(888L);

        // 验证入库单状态更新为已收货
        verify(inboundOrderMapper).updateInboundStatus(
                1L, InboundOrderStatus.RECEIVED.getCode());
    }

    @Test
    void createReceiptOrderShouldKeepWaitingWhenPartiallyReceived() {
        // 准备请求：计划 100，收货 50（部分收货）
        CreateReceiptOrderRequest request = new CreateReceiptOrderRequest();
        request.setInboundOrderId(2L);
        request.setWarehouseId(10L);
        request.setReceiverId(5L);

        CreateReceiptOrderRequest.Item item = new CreateReceiptOrderRequest.Item();
        item.setInboundOrderItemId(101L);
        item.setSkuId(201L);
        item.setReceivedQty(new BigDecimal("50"));
        request.setItems(List.of(item));

        // Mock 入库单状态为待收货
        when(inboundOrderMapper.selectInboundStatusForUpdate(2L))
                .thenReturn(InboundOrderStatus.WAIT_RECEIVE.getCode());

        when(inboundOrderMapper.selectReceiptRecords(2L))
                .thenReturn(Collections.emptyList());

        // Mock 计划数量合计为 100
        InboundOrderDetailResponse detail = new InboundOrderDetailResponse();
        detail.setPlanQty(new BigDecimal("100"));
        when(inboundOrderMapper.selectInboundOrderDetail(2L))
                .thenReturn(detail);

        // 执行收货
        CreateReceiptOrderResponse response =
                receiptOrderService.createReceiptOrder(request);

        // 验证返回结果
        assertNotNull(response);

        // 验证收货单头状态为未完成
        ArgumentCaptor<ReceiptOrder> orderCaptor =
                ArgumentCaptor.forClass(ReceiptOrder.class);
        verify(receiptOrderMapper).insertReceiptOrder(orderCaptor.capture());
        ReceiptOrder capturedOrder = orderCaptor.getValue();
        assertEquals(ReceiptOrderStatus.UNFINISHED.getCode(),
                capturedOrder.getReceiptStatus());
        assertNull(capturedOrder.getReceivedTime());

        // 验证明细已插入
        verify(receiptOrderMapper).insertReceiptOrderItems(anyList());

        // 验证入库单状态未更新（保持待收货）
        verify(inboundOrderMapper, never()).updateInboundStatus(anyLong(), anyInt());
    }

    @Test
    void createReceiptOrderShouldThrowExceptionWhenInboundNotFound() {
        // 准备请求
        CreateReceiptOrderRequest request = new CreateReceiptOrderRequest();
        request.setInboundOrderId(999L);
        request.setWarehouseId(10L);
        request.setReceiverId(5L);

        CreateReceiptOrderRequest.Item item = new CreateReceiptOrderRequest.Item();
        item.setSkuId(200L);
        item.setReceivedQty(new BigDecimal("10"));
        request.setItems(List.of(item));

        // Mock 入库单不存在
        when(inboundOrderMapper.selectInboundStatusForUpdate(999L))
                .thenReturn(null);

        // 执行并验证异常
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> receiptOrderService.createReceiptOrder(request)
        );

        assertEquals("入库单不存在", exception.getMessage());

        // 验证后续逻辑未执行
        verify(receiptOrderMapper, never()).insertReceiptOrder(any());
        verify(receiptOrderMapper, never()).insertReceiptOrderItems(anyList());
        verify(inboundOrderMapper, never()).updateInboundStatus(anyLong(), anyInt());
    }
}