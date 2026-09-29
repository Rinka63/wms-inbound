package com.example.wms.mapper;

import com.example.wms.dto.InboundOrderResponse;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InboundOrderMapper {

    List<InboundOrderResponse> selectInboundOrderList();
}