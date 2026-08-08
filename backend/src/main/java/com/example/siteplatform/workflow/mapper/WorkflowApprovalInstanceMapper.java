package com.example.siteplatform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.workflow.entity.WorkflowApprovalInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkflowApprovalInstanceMapper extends BaseMapper<WorkflowApprovalInstance> {
    @Update("""
            UPDATE workflow_approval_instance
            SET status = #{targetStatus}, decision_user_id = #{userId}, decision_user_name = #{userName},
                decision_opinion = #{opinion}, decision_time = #{decisionTime},
                version = version + 1, update_time = #{decisionTime}
            WHERE id = #{id} AND status = 'PENDING' AND version = #{expectedVersion}
            """)
    int decide(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
               @Param("targetStatus") String targetStatus, @Param("userId") Long userId,
               @Param("userName") String userName, @Param("opinion") String opinion,
               @Param("decisionTime") LocalDateTime decisionTime);

    @Update("""
            UPDATE workflow_approval_instance
            SET status = 'WITHDRAWN', version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'PENDING' AND version = #{expectedVersion}
            """)
    int withdraw(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                 @Param("updateTime") LocalDateTime updateTime);
}
