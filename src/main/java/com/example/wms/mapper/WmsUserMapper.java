package com.example.wms.mapper;

import com.example.wms.entity.WmsUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface WmsUserMapper {

    /**
     * 根据主键ID查询用户
     */
    WmsUser selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     *
     * 后续登录功能主要使用这个方法
     */
    WmsUser selectByUsername(@Param("username") String username);

    /**
     * 新增用户
     *
     * 插入成功返回影响行数
     */
    int insert(WmsUser user);

    /**
     * 更新用户基本信息
     */
    int update(WmsUser user);

    /**
     * 更新用户状态
     *
     * 0：禁用
     * 1：启用
     */
    int updateStatus(
            @Param("id") Long id,
            @Param("status") Integer status
    );

    /**
     * 更新最后登录时间
     */
    int updateLastLoginTime(
            @Param("id") Long id,
            @Param("lastLoginTime") LocalDateTime lastLoginTime
    );
}
