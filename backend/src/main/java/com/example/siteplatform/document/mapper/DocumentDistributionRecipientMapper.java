package com.example.siteplatform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.document.entity.DocumentDistributionRecipient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentDistributionRecipientMapper extends BaseMapper<DocumentDistributionRecipient> {
    @Select("SELECT * FROM document_distribution_recipient WHERE id = #{id} FOR UPDATE")
    DocumentDistributionRecipient selectForUpdate(Long id);
}
