package com.example.wms.service;

import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.mapper.InboundOrderMapper;
import com.example.wms.dto.CreateInboundOrderRequest;
import com.example.wms.dto.CreateInboundOrderResponse;
import com.example.wms.entity.InboundOrder;
import com.example.wms.entity.InboundOrderItem;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundOrderService {

    private final InboundOrderMapper inboundOrderMapper;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ORDER_NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");



    public List<InboundOrderResponse> getInboundOrderList() {
        return inboundOrderMapper.selectInboundOrderList();
    }

    @Transactional
    public CreateInboundOrderResponse createInboundOrder(CreateInboundOrderRequest request) {

        /*
         * 1. 插入单头，入库类型固定为采购入库
         */
        InboundOrder order = new InboundOrder();
        order.setInboundOrderNo(generateOrderNo());
        order.setWarehouseId(request.getWarehouseId());
        order.setInboundType(1);
        order.setInboundStatus(request.getStatus());
        order.setCreatedBy(request.getCreatedBy());
        order.setRemark(request.getRemark());

        inboundOrderMapper.insertInboundOrder(order);

        /*
         * 2. 批量插入明细
         */
        List<InboundOrderItem> items = request.getItems().stream()
                .map(source -> {
                    InboundOrderItem item = new InboundOrderItem();
                    item.setInboundOrderId(order.getId());
                    item.setSkuId(source.getSkuId());
                    item.setPlanQty(source.getPlanQty());
                    return item;
                })
                .toList();

        inboundOrderMapper.insertInboundOrderItems(items);

        return new CreateInboundOrderResponse(
                order.getId(),
                order.getInboundOrderNo()
        );
    }

    private String generateOrderNo() {
        return "IN"
                + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    @Transactional(readOnly = true)
    public InboundOrderDetailResponse getInboundOrderDetail(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "入库单ID不正确"
            );
        }

        /*
         * 1. 查询单头
         */
        InboundOrderDetailResponse detail =
                inboundOrderMapper.selectInboundOrderDetail(id);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "入库单不存在"
            );
        }

        /*
         * 2. 查询入库明细
         */
        List<InboundOrderDetailResponse.Item> items =
                inboundOrderMapper.selectInboundOrderItems(id);

        /*
         * 3. 查询收货记录
         */
        List<InboundOrderDetailResponse.Receipt> receipts =
                inboundOrderMapper.selectReceiptRecords(id);

        /*
         * 4. 查询上架记录
         */
        List<InboundOrderDetailResponse.Putaway> putaways =
                inboundOrderMapper.selectPutawayRecords(id);

        detail.setItems(items);
        detail.setReceipts(receipts);
        detail.setPutaways(putaways);

        /*
         * 5. 根据已有业务数据构造业务轨迹
         */
        detail.setTimeline(
                buildTimeline(detail, receipts, putaways)
        );

        return detail;
    }


    private List<InboundOrderDetailResponse.TimelineEvent> buildTimeline(
            InboundOrderDetailResponse detail,
            List<InboundOrderDetailResponse.Receipt> receipts,
            List<InboundOrderDetailResponse.Putaway> putaways
    ) {

        List<InboundOrderDetailResponse.TimelineEvent> timeline = new ArrayList<>();

        /*
         * 入库单创建
         */
        timeline.add(
                new InboundOrderDetailResponse.TimelineEvent(
                        "CREATE",
                        "创建入库单",
                        buildMeta(
                                detail.getCreatorName(),
                                detail.getCreatedTime()
                        ),
                        detail.getCreatedTime()
                )
        );


        /*
         * 收货轨迹
         */
        for (InboundOrderDetailResponse.Receipt receipt : receipts) {

            LocalDateTime eventTime =
                    receipt.getTime() != null
                            ? receipt.getTime()
                            : receipt.getCreatedTime();

            timeline.add(
                    new InboundOrderDetailResponse.TimelineEvent(
                            "RECEIPT",
                            buildReceiptTitle(receipt),
                            buildMeta(
                                    receipt.getReceiver(),
                                    eventTime
                            ),
                            eventTime
                    )
            );
        }


        /*
         * 上架轨迹
         */
        for (InboundOrderDetailResponse.Putaway putaway : putaways) {

            LocalDateTime eventTime =
                    putaway.getTime() != null
                            ? putaway.getTime()
                            : putaway.getCreatedTime();

            timeline.add(
                    new InboundOrderDetailResponse.TimelineEvent(
                            "PUTAWAY",
                            buildPutawayTitle(putaway),
                            buildMeta(
                                    putaway.getOperator(),
                                    eventTime
                            ),
                            eventTime
                    )
            );
        }


        /*
         * 从最早到最新
         */
        timeline.sort(
                Comparator.comparing(
                        InboundOrderDetailResponse.TimelineEvent::getTime,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                )
        );

        return timeline;
    }


    private String buildReceiptTitle(
            InboundOrderDetailResponse.Receipt receipt
    ) {

        String suffix = switch (receipt.getStatus()) {
            case 0 -> "未完成";
            case 1 -> "已完成";
            case 2 -> "已取消";
            default -> "";
        };

        return "收货单 " + receipt.getNo() + " " + suffix;
    }


    private String buildPutawayTitle(
            InboundOrderDetailResponse.Putaway putaway
    ) {

        String suffix = switch (putaway.getStatus()) {
            case 0 -> "待上架";
            case 1 -> "上架中";
            case 2 -> "已完成";
            case 3 -> "已取消";
            default -> "";
        };

        return "上架单 " + putaway.getNo() + " " + suffix;
    }


    private String buildMeta(
            String operator,
            LocalDateTime time
    ) {

        String name =
                operator == null || operator.isBlank()
                        ? "未知操作人"
                        : operator;

        if (time == null) {
            return name;
        }

        return name + " · " + TIME_FORMATTER.format(time);
    }
}