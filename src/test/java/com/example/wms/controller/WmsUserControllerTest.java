package com.example.wms.controller;

import com.example.wms.dto.CreateWmsUserRequest;
import com.example.wms.dto.UpdateWmsUserRequest;
import com.example.wms.dto.WmsUserResponse;
import com.example.wms.service.WmsUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WmsUserController.class)
class WmsUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WmsUserService wmsUserService;

    @Test
    void createUser_shouldReturnUserId() throws Exception {
        when(wmsUserService.createUser(any(CreateWmsUserRequest.class)))
                .thenReturn(100L);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "123456",
                                  "realName": "张三",
                                  "mobile": "13800138000",
                                  "email": "zhangsan@example.com",
                                  "defaultWarehouseId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("100"));

        verify(wmsUserService).createUser(any(CreateWmsUserRequest.class));
    }

    @Test
    void createUser_whenRequestIsInvalid_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": "123",
                                  "realName": "",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(wmsUserService, never())
                .createUser(any(CreateWmsUserRequest.class));
    }

    @Test
    void getUser_shouldReturnUser() throws Exception {
        WmsUserResponse response = createResponse();
        when(wmsUserService.getUserById(1L)).thenReturn(response);

        mockMvc.perform(get("/users/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("zhangsan"))
                .andExpect(jsonPath("$.realName").value("张三"))
                .andExpect(jsonPath("$.status").value(1));

        verify(wmsUserService).getUserById(1L);
    }

    @Test
    void getUserByUsername_shouldReturnUser() throws Exception {
        WmsUserResponse response = createResponse();
        when(wmsUserService.getUserByUsername("zhangsan")).thenReturn(response);

        mockMvc.perform(get("/users/username/{username}", "zhangsan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("zhangsan"));

        verify(wmsUserService).getUserByUsername("zhangsan");
    }

    @Test
    void updateUser_shouldReturnNoContent() throws Exception {
        mockMvc.perform(put("/users/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "realName": "李四",
                                  "mobile": "13900139000",
                                  "email": "lisi@example.com",
                                  "defaultWarehouseId": 2
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(wmsUserService).updateUser(
                org.mockito.ArgumentMatchers.eq(1L),
                any(UpdateWmsUserRequest.class));
    }

    @Test
    void enableUser_shouldReturnNoContent() throws Exception {
        mockMvc.perform(post("/users/{id}/enable", 1L))
                .andExpect(status().isNoContent());

        verify(wmsUserService).enableUser(1L);
    }

    @Test
    void disableUser_shouldReturnNoContent() throws Exception {
        mockMvc.perform(post("/users/{id}/disable", 1L))
                .andExpect(status().isNoContent());

        verify(wmsUserService).disableUser(1L);
    }

    private WmsUserResponse createResponse() {
        WmsUserResponse response = new WmsUserResponse();
        response.setId(1L);
        response.setUsername("zhangsan");
        response.setRealName("张三");
        response.setMobile("13800138000");
        response.setEmail("zhangsan@example.com");
        response.setDefaultWarehouseId(1L);
        response.setStatus(1);
        return response;
    }
}
