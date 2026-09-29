package com.example.wms.service;

import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.mapper.InboundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InboundOrderService {

    private final InboundOrderMapper inboundOrderMapper;

    public List<InboundOrderResponse> getInboundOrderList() {
        return inboundOrderMapper.selectInboundOrderList();
    }
}