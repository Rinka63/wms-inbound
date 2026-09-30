package com.example.wms.mapper;

import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.InboundOrderResponse;
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
}