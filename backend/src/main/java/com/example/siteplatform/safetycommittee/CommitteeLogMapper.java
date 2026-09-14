package com.example.siteplatform.safetycommittee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CommitteeLogMapper extends BaseMapper<CommitteeLog> {
    @Select("SELECT * FROM safety_committee_log WHERE id = #{id} FOR UPDATE")
    CommitteeLog lock(@Param("id") Long id);
}
