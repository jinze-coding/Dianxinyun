package com.example.siteplatform.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.file.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface FileResourceMapper extends BaseMapper<FileResource> {
    @Delete("DELETE FROM file_resource WHERE id = #{id}")
    int purgeById(Long id);
}
