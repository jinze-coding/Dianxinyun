package com.example.siteplatform.inspection.general.mapper;

import com.example.siteplatform.inspection.general.vo.EdgeInspectionWorkspaceSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface EdgeInspectionWorkspaceMapper {

    @Select("""
            SELECT
                (SELECT COUNT(*)
                   FROM general_inspection_point p
                  WHERE p.project_id = #{projectId}
                    AND p.deleted = 0
                    AND p.status = 'ACTIVE'
                    AND p.point_type_code IS NOT NULL) AS enabled_point_count,
                COUNT(CASE WHEN t.occurrence_date = #{today} THEN 1 END) AS my_today_due_count,
                COUNT(CASE WHEN t.occurrence_date = #{today}
                            AND t.status = 'PENDING' THEN 1 END) AS my_today_pending_count,
                COUNT(CASE WHEN t.occurrence_date = #{today}
                            AND t.submitted_time IS NOT NULL THEN 1 END) AS my_today_submitted_count,
                COUNT(CASE WHEN t.occurrence_date = #{today}
                            AND t.status = 'CANCELLED' THEN 1 END) AS my_today_cancelled_count,
                COUNT(CASE WHEN t.status = 'PENDING'
                            AND t.due_time < #{now} THEN 1 END) AS my_overdue_count,
                (SELECT COUNT(DISTINCT r.task_id)
                   FROM general_inspection_rectification r
                   JOIN general_inspection_task rt ON rt.id = r.task_id
                  WHERE r.project_id = #{projectId}
                    AND r.assignee_id = #{userId}
                    AND r.status IN ('PENDING', 'REJECTED')
                    AND rt.project_id = #{projectId}
                    AND rt.point_type_code IS NOT NULL) AS my_rectification_pending_count,
                (SELECT COUNT(DISTINCT r.task_id)
                   FROM general_inspection_rectification r
                   JOIN general_inspection_task rt ON rt.id = r.task_id
                  WHERE r.project_id = #{projectId}
                    AND r.reviewer_id = #{userId}
                    AND r.status = 'COMPLETED'
                    AND rt.project_id = #{projectId}
                    AND rt.point_type_code IS NOT NULL) AS my_review_pending_count
            FROM general_inspection_task t
            WHERE t.project_id = #{projectId}
              AND t.assignee_id = #{userId}
              AND t.point_type_code IS NOT NULL
            """)
    EdgeInspectionWorkspaceSummaryVO selectSummary(@Param("projectId") Long projectId,
                                                    @Param("userId") Long userId,
                                                    @Param("today") LocalDate today,
                                                    @Param("now") LocalDateTime now);
}
