package com.example.wms.mapper;

import com.example.wms.vo.WarehouseLocationVO;
import com.example.wms.vo.WarehouseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WarehouseMapper {

    List<WarehouseVO> selectWarehouseList();

    /**
     * 从给定库位中筛出属于该仓库且已启用的 ID，用于保存时校验
     */
    List<Long> selectEnabledLocationIds(
            @Param("warehouseId") Long warehouseId,
            @Param("locationIds") Collection<Long> locationIds
    );

    /**
     * 查询启用库位
     * @param warehouseId 仓库ID，为 null 时查询所有启用库位，有值时查询指定仓库的启用库位
     */
    List<WarehouseLocationVO> selectEnabledLocations(
            @Param("warehouseId") Long warehouseId
    );

}
