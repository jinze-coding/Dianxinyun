package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.DocumentIncomingItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentIncomingItemMapper extends BaseMapper<DocumentIncomingItem> {
    @Delete("DELETE FROM document_incoming_item WHERE batch_id = #{batchId} AND status = 'PENDING'")
    int deleteDraftItems(Long batchId);
}
