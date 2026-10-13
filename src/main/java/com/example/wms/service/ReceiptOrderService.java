package com.example.wms.service;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.CreateReceiptOrderRequest;
import com.example.wms.dto.CreateReceiptOrderResponse;
import com.example.wms.dto.ReceiptOrderResponse;
import com.example.wms.entity.ReceiptOrder;
import com.example.wms.entity.ReceiptOrderItem;
import com.example.wms.enums.InboundOrderStatus;
import com.example.wms.enums.ReceiptOrderStatus;
import com.example.wms.mapper.InboundOrderMapper;
import com.example.wms.mapper.ReceiptOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptOrderService {

    private final ReceiptOrderMapper receiptOrderMapper;

    private final InboundOrderMapper inboundOrderMapper;

    private static final DateTimeFormatter NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");


    /**
     * 收货单列表：单头、明细各查一次，按 receiptOrderId 挂载
     * readOnly 事务保证两次查询读到同一快照
     */
    @Transactional(readOnly = true)
    public List<ReceiptOrderResponse> getReceiptOrderList() {

        List<ReceiptOrderResponse> orders =
                receiptOrderMapper.selectReceiptOrderList();

        Map<Long, List<ReceiptOrderResponse.Line>> linesByOrderId =
                receiptOrderMapper.selectReceiptOrderLines().stream()
                        .collect(Collectors.groupingBy(
                                ReceiptOrderResponse.Line::getReceiptOrderId
                        ));

        for (ReceiptOrderResponse order : orders) {

            List<ReceiptOrderResponse.Line> lines =
                    linesByOrderId.getOrDefault(order.getId(), List.of());

            order.setLines(lines);
            order.setSkuCount(lines.size());
            order.setTotalQty(sumReceivedQty(lines));
        }

        return orders;
    }


    @Transactional
    public CreateReceiptOrderResponse createReceiptOrder(CreateReceiptOrderRequest request) {

        Long inboundOrderId = request.getInboundOrderId();

        /*
         * 1. 锁住入库单行，并发新建同一入库单时串行执行
         */
        Integer inboundStatus =
                inboundOrderMapper.selectInboundStatusForUpdate(inboundOrderId);

        if (inboundStatus == null) {
            throw new BusinessException("入库单不存在");
        }

        if (!InboundOrderStatus.WAIT_RECEIVE.getCode().equals(inboundStatus)) {
            throw new BusinessException("当前状态不允许收货");
        }

        /*
         * 部分收货时入库单仍是待收货，靠状态拦不住第二张收货单；
         * 在行锁之后查，后到的请求能看到先到者已提交的收货单
         */
        if (!inboundOrderMapper.selectReceiptRecords(inboundOrderId).isEmpty()) {
            throw new BusinessException("该入库单已有收货单，请在列表中继续处理");
        }
        /*
         * 2. 计划量合计；上一步已确认存在且已加锁，这里不会为 null
         */
        BigDecimal planQty =
                inboundOrderMapper.selectInboundOrderDetail(inboundOrderId).getPlanQty();

        /*
         * 3. 本次收货合计，判断是否一次收满
         */
        BigDecimal received = request.getItems().stream()
                .map(CreateReceiptOrderRequest.Item::getReceivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean fullyReceived = received.compareTo(planQty) == 0;

        /*
         * 4. 收货单头
         */
        ReceiptOrder order = new ReceiptOrder();
        order.setReceiptOrderNo(generateReceiptOrderNo());
        order.setInboundOrderId(inboundOrderId);
        order.setWarehouseId(request.getWarehouseId());
        order.setReceiverId(request.getReceiverId());
        order.setRemark(request.getRemark());

        if (fullyReceived) {
            order.setReceiptStatus(ReceiptOrderStatus.COMPLETED.getCode());
            order.setReceivedTime(LocalDateTime.now());
        } else {
            order.setReceiptStatus(ReceiptOrderStatus.UNFINISHED.getCode());
            order.setReceivedTime(null);
        }

        receiptOrderMapper.insertReceiptOrder(order);

        /*
         * 5. 收货明细（含 0 行），再累加到入库明细
         */
        List<ReceiptOrderItem> items = request.getItems().stream()
                .map(item -> toEntity(order.getId(), item))
                .toList();

        receiptOrderMapper.insertReceiptOrderItems(items);
        receiptOrderMapper.addInboundItemReceivedQty(order.getId());


        // 只有一次收满才改入库单状态；部分收货时入库单保持待收货
        if (fullyReceived) {
            inboundOrderMapper.updateInboundStatus(
                    inboundOrderId, InboundOrderStatus.RECEIVED.getCode());
        }

        return new CreateReceiptOrderResponse(order.getId(), order.getReceiptOrderNo());
    }


    @Transactional
    public void completeReceiptOrder(Long id) {

        if (receiptOrderMapper.completeReceiptOrder(id) == 0) {
            throw new BusinessException("收货单不存在或已完成");
        }
    }


    private BigDecimal sumReceivedQty(List<ReceiptOrderResponse.Line> lines) {
        return lines.stream()
                .map(ReceiptOrderResponse.Line::getReceivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    private ReceiptOrderItem toEntity(
            Long receiptOrderId,
            CreateReceiptOrderRequest.Item item
    ) {

        ReceiptOrderItem entity = new ReceiptOrderItem();
        entity.setReceiptOrderId(receiptOrderId);
        entity.setInboundOrderItemId(item.getInboundOrderItemId());
        entity.setSkuId(item.getSkuId());
        entity.setReceivedQty(item.getReceivedQty());
        return entity;
    }


    /**
     * RC + yyyyMMddHHmmss + 4 位随机数，共 20 位，对应 CHAR(20)
     */
    private String generateReceiptOrderNo() {

        int random = ThreadLocalRandom.current().nextInt(10000);

        return "RC" + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%04d", random);
    }
}