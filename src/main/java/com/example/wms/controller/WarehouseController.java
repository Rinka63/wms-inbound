package com.example.wms.controller;


import com.example.wms.vo.WarehouseVO;
import com.example.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WarehouseController {


    private final WarehouseService warehouseService;


    /**
     * 查询可选仓库
     */
    @GetMapping
    public List<WarehouseVO> list(){

        return warehouseService.listAvailable();

    }

}