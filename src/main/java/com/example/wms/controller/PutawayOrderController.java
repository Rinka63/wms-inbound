package com.example.wms.controller;


import com.example.wms.dto.CreatePutawayOrderRequest;
import com.example.wms.dto.CreatePutawayOrderResponse;
import com.example.wms.dto.PutawayOrderResponse;
import com.example.wms.service.PutawayOrderService;
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
@RequestMapping("/inbound/putaways")
@RequiredArgsConstructor
public class PutawayOrderController {

    private final PutawayOrderService putawayOrderService;

    @GetMapping
    public ResponseEntity<List<PutawayOrderResponse>> getPutawayOrderList() {

        List<PutawayOrderResponse> list =
                putawayOrderService.getPutawayOrderList();

        return ResponseEntity.ok(list);
    }

    /**
     * 新建上架单并登记首批（同时入账）
     */
    @PostMapping
    public ResponseEntity<CreatePutawayOrderResponse> createPutawayOrder(
            @Valid
            @RequestBody CreatePutawayOrderRequest request) {

        CreatePutawayOrderResponse response =
                putawayOrderService.save(null, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 继续上架：只追加新明细并入账
     */
    @PostMapping("/{id}/items")
    public ResponseEntity<CreatePutawayOrderResponse> appendPutawayItems(
            @PathVariable Long id,
            @Valid
            @RequestBody CreatePutawayOrderRequest request) {

        CreatePutawayOrderResponse response =
                putawayOrderService.save(id, request);

        return ResponseEntity.ok(response);
    }
}
