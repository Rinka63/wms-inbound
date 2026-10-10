package com.example.wms.service;

import com.example.wms.vo.WarehouseVO;
import com.example.wms.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class WarehouseService {


    private final WarehouseMapper warehouseMapper;


    public List<WarehouseVO> listAvailable(){

        return warehouseMapper.selectWarehouseList();

    }

}