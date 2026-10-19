package com.example.wms.controller;


import com.example.wms.vo.WarehouseLocationVO;
import com.example.wms.vo.WarehouseVO;
import com.example.wms.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/warehouses")
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

    /**
     * 查询库位
     */
    @GetMapping("/{id}/locations")
    public List<WarehouseLocationVO> locations(@PathVariable Long id){

        return warehouseService.listEnabledLocations(id);
    }

    /**
     * 查询所有库位（用于前端一次性加载）
     */
    @GetMapping("/locations")
    public List<WarehouseLocationVO> allLocations() {
        return warehouseService.listAllEnabledLocations();
    }


}