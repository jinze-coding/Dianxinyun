package com.example.siteplatform.seal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.seal.entity.SealApplicationFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SealApplicationFileMapper extends BaseMapper<SealApplicationFile> {
    @Update("""
            UPDATE seal_application_file
            SET archived_document_id = #{documentId}, archived_version_id = #{versionId},
                archived_time = #{archivedTime}, update_time = #{archivedTime}
            WHERE id = #{id} AND application_id = #{applicationId}
              AND archived_document_id IS NULL AND deleted = 0
            """)
    int markArchived(@Param("id") Long id, @Param("applicationId") Long applicationId,
                     @Param("documentId") Long documentId, @Param("versionId") Long versionId,
                     @Param("archivedTime") LocalDateTime archivedTime);
}
