package com.example.wms.controller;

import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.service.InboundOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inbound/orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;

    @GetMapping
    public ResponseEntity<List<InboundOrderResponse>> getInboundOrderList() {

        List<InboundOrderResponse> list =
                inboundOrderService.getInboundOrderList();

        return ResponseEntity.ok(list);
    }
}