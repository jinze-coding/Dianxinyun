package com.example.siteplatform.inspection.general.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionEventOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GeneralInspectionEventOutboxMapper extends BaseMapper<GeneralInspectionEventOutbox> {
    @Update("UPDATE general_inspection_event_outbox SET status='RUNNING', update_time=CURRENT_TIMESTAMP " +
            "WHERE id=#{id} AND status IN ('PENDING','FAILED') AND retry_count < 5")
    int claim(@Param("id") Long id);

    @Update("UPDATE general_inspection_event_outbox SET status='PUBLISHED', published_time=CURRENT_TIMESTAMP, " +
            "last_error=NULL, update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND status='RUNNING'")
    int markPublished(@Param("id") Long id);

    @Update("UPDATE general_inspection_event_outbox SET status='FAILED', retry_count=retry_count+1, " +
            "last_error=#{error}, update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND status='RUNNING'")
    int markFailed(@Param("id") Long id, @Param("error") String error);

    @Update("UPDATE general_inspection_event_outbox SET status='FAILED', retry_count=retry_count+1, " +
            "last_error='发布进程中断，等待补偿', update_time=CURRENT_TIMESTAMP " +
            "WHERE status='RUNNING' AND update_time < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE)")
    int recoverStuck();
}
