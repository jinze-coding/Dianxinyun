package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommitteeRecordMapper extends BaseMapper<CommitteeRecord> {
    @Select("SELECT * FROM safety_committee_record WHERE id = #{id} FOR UPDATE")
    CommitteeRecord lock(@Param("id") Long id);
}
