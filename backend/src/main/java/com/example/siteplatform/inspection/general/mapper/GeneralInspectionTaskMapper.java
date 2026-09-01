package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface GeneralInspectionTaskMapper extends BaseMapper<GeneralInspectionTask> {
    @Select("SELECT * FROM general_inspection_task WHERE id=#{id} FOR UPDATE")
    GeneralInspectionTask selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM general_inspection_task WHERE point_id=#{pointId} AND status='PENDING' FOR UPDATE")
    List<GeneralInspectionTask> selectPendingByPointForUpdate(@Param("pointId") Long pointId);

    @Select("""
            SELECT t.id
            FROM general_inspection_task t
            WHERE t.plan_id = #{planId}
              AND t.status = 'PENDING'
              AND t.point_type_code IS NOT NULL
              AND t.assignee_id IS NOT NULL
              AND t.due_time > #{effectiveTime}
              AND t.due_time <= #{now}
              AND NOT EXISTS (
                SELECT 1 FROM user_notification n
                WHERE n.dedup_key = CONCAT('reminder:edge:', t.id)
            )
            ORDER BY t.due_time ASC, t.id ASC
            """)
    List<Long> selectPendingReminderCandidateIds(@Param("planId") Long planId,
                                                 @Param("effectiveTime") LocalDateTime effectiveTime,
                                                 @Param("now") LocalDateTime now);
}
