package com.example.wms.service;


import com.example.wms.common.BusinessException;
import com.example.wms.dto.DashboardResponse;
import com.example.wms.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    public DashboardResponse getOverview(Long userId){

        DashboardResponse response = dashboardMapper.selectOverviewByuserId(userId);

        if(null == response){
            throw new BusinessException("用户不存在、用户已禁用或未配置默认仓库");
        }

        Long total = response.getTotalTaskCount();
        Long completed = response.getCompletedTaskCount();

        if(0 == total||0 == completed){
            response.setCompletionRate(0);
        }else{
            int rate = (int)Math.round(completed*100.0 / total);
            response.setCompletionRate(rate);
        }

        return response;
    }

}
