package com.example.wms.controller;


import com.example.wms.dto.DashboardResponse;
import com.example.wms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardResponse> getOverview(@RequestParam Long userId){
        DashboardResponse response = dashboardService.getOverview(userId);
        return ResponseEntity.ok(response);
    }

}
