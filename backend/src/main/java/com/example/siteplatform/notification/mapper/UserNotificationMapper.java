package com.example.siteplatform.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.notification.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {
    @Insert("""
            INSERT IGNORE INTO user_notification
            (user_id, project_id, business_type, business_id, event_code, title, summary,
             route_code, route_params_json, is_read, dedup_key, create_time, update_time)
            VALUES
            (#{userId}, #{projectId}, #{businessType}, #{businessId}, #{eventCode}, #{title}, #{summary},
             #{routeCode}, #{routeParamsJson}, 0, #{dedupKey}, #{createTime}, #{updateTime})
            """)
    int insertIgnore(UserNotification notification);
    @Update("""
            UPDATE user_notification SET is_read = 1, read_time = #{readTime}, update_time = #{readTime}
            WHERE id = #{id} AND user_id = #{userId} AND is_read = 0
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId,
                 @Param("readTime") LocalDateTime readTime);

    @Update("""
            UPDATE user_notification SET is_read = 1, read_time = #{readTime}, update_time = #{readTime}
            WHERE user_id = #{userId} AND is_read = 0
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readTime") LocalDateTime readTime);

    @Update("""
            <script>
            UPDATE user_notification SET is_read = 1, read_time = #{readTime}, update_time = #{readTime}
            WHERE user_id = #{userId} AND is_read = 0
              AND
              <choose>
                <when test="includeGlobal and projectIds != null and projectIds.size() > 0">
                  (project_id IS NULL OR project_id IN
                  <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                    #{projectId}
                  </foreach>)
                </when>
                <when test="includeGlobal">
                  project_id IS NULL
                </when>
                <when test="projectIds != null and projectIds.size() > 0">
                  project_id IN
                  <foreach collection="projectIds" item="projectId" open="(" separator="," close=")">
                    #{projectId}
                  </foreach>
                </when>
                <otherwise>1 = 0</otherwise>
              </choose>
            </script>
            """)
    int markAllReadInScope(@Param("userId") Long userId,
                           @Param("projectIds") List<Long> projectIds,
                           @Param("includeGlobal") boolean includeGlobal,
                           @Param("readTime") LocalDateTime readTime);
}
