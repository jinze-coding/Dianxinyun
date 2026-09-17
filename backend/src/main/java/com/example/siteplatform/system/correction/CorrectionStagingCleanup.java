package com.example.siteplatform.system.correction;

import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.*;
import static com.example.siteplatform.system.correction.CorrectionRepository.*;

@Component
@RequiredArgsConstructor
public class CorrectionStagingCleanup {
    private final CorrectionRepository repo;
    private final FileResourceMapper files;
    private final FileStorageManager storage;
    @Scheduled(initialDelay=900000,fixedDelay=3600000)
    @Transactional
    public void expiredUploads() {
        var expired=repo.rows("SELECT id,file_id FROM sys_data_correction_attachment WHERE phase='PENDING' AND expires_at<? ORDER BY id LIMIT 100 FOR UPDATE",now());
        for(var row:expired) {
            long id=id(row.get("file_id"));var file=files.selectById(id);
            repo.jdbc().update("UPDATE sys_data_correction_attachment SET phase='EXPIRED' WHERE id=? AND phase='PENDING'",row.get("id"));
            if(file==null)continue;
            if(!"DATA_CORRECTION_PENDING".equals(file.getBusinessType()))continue;
            repo.jdbc().update("UPDATE file_resource SET deleted=1,status='PENDING_DELETE',update_time=? WHERE id=? AND business_type='DATA_CORRECTION_PENDING'",now(),id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){storage.deleteQuietly(file.getStorageProvider(),file.getStorageKey());}});
        }
    }
}
