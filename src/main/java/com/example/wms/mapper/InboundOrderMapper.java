package com.example.wms.mapper;

import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.dto.InboundOrderRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InboundOrderMapper {

    List<InboundOrderResponse> selectInboundOrderList();
    /**
     * 查询入库单头
     */
    InboundOrderDetailResponse selectInboundOrderDetail(
            @Param("id") Long id
    );


    /**
     * 查询入库单明细
     */
    List<InboundOrderDetailResponse.Item> selectInboundOrderItems(
            @Param("inboundOrderId") Long inboundOrderId
    );


    /**
     * 查询关联收货记录
     */
    List<InboundOrderDetailResponse.Receipt> selectReceiptRecords(
            @Param("inboundOrderId") Long inboundOrderId
    );


    /**
     * 查询关联上架记录
     */
    List<InboundOrderDetailResponse.Putaway> selectPutawayRecords(
            @Param("inboundOrderId") Long inboundOrderId
    );


    /**
     * 加行锁读取入库单状态，防止并发重复建收货单
     * 返回 null 表示入库单不存在
     */
    Integer selectInboundStatusForUpdate(
            @Param("id") Long id
    );


    /**
     * 更新入库单状态
     */
    int updateInboundStatus(
            @Param("id") Long id,
            @Param("status") Integer status
    );

    /**
     * 插入入库单头
     */
    int insertInboundOrder(
            @Param("warehouseId") Long warehouseId,
            @Param("inboundOrderNo") String inboundOrderNo,
            @Param("inboundType") Integer inboundType,
            @Param("inboundStatus") Integer inboundStatus,
            @Param("createdBy") Long createdBy,
            @Param("remark") String remark
    );

    /**
     * 获取最后插入的ID
     */
    Long selectLastInsertId();

    /**
     * 批量插入入库单明细
     */
    int insertInboundOrderItems(
            @Param("inboundOrderId") Long inboundOrderId,
            @Param("items") List<InboundOrderRequest.InboundOrderItemRequest> items
    );
}