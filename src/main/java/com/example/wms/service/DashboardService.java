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

        // total 为 null 或 0 时完成率按 0 处理，避免拆箱 NPE 和除零
        if (total == null || total == 0L) {
            response.setCompletionRate(0);
        } else {
            long done = completed == null ? 0L : completed;
            response.setCompletionRate((int) Math.round(done * 100.0 / total));
        }

        return response;
    }

}
