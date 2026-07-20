package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.ProjectDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProjectDocumentMapper extends BaseMapper<ProjectDocument> {
    @Select("SELECT * FROM project_document WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ProjectDocument selectForUpdate(Long id);

    @Select("SELECT * FROM project_document WHERE id = #{id} AND deleted = 1 LIMIT 1")
    ProjectDocument selectDeletedById(Long id);

    @Select("SELECT * FROM project_document WHERE project_id = #{projectId} AND deleted = 1 ORDER BY update_time DESC")
    List<ProjectDocument> selectDeletedByProject(Long projectId);

    @Select("SELECT COUNT(*) FROM project_document WHERE folder_id = #{folderId}")
    long countAllByFolder(Long folderId);

    @Update("UPDATE project_document SET deleted = 0, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 1")
    int restoreById(Long id);

    @Delete("DELETE FROM project_document WHERE id = #{id}")
    int purgeById(Long id);
}
