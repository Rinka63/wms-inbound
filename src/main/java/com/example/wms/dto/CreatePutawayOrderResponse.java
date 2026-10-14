package com.example.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePutawayOrderResponse {

    private Long id;

    private String putawayOrderNo;

    private Integer status;
}
