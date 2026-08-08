package com.example.siteplatform.seal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.siteplatform.seal.entity.SealApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface SealApplicationMapper extends BaseMapper<SealApplication> {
    @Select("""
            SELECT * FROM seal_application
            WHERE applicant_id = #{applicantId} AND request_key = #{requestKey} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    SealApplication selectByRequestKeyForUpdate(@Param("applicantId") Long applicantId,
                                                @Param("requestKey") String requestKey);

    @Select("SELECT * FROM seal_application WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SealApplication selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE seal_application
            SET department_name = #{departmentName}, purpose = #{purpose},
                version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND applicant_id = #{applicantId}
              AND status = 'DRAFT' AND version = #{expectedVersion} AND deleted = 0
            """)
    int updateDraft(@Param("id") Long id, @Param("applicantId") Long applicantId,
                    @Param("expectedVersion") Integer expectedVersion,
                    @Param("departmentName") String departmentName,
                    @Param("purpose") String purpose, @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            UPDATE seal_application
            SET application_no = #{applicationNo}, application_date = #{applicationDate},
                department_name = #{departmentName}, seal_name = #{sealName}, company_name = #{companyName},
                applicant_name = #{applicantName}, applicant_phone = #{applicantPhone},
                status = 'PENDING_APPROVAL', approval_instance_id = #{instanceId},
                submit_time = #{submitTime}, version = version + 1, update_time = #{submitTime}
            WHERE id = #{id} AND status = 'DRAFT' AND version = #{expectedVersion} AND deleted = 0
            """)
    int submit(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
               @Param("applicationNo") String applicationNo,
               @Param("applicationDate") java.time.LocalDate applicationDate,
               @Param("departmentName") String departmentName,
               @Param("sealName") String sealName,
               @Param("companyName") String companyName,
               @Param("applicantName") String applicantName,
               @Param("applicantPhone") String applicantPhone,
               @Param("instanceId") Long instanceId, @Param("submitTime") LocalDateTime submitTime);

    @Update("""
            UPDATE seal_application
            SET status = #{targetStatus}, approver_id = #{approverId}, approver_name = #{approverName},
                approval_opinion = #{opinion}, approval_time = #{approvalTime},
                version = version + 1, update_time = #{approvalTime}
            WHERE id = #{id} AND status = 'PENDING_APPROVAL'
              AND version = #{expectedVersion} AND deleted = 0
            """)
    int decide(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
               @Param("targetStatus") String targetStatus, @Param("approverId") Long approverId,
               @Param("approverName") String approverName, @Param("opinion") String opinion,
               @Param("approvalTime") LocalDateTime approvalTime);

    @Update("""
            UPDATE seal_application
            SET status = 'WITHDRAWN', version = version + 1, update_time = #{updateTime}
            WHERE id = #{id} AND status = 'PENDING_APPROVAL'
              AND version = #{expectedVersion} AND deleted = 0
            """)
    int withdraw(@Param("id") Long id, @Param("expectedVersion") Integer expectedVersion,
                 @Param("updateTime") LocalDateTime updateTime);
}
