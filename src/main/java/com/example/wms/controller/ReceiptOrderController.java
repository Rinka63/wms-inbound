package com.example.wms.controller;

import com.example.wms.dto.CreateReceiptOrderRequest;
import com.example.wms.dto.CreateReceiptOrderResponse;
import com.example.wms.dto.ReceiptOrderResponse;
import com.example.wms.service.ReceiptOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inbound/receipts")
@RequiredArgsConstructor
public class ReceiptOrderController {
    private final ReceiptOrderService receiptOrderService;

    @GetMapping
    public ResponseEntity<List<ReceiptOrderResponse>> getReceiptOrderList() {

        List<ReceiptOrderResponse> list =
                receiptOrderService.getReceiptOrderList();

        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<CreateReceiptOrderResponse> createReceiptOrder(
            @Valid
            @RequestBody CreateReceiptOrderRequest request) {

        CreateReceiptOrderResponse response =
                receiptOrderService.createReceiptOrder(request);

        return ResponseEntity.ok(response);
    }

    /**
     * 确认全部收货：无请求体，成功返回 200 空响应
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> completeReceiptOrder(@PathVariable Long id) {

        receiptOrderService.completeReceiptOrder(id);

        return ResponseEntity.ok().build();
    }
}
