package com.example.siteplatform.seal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.seal.entity.SealDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SealDefinitionMapper extends BaseMapper<SealDefinition> {
    @Select("SELECT * FROM seal_definition WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SealDefinition selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE seal_definition
            SET seal_code = #{sealCode}, seal_name = #{sealName}, seal_type = #{sealType},
                company_name = #{companyName}, status = #{status}, sort_order = #{sortOrder},
                updated_by = #{updatedBy}, version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
            """)
    int updateDefinition(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                         @Param("sealCode") String sealCode, @Param("sealName") String sealName,
                         @Param("sealType") String sealType, @Param("companyName") String companyName,
                         @Param("status") String status, @Param("sortOrder") Integer sortOrder,
                         @Param("updatedBy") Long updatedBy, @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE seal_definition
            SET scene_token_hash = #{sceneTokenHash}, scene_token_encrypted = #{sceneTokenEncrypted},
                qr_version = qr_version + 1,
                updated_by = #{updatedBy}, version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
            """)
    int rotateScene(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                    @Param("sceneTokenHash") String sceneTokenHash,
                    @Param("sceneTokenEncrypted") String sceneTokenEncrypted,
                    @Param("updatedBy") Long updatedBy,
                    @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE seal_definition
            SET qr_status = #{qrStatus}, updated_by = #{updatedBy}, version = version + 1,
                update_time = #{updateTime}
            WHERE id = #{id} AND version = #{expectedVersion} AND deleted = 0
            """)
    int updateQrStatus(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                       @Param("qrStatus") String qrStatus, @Param("updatedBy") Long updatedBy,
                       @Param("updateTime") LocalDateTime updateTime);
}
