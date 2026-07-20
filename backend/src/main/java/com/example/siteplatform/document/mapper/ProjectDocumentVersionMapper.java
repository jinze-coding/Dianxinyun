package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectDocumentVersionMapper extends BaseMapper<ProjectDocumentVersion> {
    @Select("SELECT COALESCE(MAX(version_no), 0) FROM project_document_version WHERE document_id = #{documentId}")
    int selectMaxVersionNo(Long documentId);

    @Delete("DELETE FROM project_document_version WHERE document_id = #{documentId}")
    int deleteByDocumentId(Long documentId);
}
