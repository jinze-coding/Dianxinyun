package com.example.siteplatform.electricbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ElectricBoxMapper extends BaseMapper<ElectricBox> {
    @Select("SELECT * FROM electric_box WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ElectricBox selectByIdForUpdate(Long id);

    @Select("""
            SELECT box.*
            FROM electric_box box
            WHERE box.project_id = #{projectId}
              AND box.status = 'ACTIVE'
              AND box.deleted = 0
              AND (box.create_time IS NULL OR DATE(box.create_time) <= #{date})
              AND COALESCE((
                SELECT CASE
                    WHEN scope.included = 1
                     AND (scope.end_date IS NULL OR scope.end_date >= #{date})
                    THEN 1 ELSE 0 END
                FROM electric_box_inspection_scope scope
                WHERE scope.electric_box_id = box.id
                  AND scope.effective_date <= #{date}
                ORDER BY scope.effective_date DESC, scope.id DESC
                LIMIT 1
              ), 1) = 1
              AND NOT EXISTS (
                SELECT 1 FROM inspection_record record
                WHERE record.project_id = box.project_id
                  AND record.electric_box_id = box.id
                  AND record.source = 'ELECTRICIAN_DAILY'
                  AND record.check_date = #{date}
                  AND record.status <> 'DRAFT'
                  AND record.deleted = 0
              )
              AND NOT EXISTS (
                SELECT 1 FROM user_notification notification
                WHERE notification.dedup_key = CONCAT('reminder:ebox:', box.id, ':', #{date})
              )
            ORDER BY box.id
            """)
    List<ElectricBox> selectPendingSubmissionReminderBoxes(
            @Param("projectId") Long projectId,
            @Param("date") LocalDate date);
}
