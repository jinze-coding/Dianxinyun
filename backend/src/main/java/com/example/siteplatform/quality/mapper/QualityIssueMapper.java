package com.example.siteplatform.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.quality.entity.QualityIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QualityIssueMapper extends BaseMapper<QualityIssue> {

    @Update("""
            UPDATE quality_issue
            SET rectification_description = #{description},
                rectification_photo_file_ids = #{photoFileIds},
                rectified_time = #{rectifiedTime},
                status = #{targetStatus},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateRectification(@Param("id") Long id,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("expectedVersion") Integer expectedVersion,
                            @Param("targetStatus") String targetStatus,
                            @Param("description") String description,
                            @Param("photoFileIds") String photoFileIds,
                            @Param("rectifiedTime") LocalDateTime rectifiedTime,
                            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE quality_issue
            SET status = #{targetStatus},
                reviewer_id = #{reviewerId},
                reviewer_name = #{reviewerName},
                review_comment = #{reviewComment},
                review_time = #{reviewTime},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateReview(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("expectedVersion") Integer expectedVersion,
                     @Param("targetStatus") String targetStatus,
                     @Param("reviewerId") Long reviewerId,
                     @Param("reviewerName") String reviewerName,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime,
                     @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE quality_issue
            SET assignee_id = #{assigneeId},
                assignee_name = #{assigneeName},
                deadline = #{deadline},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateAssignment(@Param("id") Long id,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("expectedVersion") Integer expectedVersion,
                         @Param("assigneeId") Long assigneeId,
                         @Param("assigneeName") String assigneeName,
                         @Param("deadline") LocalDate deadline,
                         @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE quality_issue
            SET status = #{targetStatus},
                version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{id}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("expectedVersion") Integer expectedVersion,
                     @Param("targetStatus") String targetStatus,
                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * 先找出当前项目的有效成员和平台管理员，再由服务层复用正式权限模型，
     * 过滤出在当前项目具备 quality.review 权限的复查人。
     */
    @Select("""
            SELECT DISTINCT u.id
            FROM sys_user u
            WHERE u.status = 1
              AND u.deleted = 0
              AND (
                    EXISTS (
                        SELECT 1
                        FROM sys_user_project sup
                        WHERE sup.user_id = u.id
                          AND sup.project_id = #{projectId}
                          AND sup.status = 'ACTIVE'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM sys_user_role ur
                        INNER JOIN sys_role r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id
                          AND r.role_code = 'PLATFORM_ADMIN'
                          AND r.scope_type = 'PLATFORM'
                          AND r.enabled = 1
                          AND r.deleted = 0
                    )
                  )
            ORDER BY u.id
            """)
    List<Long> selectPotentialReviewerIds(@Param("projectId") Long projectId);
}
