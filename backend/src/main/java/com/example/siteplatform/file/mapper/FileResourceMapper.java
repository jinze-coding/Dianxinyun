package com.example.siteplatform.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.file.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileResourceMapper extends BaseMapper<FileResource> {
    @Select("""
            <script>
            SELECT * FROM file_resource
            WHERE deleted = 0 AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY id ASC
            FOR UPDATE
            </script>
            """)
    List<FileResource> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    @Update("""
            UPDATE file_resource SET status = 'ARCHIVED', update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = 'PROJECT_PROFILE_IMAGE' AND business_id = #{projectId}
              AND status = 'UPLOADED' AND deleted = 0
            """)
    int archiveProjectProfileImage(@Param("id") Long id, @Param("projectId") Long projectId);

    @Update("""
            UPDATE file_resource
            SET business_type = 'PROJECT_PROFILE_IMAGE', business_id = #{projectId}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = 'PROJECT_PROFILE_IMAGE_PENDING' AND business_id IS NULL
              AND status = 'UPLOADED' AND deleted = 0
            """)
    int bindPendingProjectProfileImage(@Param("id") Long id, @Param("projectId") Long projectId);

    @Update("""
            UPDATE file_resource SET status = 'UPLOADED', update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = 'PROJECT_PROFILE_IMAGE' AND business_id = #{projectId}
              AND status = 'ARCHIVED' AND deleted = 0
            """)
    int restoreProjectProfileImage(@Param("id") Long id, @Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM file_resource
            WHERE project_id = #{projectId}
              AND business_type = 'PROJECT_ROUTE_IMAGE'
              AND business_id = #{projectId}
              AND status = 'UPLOADED' AND deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    FileResource selectActiveProjectRouteImage(@Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM file_resource
            WHERE project_id = #{projectId}
              AND business_type = 'PROJECT_ROUTE_IMAGE'
              AND business_id = #{projectId}
              AND status = 'UPLOADED' AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<FileResource> selectActiveProjectRouteImagesForUpdate(@Param("projectId") Long projectId);

    @Update("""
            UPDATE file_resource SET status = 'ARCHIVED', update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = 'PROJECT_ROUTE_IMAGE' AND business_id = #{projectId}
              AND status = 'UPLOADED' AND deleted = 0
            """)
    int archiveProjectRouteImage(@Param("id") Long id, @Param("projectId") Long projectId);

    @Update("""
            UPDATE file_resource
            SET business_type = 'PROJECT_ROUTE_IMAGE', business_id = #{projectId}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = 'PROJECT_ROUTE_IMAGE_PENDING' AND business_id IS NULL
              AND status = 'UPLOADED' AND deleted = 0
            """)
    int bindPendingProjectRouteImage(@Param("id") Long id, @Param("projectId") Long projectId);

    @Delete("DELETE FROM file_resource WHERE id = #{id}")
    int purgeById(Long id);

    @Select("""
            SELECT *
            FROM file_resource
            WHERE deleted IN (0, 1)
              AND business_id IS NULL
              AND business_type IN (
                  'QUALITY_PENDING',
                  'QUALITY_RECTIFICATION_PENDING',
                  'QUALITY_REVIEW_PENDING',
                  'QUALITY_WEEKLY_PENDING',
                  'QUALITY_WEEKLY_ITEM_PENDING',
                  'INSPECTION_CUSTOM_POINT_PENDING',
                  'INSPECTION_CUSTOM_TASK_PENDING',
                  'INSPECTION_CUSTOM_RECTIFICATION_PENDING',
                  'EDGE_INSPECTION_TASK_PENDING',
                  'EDGE_INSPECTION_RECTIFICATION_PENDING'
              )
              AND create_time < #{cutoff}
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<FileResource> selectExpiredQualityStagingFiles(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    @Update("""
            UPDATE file_resource
            SET deleted = 1,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = 0
              AND business_id IS NULL
              AND business_type IN (
                  'QUALITY_PENDING',
                  'QUALITY_RECTIFICATION_PENDING',
                  'QUALITY_REVIEW_PENDING',
                  'QUALITY_WEEKLY_PENDING',
                  'QUALITY_WEEKLY_ITEM_PENDING',
                  'INSPECTION_CUSTOM_POINT_PENDING',
                  'INSPECTION_CUSTOM_TASK_PENDING',
                  'INSPECTION_CUSTOM_RECTIFICATION_PENDING',
                  'EDGE_INSPECTION_TASK_PENDING',
                  'EDGE_INSPECTION_RECTIFICATION_PENDING'
              )
              AND create_time < #{cutoff}
            """)
    int claimExpiredQualityStagingFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);

    @Delete("""
            DELETE FROM file_resource
            WHERE id = #{id}
              AND deleted = 1
              AND business_id IS NULL
              AND business_type IN (
                  'QUALITY_PENDING',
                  'QUALITY_RECTIFICATION_PENDING',
                  'QUALITY_REVIEW_PENDING',
                  'QUALITY_WEEKLY_PENDING',
                  'QUALITY_WEEKLY_ITEM_PENDING',
                  'INSPECTION_CUSTOM_POINT_PENDING',
                  'INSPECTION_CUSTOM_TASK_PENDING',
                  'INSPECTION_CUSTOM_RECTIFICATION_PENDING',
                  'EDGE_INSPECTION_TASK_PENDING',
                  'EDGE_INSPECTION_RECTIFICATION_PENDING'
              )
              AND create_time < #{cutoff}
            """)
    int purgeClaimedQualityStagingFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);

    @Select("""
            SELECT * FROM file_resource
            WHERE project_id = #{projectId}
              AND business_type = #{businessType}
              AND business_id = #{businessId}
              AND deleted = 0
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<FileResource> selectWeeklyDraftFilesForUpdate(
            @Param("projectId") Long projectId,
            @Param("businessType") String businessType,
            @Param("businessId") Long businessId);

    @Update("""
            UPDATE file_resource
            SET business_type = #{draftType}, business_id = #{draftBusinessId}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = #{pendingType} AND business_id IS NULL
              AND uploader_id = #{uploaderId} AND deleted = 0 AND status = 'UPLOADED'
            """)
    int bindWeeklyPendingFile(
            @Param("id") Long id,
            @Param("projectId") Long projectId,
            @Param("pendingType") String pendingType,
            @Param("draftType") String draftType,
            @Param("draftBusinessId") Long draftBusinessId,
            @Param("uploaderId") Long uploaderId);

    @Update("""
            UPDATE file_resource
            SET business_type = #{targetType}, business_id = #{targetBusinessId}, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = #{draftType} AND business_id = #{draftBusinessId}
              AND deleted = 0 AND status = 'UPLOADED'
            """)
    int transferWeeklyDraftFile(
            @Param("id") Long id,
            @Param("projectId") Long projectId,
            @Param("draftType") String draftType,
            @Param("draftBusinessId") Long draftBusinessId,
            @Param("targetType") String targetType,
            @Param("targetBusinessId") Long targetBusinessId);

    @Update("""
            UPDATE file_resource
            SET deleted = 1, status = 'PENDING_DELETE', update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND project_id = #{projectId}
              AND business_type = #{draftType} AND business_id = #{draftBusinessId}
              AND deleted = 0
            """)
    int stageWeeklyDraftFileForDelete(
            @Param("id") Long id,
            @Param("projectId") Long projectId,
            @Param("draftType") String draftType,
            @Param("draftBusinessId") Long draftBusinessId);

    @Update("""
            UPDATE file_resource SET deleted = 1, status = 'DELETE_FAILED', update_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = 1 AND status = 'PENDING_DELETE'
            """)
    int markPhysicalDeleteFailed(@Param("id") Long id);

    @Select("""
            SELECT * FROM file_resource
            WHERE deleted = 1
              AND status = 'PENDING_DELETE'
              AND update_time < #{cutoff}
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<FileResource> selectStalePendingDeleteFiles(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    @Delete("""
            DELETE FROM file_resource
            WHERE id = #{id}
              AND deleted = 1
              AND status = 'PENDING_DELETE'
              AND update_time < #{cutoff}
            """)
    int purgeStalePendingDeleteFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);

    @Select("""
            SELECT * FROM file_resource
            WHERE deleted IN (0, 1)
              AND business_id IS NULL
              AND business_type IN ('PROJECT_PROFILE_IMAGE_PENDING', 'PROJECT_ROUTE_IMAGE_PENDING')
              AND create_time < #{cutoff}
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<FileResource> selectExpiredProjectProfileStagingFiles(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    @Update("""
            UPDATE file_resource
            SET deleted = 1, update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted = 0
              AND business_id IS NULL
              AND business_type IN ('PROJECT_PROFILE_IMAGE_PENDING', 'PROJECT_ROUTE_IMAGE_PENDING')
              AND create_time < #{cutoff}
            """)
    int claimExpiredProjectProfileStagingFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);

    @Delete("""
            DELETE FROM file_resource
            WHERE id = #{id}
              AND deleted = 1
              AND business_id IS NULL
              AND business_type IN ('PROJECT_PROFILE_IMAGE_PENDING', 'PROJECT_ROUTE_IMAGE_PENDING')
              AND create_time < #{cutoff}
            """)
    int purgeClaimedProjectProfileStagingFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);
}
