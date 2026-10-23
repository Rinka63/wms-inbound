package com.example.wms.controller;

import com.example.wms.dto.InboundOrderDetailResponse;
import com.example.wms.dto.InboundOrderResponse;
import com.example.wms.dto.InboundOrderRequest;
import com.example.wms.service.InboundOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "入库单管理", description = "入库单相关接口")
@RestController
@RequestMapping("/inbound/orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;

    @Operation(summary = "查询入库单列表")
    @GetMapping
    public ResponseEntity<List<InboundOrderResponse>> getInboundOrderList() {

        List<InboundOrderResponse> list =
                inboundOrderService.getInboundOrderList();

        return ResponseEntity.ok(list);
    }

    @Operation(summary = "根据ID查询入库单详情")
    @GetMapping("/{id}")
    public ResponseEntity<InboundOrderDetailResponse> getInboundOrderDetail(@PathVariable Long id) {

        InboundOrderDetailResponse response =
                inboundOrderService.getInboundOrderDetail(id);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "创建入库单")
    @PostMapping
    public ResponseEntity<InboundOrderResponse> createInboundOrder(@RequestBody InboundOrderRequest request) {

        InboundOrderResponse response =
                inboundOrderService.createInboundOrder(request);

        return ResponseEntity.ok(response);
    }
}