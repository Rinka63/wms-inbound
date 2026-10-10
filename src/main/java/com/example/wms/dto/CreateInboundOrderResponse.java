package com.example.wms.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateInboundOrderResponse {

    private Long id;

    private String inboundOrderNo;
}
