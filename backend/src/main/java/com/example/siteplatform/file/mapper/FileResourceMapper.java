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
                  'QUALITY_REVIEW_PENDING'
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
                  'QUALITY_REVIEW_PENDING'
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
                  'QUALITY_REVIEW_PENDING'
              )
              AND create_time < #{cutoff}
            """)
    int purgeClaimedQualityStagingFile(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff);
}
