package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.DocumentFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentFolderMapper extends BaseMapper<DocumentFolder> {
    @Select("SELECT COUNT(*) FROM document_folder WHERE parent_id = #{folderId} AND deleted = 0")
    long countAllChildren(Long folderId);
}
