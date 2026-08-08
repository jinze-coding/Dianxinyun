package com.example.siteplatform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowApprovalConfigMapper extends BaseMapper<WorkflowApprovalConfig> {
    @Select("""
            SELECT * FROM workflow_approval_config
            WHERE business_code = #{businessCode} AND project_id = #{projectId} AND seal_id = #{sealId}
            LIMIT 1 FOR UPDATE
            """)
    WorkflowApprovalConfig selectForUpdate(@Param("businessCode") String businessCode,
                                           @Param("projectId") Long projectId,
                                           @Param("sealId") Long sealId);
}
