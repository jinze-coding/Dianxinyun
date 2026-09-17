package com.example.siteplatform.system.userimport.mapper;

import com.example.siteplatform.system.userimport.UserImportItem;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface UserImportItemMapper extends BaseMapper<UserImportItem> {
    @Select("SELECT i.*, i.excel_row AS rowNumber FROM system_user_import_item i WHERE batch_id=#{id} ORDER BY excel_row")
    List<UserImportItem> forBatch(Long id);
    @Select("SELECT i.*, i.excel_row AS rowNumber FROM system_user_import_item i WHERE user_id=#{id} ORDER BY id LIMIT 1")
    UserImportItem forUser(Long id);
    @Update("UPDATE system_user_import_item SET credential_cipher=NULL,download_until=NULL WHERE user_id=#{id}")
    int clearCredentials(Long id);
    @Update("UPDATE system_user_import_item i LEFT JOIN sys_user u ON u.id=i.user_id SET i.credential_cipher=NULL,i.download_until=NULL WHERE i.credential_cipher IS NOT NULL AND (i.download_until<=NOW() OR u.id IS NULL OR u.deleted=1 OR u.must_change_password=0 OR u.credential_version<>i.credential_version)")
    int purgeCredentials();
}
