package com.example.siteplatform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT project_id
            FROM sys_user_project
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            """)
    List<Long> selectProjectIdsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id
            FROM sys_role
            WHERE role_code = #{roleCode}
              AND deleted = 0
            LIMIT 1
            """)
    Long selectRoleIdByCode(@Param("roleCode") String roleCode);

    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
