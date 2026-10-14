package com.example.wms.service;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.CreatePutawayOrderRequest;
import com.example.wms.dto.CreatePutawayOrderResponse;
import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.PutawayOrderResponse;
import com.example.wms.entity.PutawayOrder;
import com.example.wms.entity.PutawayOrderItem;
import com.example.wms.enums.InboundOrderStatus;
import com.example.wms.enums.PutawayOrderStatus;
import com.example.wms.enums.ReceiptOrderStatus;
import com.example.wms.mapper.InboundOrderMapper;
import com.example.wms.mapper.PutawayOrderMapper;
import com.example.wms.mapper.WarehouseMapper;
import com.example.wms.vo.WarehouseLocationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PutawayOrderService {

    private final PutawayOrderMapper putawayOrderMapper;

    private final InboundOrderMapper inboundOrderMapper;

    private final WarehouseMapper warehouseMapper;

    private static final DateTimeFormatter NO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");


    /**
     * 上架单列表：单头、明细各查一次，按 putawayOrderId 挂载
     * readOnly 事务保证两次查询读到同一快照
     */
    @Transactional(readOnly = true)
    public List<PutawayOrderResponse> getPutawayOrderList() {

        List<PutawayOrderResponse> orders =
                putawayOrderMapper.selectPutawayOrderList();

        Map<Long, List<PutawayOrderResponse.Line>> linesByOrderId =
                putawayOrderMapper.selectPutawayOrderLines().stream()
                        .collect(Collectors.groupingBy(
                                PutawayOrderResponse.Line::getPutawayOrderId
                        ));

        for (PutawayOrderResponse order : orders) {

            List<PutawayOrderResponse.Line> lines =
                    linesByOrderId.getOrDefault(order.getId(), List.of());

            order.setLines(lines);
            // 同一 SKU 可多次登记，按 skuId 去重计数
            order.setSkuCount((int) lines.stream()
                    .map(PutawayOrderResponse.Line::getSkuId)
                    .distinct()
                    .count());
            order.setTotalQty(sumPutawayQty(lines));
        }

        return orders;
    }


    /**
     * 新建（id == null）与继续上架（id != null）共用：
     * 每次登记都追加明细并立即入账，上架单保持上架中
     */
    @Transactional
    public CreatePutawayOrderResponse save(Long id, CreatePutawayOrderRequest request) {

        Long inboundOrderId = request.getInboundOrderId();
        PutawayOrder order = null;

        /*
         * 1. 继续上架：先锁上架单，与完成上架串行；来源以库中记录为准
         */
        if (id != null) {
            order = putawayOrderMapper.selectPutawayOrderForUpdate(id);

            if (order == null) {
                throw new BusinessException("上架单不存在");
            }

            if (!PutawayOrderStatus.PUTTING_AWAY.getCode().equals(order.getPutawayStatus())) {
                throw new BusinessException("上架单已完成，不能继续上架");
            }

            if (!order.getInboundOrderId().equals(inboundOrderId)) {
                throw new BusinessException("来源入库单与上架单不一致");
            }
        }

        /*
         * 2. 锁住入库单行，同一入库单的上架写入串行执行
         */
        Integer inboundStatus =
                inboundOrderMapper.selectInboundStatusForUpdate(inboundOrderId);

        if (inboundStatus == null) {
            throw new BusinessException("入库单不存在");
        }

        if (!InboundOrderStatus.RECEIVED.getCode().equals(inboundStatus)
                && !InboundOrderStatus.PART_PUTAWAY.getCode().equals(inboundStatus)) {
            throw new BusinessException("当前状态不允许上架");
        }

        // 上一步已确认存在且已加锁，这里不会为 null
        Long warehouseId =
                inboundOrderMapper.selectInboundOrderDetail(inboundOrderId).getWarehouseId();

        /*
         * 3. 新建：锁内检查是否已有上架中单据，再插入单头
         */
        if (order == null) {
            order = createOrder(request, inboundOrderId, warehouseId);
        }

        /*
         * 4. 先校验全部行，再按 (库位, SKU) 顺序入账，
         *    跨入库单争用同一库存行时加锁顺序一致，降低死锁概率
         */
        List<InboundOrderDetailResponse.Item> sources =
                inboundOrderMapper.selectInboundOrderItems(inboundOrderId);

        List<PutawayOrderItem> items =
                toItems(order.getId(), warehouseId, sources, request.getItems()).stream()
                        .sorted(Comparator.comparing(PutawayOrderItem::getLocationId)
                                .thenComparing(PutawayOrderItem::getSkuId))
                        .toList();

        for (PutawayOrderItem item : items) {
            post(warehouseId, item);
        }

        /*
         * 5. 重算入库单状态：全部上满为已完成，否则部分上架
         *    （toItems 已把本次数量计入 sources 的 putawayQty）
         */
        boolean allPutaway = sources.stream()
                .allMatch(s -> s.getPutawayQty().compareTo(s.getReceivedQty()) >= 0);

        InboundOrderStatus next = allPutaway
                ? InboundOrderStatus.COMPLETED
                : InboundOrderStatus.PART_PUTAWAY;

        inboundOrderMapper.updateInboundStatus(inboundOrderId, next.getCode());

        return new CreatePutawayOrderResponse(order.getId(), order.getPutawayOrderNo());
    }


    /**
     * 完成上架：只改状态和完成时间；不动明细、库存、流水和入库单状态
     */
    @Transactional
    public void completePutawayOrder(Long id) {

        if (putawayOrderMapper.completePutawayOrder(id) == 0) {
            throw new BusinessException("上架单不存在或已完成");
        }
    }


    private PutawayOrder createOrder(
            CreatePutawayOrderRequest request,
            Long inboundOrderId,
            Long warehouseId
    ) {

        /*
         * 在入库单行锁之后查，后到的请求能看到先到者已提交的上架单
         */
        boolean hasOpen = inboundOrderMapper.selectPutawayRecords(inboundOrderId).stream()
                .anyMatch(p -> PutawayOrderStatus.PUTTING_AWAY.getCode().equals(p.getStatus()));

        if (hasOpen) {
            throw new BusinessException("该入库单已有上架中的上架单，请在列表中继续上架");
        }

        /*
         * 一张入库单最多一张收货单（ReceiptOrderService 保证）
         */
        List<InboundOrderDetailResponse.Receipt> receipts =
                inboundOrderMapper.selectReceiptRecords(inboundOrderId);

        if (receipts.isEmpty()
                || !ReceiptOrderStatus.COMPLETED.getCode().equals(receipts.get(0).getStatus())) {
            throw new BusinessException("收货单未完成，请先完成收货");
        }

        PutawayOrder order = new PutawayOrder();
        order.setPutawayOrderNo(generatePutawayOrderNo());
        order.setInboundOrderId(inboundOrderId);
        order.setReceiptOrderId(receipts.get(0).getId());
        order.setWarehouseId(warehouseId);
        order.setOperatorId(request.getOperatorId());
        // 显式写 1
        order.setPutawayStatus(PutawayOrderStatus.PUTTING_AWAY.getCode());
        order.setPutawayTime(null);
        order.setRemark(request.getRemark());

        putawayOrderMapper.insertPutawayOrder(order);

        return order;
    }


    /**
     * 逐行校验来源归属、剩余量、库位，补全后端字段；不信任前端的 skuId、数量上限
     * 校验通过后把本次数量计入 source.putawayQty，供 save 第 5 步判断是否全部上满
     */
    private List<PutawayOrderItem> toItems(
            Long putawayOrderId,
            Long warehouseId,
            List<InboundOrderDetailResponse.Item> sources,
            List<CreatePutawayOrderRequest.Item> requestItems
    ) {

        Map<Long, InboundOrderDetailResponse.Item> sourceById = sources.stream()
                .collect(Collectors.toMap(
                        InboundOrderDetailResponse.Item::getId,
                        Function.identity()
                ));

        Set<Long> requested = requestItems.stream()
                .map(CreatePutawayOrderRequest.Item::getLocationId)
                .collect(Collectors.toSet());

        Set<Long> locationIds = new HashSet<>(
                warehouseMapper.selectEnabledLocationIds(warehouseId, requested));

        Set<Long> seen = new HashSet<>();
        List<PutawayOrderItem> items = new ArrayList<>();

        for (CreatePutawayOrderRequest.Item req : requestItems) {

            if (!seen.add(req.getInboundOrderItemId())) {
                throw new BusinessException("上架明细重复：" + req.getInboundOrderItemId());
            }

            InboundOrderDetailResponse.Item source = sourceById.get(req.getInboundOrderItemId());

            if (source == null) {
                throw new BusinessException("明细 " + req.getInboundOrderItemId() + " 不属于该入库单");
            }

            BigDecimal remaining = source.getReceivedQty().subtract(source.getPutawayQty());

            if (req.getPutawayQty().compareTo(remaining) > 0) {
                throw new BusinessException(
                        "SKU " + source.getSkuId() + " 超过可上架量 " + remaining.toPlainString());
            }

            if (!locationIds.contains(req.getLocationId())) {
                throw new BusinessException(
                        "SKU " + source.getSkuId() + " 的目标库位不存在、已停用或不属于该仓库");
            }

            source.setPutawayQty(source.getPutawayQty().add(req.getPutawayQty()));

            PutawayOrderItem item = new PutawayOrderItem();
            item.setPutawayOrderId(putawayOrderId);
            item.setInboundOrderItemId(source.getId());
            item.setSkuId(source.getSkuId());
            item.setLocationId(req.getLocationId());
            item.setPutawayQty(req.getPutawayQty());
            items.add(item);
        }

        return items;
    }


    /**
     * 单行入账：累加入库明细 → 插入上架明细 → 库存累加（持有行锁）→ 写流水
     */
    private void post(Long warehouseId, PutawayOrderItem item) {

        // 条件更新兜底：toItems 已按锁内快照校验，这里正常不会返回 0
        if (putawayOrderMapper.increasePutawayQty(
                item.getInboundOrderItemId(), item.getPutawayQty()) == 0) {
            throw new BusinessException("SKU " + item.getSkuId() + " 超过可上架量");
        }

        putawayOrderMapper.insertPutawayOrderItem(item);

        putawayOrderMapper.increaseInventory(warehouseId, item);

        putawayOrderMapper.insertInventoryTransaction(item);
    }


    private BigDecimal sumPutawayQty(List<PutawayOrderResponse.Line> lines) {
        return lines.stream()
                .map(PutawayOrderResponse.Line::getPutawayQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    /**
     * PA + yyyyMMddHHmmss + 4 位随机数，共 20 位，对应 CHAR(20)
     */
    private String generatePutawayOrderNo() {

        int random = ThreadLocalRandom.current().nextInt(10000);

        return "PA" + LocalDateTime.now().format(NO_FORMATTER)
                + String.format("%04d", random);
    }
}
