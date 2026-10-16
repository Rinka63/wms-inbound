package com.example.wms.service;


import com.example.wms.common.BusinessException;
import com.example.wms.dto.InventoryRequest;
import com.example.wms.entity.LocationStockItem;
import com.example.wms.mapper.InventoryMapper;
import com.example.wms.vo.InventoryVO;
import com.example.wms.vo.LocationStockVO;
import com.example.wms.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryMapper inventoryMapper;

    /**
     * 分页查询库存
     *
     * @param dto 查询参数
     * @return 分页结果（包含库位明细）
     */
    public PageResultVO<InventoryVO> queryPage(InventoryRequest dto) {
        // ========== 步骤1: 参数标准化和校验 ==========
        String skuCode = StringUtils.trimToNull(dto.getSkuCode());

        // SKU编码长度校验
        if (skuCode != null && skuCode.length() > 64) {
            throw new BusinessException("SKU编码不能超过64字符");
        }

        // 页码校验
        if (dto.getPage() == null || dto.getPage() < 1) {
            throw new BusinessException("页码必须 >= 1");
        }

        // 每页条数校验（仅允许10/20/50）
        if (dto.getSize() == null || !Arrays.asList(10, 20, 50).contains(dto.getSize())) {
            throw new BusinessException("每页条数仅支持 10/20/50");
        }

        // ========== 步骤2: 统计总数 ==========
        Long total = inventoryMapper.countGroups(dto.getWarehouseId(), skuCode);

        // 无数据时直接返回空结果
        if (total == 0) {
            return new PageResultVO<>(0L, Collections.emptyList());
        }

        // ========== 步骤3: 分页查询分组 ==========
        int offset = (dto.getPage() - 1) * dto.getSize();
        List<InventoryVO> groups = inventoryMapper.selectGroupPage(
                dto.getWarehouseId(),
                skuCode,
                offset,
                dto.getSize()
        );

        // ========== 步骤4: 批量查询库位（防止N+1问题） ==========
        if (!groups.isEmpty()) {
            // 一次性查询当前页所有分组的库位明细
            List<LocationStockItem> allLocations =
                    inventoryMapper.selectLocations(groups);

            // ========== 步骤5: 组装库位到各分组 ==========
            // 按 "warehouseId-skuId" 分组库位数据
            Map<String, List<LocationStockVO>> locationMap = allLocations.stream()
                    .collect(Collectors.groupingBy(
                            // 分组键: "warehouseId-skuId"
                            loc -> loc.getWarehouseId() + "-" + loc.getSkuId(),
                            // 转换为LocationStockVO
                            Collectors.mapping(
                                    loc -> new LocationStockVO(
                                            loc.getLocationId(),
                                            loc.getLocationCode(),
                                            loc.getQuantity()
                                    ),
                                    Collectors.toList()
                            )
                    ));

            // 将库位列表填充到对应的分组中
            groups.forEach(group -> {
                String key = group.getWarehouseId() + "-" + group.getSkuId();
                group.setLocations(
                        locationMap.getOrDefault(key, Collections.emptyList())
                );
            });
        }

        return new PageResultVO<>(total, groups);
    }


}
