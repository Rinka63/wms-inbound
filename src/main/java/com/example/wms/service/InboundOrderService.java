package com.example.wms.service;

import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.InboundOrderRequest;
import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.mapper.InboundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundOrderService {

    private final InboundOrderMapper inboundOrderMapper;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");


    public List<InboundOrderResponse> getInboundOrderList() {
        return inboundOrderMapper.selectInboundOrderList();
    }

    @Transactional
    public InboundOrderResponse createInboundOrder(InboundOrderRequest request) {

        if (request.getWarehouseId() == null || request.getWarehouseId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "仓库ID不正确"
            );
        }

        if (request.getCreatedBy() == null || request.getCreatedBy() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "创建人ID不正确"
            );
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "入库单明细不能为空"
            );
        }

        // 验证明细项
        for (InboundOrderRequest.InboundOrderItemRequest item : request.getItems()) {
            if (item.getSkuId() == null || item.getSkuId() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "SKU ID不正确"
                );
            }
            if (item.getPlanQty() == null || item.getPlanQty() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "计划数量必须大于0"
                );
            }
        }

        // 生成入库单号
        String inboundOrderNo = generateInboundOrderNo();

        // 插入入库单头
        int rows = inboundOrderMapper.insertInboundOrder(
                request.getWarehouseId(),
                inboundOrderNo,
                1, // inboundType: 采购入库
                request.getStatus() != null ? request.getStatus() : 1,
                request.getCreatedBy(),
                request.getRemark()
        );

        if (rows == 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "创建入库单失败"
            );
        }

        // 获取插入的ID
        Long inboundOrderId = inboundOrderMapper.selectLastInsertId();

        // 插入入库单明细
        int itemRows = inboundOrderMapper.insertInboundOrderItems(
                inboundOrderId,
                request.getItems()
        );

        if (itemRows != request.getItems().size()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "创建入库单明细失败"
            );
        }

        // 返回创建的入库单信息
        InboundOrderResponse response = new InboundOrderResponse();
        response.setId(inboundOrderId);
        response.setInboundOrderNo(inboundOrderNo);
        response.setWarehouseId(request.getWarehouseId());
        response.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        response.setCreatedTime(LocalDateTime.now());

        return response;
    }

    private String generateInboundOrderNo() {
        // 格式: IB + yyyyMMddHHmmss + 随机3位数
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = (int) (Math.random() * 900) + 100;
        return "IB" + timestamp + random;
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
            case 0 -> "已创建";
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