package com.example.wms.mapper;

import com.example.wms.dto.DashboardResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {
    DashboardResponse selectOverviewByuserId(@Param("userId") Long userId);
}
