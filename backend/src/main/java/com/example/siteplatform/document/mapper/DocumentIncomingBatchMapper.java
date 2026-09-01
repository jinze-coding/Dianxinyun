package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentIncomingBatchMapper extends BaseMapper<DocumentIncomingBatch> {
    @Select("SELECT * FROM document_incoming_batch WHERE id = #{id} FOR UPDATE")
    DocumentIncomingBatch selectForUpdate(Long id);
}
