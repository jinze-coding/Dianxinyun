package com.example.siteplatform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.workflow.entity.WorkflowApprovalTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkflowApprovalTaskMapper extends BaseMapper<WorkflowApprovalTask> {
    @Update("""
            UPDATE workflow_approval_task
            SET status = #{targetStatus}, decision_user_id = #{userId}, decision_user_name = #{userName},
                decision_opinion = #{opinion}, decision_time = #{decisionTime},
                version = version + 1, update_time = #{decisionTime}
            WHERE id = #{id} AND assignee_user_id = #{userId}
              AND status = 'PENDING' AND version = #{expectedVersion}
            """)
    int decide(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
               @Param("targetStatus") String targetStatus, @Param("userId") Long userId,
               @Param("userName") String userName, @Param("opinion") String opinion,
               @Param("decisionTime") LocalDateTime decisionTime);

    @Update("""
            UPDATE workflow_approval_task
            SET status = 'TRANSFERRED', decision_user_id = #{userId}, decision_user_name = #{userName},
                decision_opinion = #{reason}, decision_time = #{decisionTime},
                version = version + 1, update_time = #{decisionTime}
            WHERE id = #{id} AND assignee_user_id = #{userId}
              AND status = 'PENDING' AND version = #{expectedVersion}
            """)
    int transfer(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                 @Param("userId") Long userId, @Param("userName") String userName,
                 @Param("reason") String reason, @Param("decisionTime") LocalDateTime decisionTime);

    @Update("""
            UPDATE workflow_approval_task
            SET status = 'CANCELLED', version = version + 1, update_time = #{updateTime}
            WHERE instance_id = #{instanceId} AND status = 'PENDING'
            """)
    int cancelPendingByInstance(@Param("instanceId") Long instanceId,
                                @Param("updateTime") LocalDateTime updateTime);
}
