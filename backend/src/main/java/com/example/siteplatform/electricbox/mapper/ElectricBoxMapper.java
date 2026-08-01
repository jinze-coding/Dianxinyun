package com.example.siteplatform.electricbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ElectricBoxMapper extends BaseMapper<ElectricBox> {
    @Select("SELECT * FROM electric_box WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ElectricBox selectByIdForUpdate(Long id);
}
