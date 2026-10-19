package com.example.wms.mapper;

import com.example.wms.dto.InventoryTransactionResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryTransactionMapper {

    /**
     * 查询总数
     */
    long count(
        @Param("warehouseId") Long warehouseId,
        @Param("skuId") Long skuId,
        @Param("direction") String direction
    );

    /**
     * 分页查询
     */
    List<InventoryTransactionResponse> selectPage(
        @Param("warehouseId") Long warehouseId,
        @Param("skuId") Long skuId,
        @Param("direction") String direction,
        @Param("offset") long offset,
        @Param("limit") int limit
    );
}
