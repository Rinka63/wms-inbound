package com.example.wms.controller;


import com.example.wms.dto.InventoryRequest;
import com.example.wms.service.InventoryService;
import com.example.wms.vo.InventoryVO;
import com.example.wms.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存查询接口
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 分页查询库存
     *
     * @param request 查询参数（warehouseId、skuCode、page、size）
     * @return 分页结果
     *
     * 示例请求:
     * GET /api/inventory/page?warehouseId=1&skuCode=SKU001&page=1&size=10
     */
    @GetMapping("/page")
    public PageResultVO<InventoryVO> page(InventoryRequest request) {
        return inventoryService.queryPage(request);
    }
}
