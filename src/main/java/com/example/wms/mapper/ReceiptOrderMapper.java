package com.example.wms.mapper;

import com.example.wms.dto.ReceiptOrderResponse;
import com.example.wms.entity.ReceiptOrder;
import com.example.wms.entity.ReceiptOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReceiptOrderMapper {

    /**
     * 查询收货单头列表
     */
    List<ReceiptOrderResponse> selectReceiptOrderList();


    /**
     * 查询全部收货明细，Service 中按 receiptOrderId 分组
     */
    List<ReceiptOrderResponse.Line> selectReceiptOrderLines();


    /**
     * 插入收货单头，回填 id；receiptStatus / receivedTime 由 Service 预先算好
     */
    int insertReceiptOrder(ReceiptOrder receiptOrder);


    /**
     * 批量插入收货明细（含数量为 0 的行）
     */
    int insertReceiptOrderItems(
            @Param("items") List<ReceiptOrderItem> items
    );


    /**
     * 新建时把本单收货量累加到入库明细 received_qty
     */
    int addInboundItemReceivedQty(
            @Param("receiptOrderId") Long receiptOrderId
    );


    /**
     * 确认全部收货：补满收货明细和入库明细，收货单置为已完成，入库单置为已收货
     * 返回 0 表示收货单不存在或已完成
     */
    int completeReceiptOrder(
            @Param("id") Long id
    );
}
