package com.example.wms.mapper;

import com.example.wms.dto.PutawayOrderResponse;
import com.example.wms.entity.PutawayOrder;
import com.example.wms.entity.PutawayOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PutawayOrderMapper {

    /**
     * 查询上架单头列表
     */
    List<PutawayOrderResponse> selectPutawayOrderList();


    /**
     * 查询全部上架明细，Service 中按 putawayOrderId 分组
     */
    List<PutawayOrderResponse.Line> selectPutawayOrderLines();


    /**
     * 加行锁读取上架单，继续上架时与完成上架串行
     * 返回 null 表示上架单不存在
     */
    PutawayOrder selectPutawayOrderForUpdate(
            @Param("id") Long id
    );


    /**
     * 插入上架单头，回填 id；putawayStatus 由 Service 显式设为 1
     */
    int insertPutawayOrder(PutawayOrder putawayOrder);


    /**
     * 插入一条上架明细，回填 id；receipt_order_item_id 在 SQL 中反查
     */
    int insertPutawayOrderItem(PutawayOrderItem item);


    /**
     * 累加入库明细已上架量；超过已收货量时影响 0 行
     */
    int increasePutawayQty(
            @Param("inboundOrderItemId") Long inboundOrderItemId,
            @Param("qty") BigDecimal qty
    );


    /**
     * 库位 + SKU 首次入库时插入，否则在原行上累加；行锁持有到事务结束
     */
    int increaseInventory(
            @Param("warehouseId") Long warehouseId,
            @Param("item") PutawayOrderItem item
    );


    /**
     * 写库存流水，须在 increaseInventory 之后调用；变动前、变动后数量在 SQL 中计算
     */
    int insertInventoryTransaction(PutawayOrderItem item);


    /**
     * 完成上架：只改状态和完成时间，不入账
     * 返回 0 表示上架单不存在或已完成
     */
    int completePutawayOrder(
            @Param("id") Long id
    );
}
