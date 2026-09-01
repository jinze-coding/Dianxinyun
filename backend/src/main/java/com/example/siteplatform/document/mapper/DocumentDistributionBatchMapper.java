package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.DocumentDistributionBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentDistributionBatchMapper extends BaseMapper<DocumentDistributionBatch> {
    @Select("SELECT * FROM document_distribution_batch WHERE id = #{id} FOR UPDATE")
    DocumentDistributionBatch selectForUpdate(Long id);

    @Select("SELECT * FROM document_distribution_batch WHERE qr_scene_digest = #{digest} LIMIT 1")
    DocumentDistributionBatch selectBySceneDigest(String digest);
}
