package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommitteeAttachmentMapper extends BaseMapper<CommitteeAttachment> {
    @Select("SELECT * FROM safety_committee_attachment WHERE id = #{id} FOR UPDATE")
    CommitteeAttachment lock(@Param("id") Long id);
}
