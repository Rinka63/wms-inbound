package com.example.wms.mapper;

import com.example.wms.entity.LocationStockItem;
import com.example.wms.vo.InventoryVO;
import com.example.wms.vo.LocationStockVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMapper {
    /**
     * 统计符合条件的仓库和SKU总数
     */
    Long countGroups(
            @Param("warehouseId") Long warehouseId,
            @Param("skuCode") String skuCode
    );

    /**
     * 分页查询库存分组（返回汇总数量，不含库位明细）
     *
     * @param warehouseId 仓库ID（可选）
     * @param skuCode SKU编码（可选，精确匹配）
     * @param offset 偏移量（从0开始）
     * @param size 每页条数
     * @return 库存分组列表（不含locations字段）
     */
    List<InventoryVO> selectGroupPage(
            @Param("warehouseId") Long warehouseId,
            @Param("skuCode") String skuCode,
            @Param("offset") Integer offset,
            @Param("size") Integer size
    );

    /**
     * 一次查询返回所有库位数据，用于防止N+1查询问题：
     *
     */
    List<LocationStockItem> selectLocations(
            @Param("groups") List<InventoryVO> groups
    );


}
