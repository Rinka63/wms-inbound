package com.example.wms.service;

import com.example.wms.common.BusinessException;
import com.example.wms.dto.CreateWmsUserRequest;
import com.example.wms.dto.UpdateWmsUserRequest;
import com.example.wms.dto.WmsUserResponse;
import com.example.wms.entity.WmsUser;
import com.example.wms.mapper.WmsUserMapper;
import com.example.wms.common.PasswordConfig;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WmsUserService {

    private final WmsUserMapper wmsUserMapper;
    private final PasswordEncoder passwordEncoder;

    public Long createUser(CreateWmsUserRequest request){

        WmsUser user = new WmsUser();
        user.setUsername(request.getUsername());
//        user.setPasswordHash(request.getPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setDefaultWarehouseId(request.getDefaultWarehouseId());

        user.setStatus(1);

        int rows = wmsUserMapper.insert(user);

        if(1 != rows){
            throw new BusinessException("用户已存在");
        }
        return user.getId();
    }

    /**
     * 根据ID查询用户
     */
    public WmsUserResponse getUserById(Long id) {

        WmsUser user = wmsUserMapper.selectById(id);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        return convertToResponse(user);
    }

    /**
     * 根据用户名查询
     */
    public WmsUserResponse getUserByUsername(String username) {

        WmsUser user = wmsUserMapper.selectByUsername(username);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        return convertToResponse(user);
    }

    /**
     *验证用户登录信息
     *
     */
    public WmsUserResponse authenticate(String username, String passWord){
        WmsUser user = wmsUserMapper.selectByUsername(username);

        if (user == null ||
                !passwordEncoder.matches(passWord, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException("用户已被禁用");
        }

        return convertToResponse(user);
    }

    /**
     * 修改用户基本资料
     */
    @Transactional
    public void updateUser(
            Long id,
            UpdateWmsUserRequest request) {

        WmsUser user = wmsUserMapper.selectById(id);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        WmsUser updateUser = new WmsUser();

        updateUser.setId(id);
        updateUser.setRealName(request.getRealName());
        updateUser.setMobile(request.getMobile());
        updateUser.setEmail(request.getEmail());
        updateUser.setDefaultWarehouseId(
                request.getDefaultWarehouseId()
        );

        int rows =
                wmsUserMapper.update(updateUser);

        if (rows != 1) {
            throw new BusinessException("修改用户失败");
        }
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enableUser(Long id) {

        changeStatus(id, 1);
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disableUser(Long id) {

        changeStatus(id, 0);
    }

    private void changeStatus(
            Long id,
            Integer status) {

        WmsUser user = wmsUserMapper.selectById(id);

        if (user == null) {
            throw new BusinessException("用户不存在，无法禁用");
        }

        if (status.equals(user.getStatus())) {
            return;
        }

        int rows = wmsUserMapper.updateStatus(id, status);

        if (rows != 1) {
            throw new BusinessException("修改用户状态失败");
        }
    }

    /**
     * Entity -> Response
     */
    private WmsUserResponse convertToResponse(WmsUser user) {

        WmsUserResponse response = new WmsUserResponse();

        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setMobile(user.getMobile());
        response.setEmail(user.getEmail());

        response.setDefaultWarehouseId(
                user.getDefaultWarehouseId()
        );

        response.setStatus(user.getStatus());

        response.setLastLoginTime(
                user.getLastLoginTime()
        );

        response.setCreatedTime(
                user.getCreatedTime()
        );

        response.setUpdatedTime(
                user.getUpdatedTime()
        );

        return response;
    }


}
