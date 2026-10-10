package com.example.wms.mapper;

import com.example.wms.vo.WarehouseVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WarehouseMapper {

    List<WarehouseVO> selectWarehouseList();
}
