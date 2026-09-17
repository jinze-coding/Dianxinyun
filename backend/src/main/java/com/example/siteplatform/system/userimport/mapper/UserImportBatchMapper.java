package com.example.siteplatform.system.userimport.mapper;

import com.example.siteplatform.system.userimport.UserImportBatch;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserImportBatchMapper extends BaseMapper<UserImportBatch> {
    @Select("SELECT * FROM system_user_import_batch WHERE id=#{id} FOR UPDATE")
    UserImportBatch lock(Long id);
    @Select("SELECT id FROM system_user_import_batch WHERE status='QUEUED' OR (status='PROCESSING' AND lease_until<NOW()) ORDER BY id LIMIT 1")
    Long nextJob();
    @Update("UPDATE system_user_import_batch SET status='PROCESSING',lease_token=#{lease},lease_until=DATE_ADD(NOW(),INTERVAL 2 MINUTE),prepared_count=0,updated_at=NOW() WHERE id=#{id} AND (status='QUEUED' OR (status='PROCESSING' AND lease_until<NOW()))")
    int claim(@Param("id") Long id, @Param("lease") String lease);
    @Update("UPDATE system_user_import_batch SET prepared_count=#{count},lease_until=DATE_ADD(NOW(),INTERVAL 2 MINUTE),updated_at=NOW() WHERE id=#{id} AND status='PROCESSING' AND lease_token=#{lease}")
    int heartbeat(@Param("id") Long id, @Param("lease") String lease, @Param("count") int count);
    @Update("UPDATE system_user_import_batch SET status=#{status},message=#{message},temporary_password_cipher=NULL,lease_token=NULL,lease_until=NULL,updated_at=NOW() WHERE id=#{id} AND status='PROCESSING' AND lease_token=#{lease}")
    int fail(@Param("id") Long id, @Param("lease") String lease, @Param("status") String status, @Param("message") String message);
    @Update("UPDATE system_user_import_batch SET temporary_password_cipher=NULL WHERE temporary_password_cipher IS NOT NULL AND status NOT IN ('QUEUED','PROCESSING')")
    int purgeTerminalCredentials();
}
