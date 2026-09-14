package com.example.siteplatform.safetycommittee;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommitteeServiceTest {
    CommitteeRecordMapper records=mock(CommitteeRecordMapper.class);
    CommitteeAttachmentMapper attachments=mock(CommitteeAttachmentMapper.class);
    CommitteeLogMapper logs=mock(CommitteeLogMapper.class);
    ProjectPermissionService permissions=mock(ProjectPermissionService.class);
    ProjectInfoMapper projects=mock(ProjectInfoMapper.class);
    FileResourceMapper files=mock(FileResourceMapper.class);
    FileStorageManager storage=mock(FileStorageManager.class);
    CommitteeService service; SysUser user; CommitteeRecord record;
    @BeforeEach void setup(){
        for(Class<?> type:List.of(CommitteeRecord.class,CommitteeAttachment.class,CommitteeLog.class,FileResource.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"committee"),type);
        service=new CommitteeService(records,attachments,logs,permissions,projects,files,storage,new ObjectMapper().findAndRegisterModules());
        user=new SysUser();user.setId(7L);user.setRealName("现场检查人");
        record=new CommitteeRecord();record.setId(10L);record.setProjectId(3L);record.setInspectorId(7L);record.setInspectorName("原检查人快照");record.setInspectedAt(LocalDateTime.of(2026,9,13,10,20,30));record.setCategory("其他");record.setConclusion("");record.setVersion(1);
        when(records.selectById(10L)).thenReturn(record);when(records.lock(10L)).thenReturn(record);
        when(projects.selectByIdForUpdate(3L)).thenReturn(new ProjectInfo());when(projects.selectById(3L)).thenReturn(new ProjectInfo());
        when(records.updateById(any(CommitteeRecord.class))).thenReturn(1);when(logs.insert(any(CommitteeLog.class))).thenReturn(1);
    }
    @Test void exactCategoriesOptionalConclusionAndThirtyFiles(){
        assertThat(CommitteeService.CATEGORIES).hasSize(11).containsSequence("基坑工程","模版工程及支持体系","脚手架工程");
        CommitteeService.CATEGORIES.forEach(c->CommitteeService.validateMetadata(c,null));
        CommitteeService.validateMetadata("其他"," ");CommitteeService.validateMetadata("其他","字".repeat(2000));
        assertThatThrownBy(()->CommitteeService.validateMetadata("任意类别",null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->CommitteeService.validateMetadata("其他","字".repeat(2001))).isInstanceOf(BusinessException.class);
        assertThat(CommitteeService.normalizedIds(null)).isEmpty();assertThat(CommitteeService.normalizedIds(LongStream.rangeClosed(1,30).boxed().toList())).hasSize(30);
        assertThatThrownBy(()->CommitteeService.normalizedIds(LongStream.rangeClosed(1,31).boxed().toList())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->CommitteeService.normalizedIds(List.of(1L,1L))).isInstanceOf(BusinessException.class);
    }
    @Test void dateRangeRequiresBothDatesInOrderAndAcceptsSameDayOrMultipleYears(){
        var start=LocalDate.of(2024,2,29);
        CommitteeService.validateDateRange(null,null);
        CommitteeService.validateDateRange(start,start);
        CommitteeService.validateDateRange(start,LocalDate.of(2026,9,14));
        CommitteeService.validateDateRange(LocalDate.of(1000,1,1),LocalDate.of(9999,12,31));
        assertThatThrownBy(()->service.page(3L,"",start,null,1,user)).isInstanceOf(BusinessException.class).hasMessageContaining("完整");
        assertThatThrownBy(()->service.page(3L,"",null,start,1,user)).isInstanceOf(BusinessException.class).hasMessageContaining("完整");
        assertThatThrownBy(()->service.page(3L,"",start,start.minusDays(1),1,user)).isInstanceOf(BusinessException.class).hasMessageContaining("早于");
        assertThatThrownBy(()->CommitteeService.validateDateRange(LocalDate.of(999,1,1),start)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->CommitteeService.validateDateRange(start,LocalDate.of(10000,1,1))).isInstanceOf(BusinessException.class);
        verify(records,never()).selectPage(any(),any());verify(records,never()).selectList(any());
    }
    @Test void ownEditPreservesOriginalInspectorAndTimeAndWritesBeforeAfterAudit(){
        var result=service.edit(10L,new CommitteeRequests.Edit("基坑工程","新版结论",List.of(),1),user);
        assertThat(result.inspectorName()).isEqualTo("原检查人快照");assertThat(result.inspectedAt()).isEqualTo(LocalDateTime.of(2026,9,13,10,20,30));assertThat(result.version()).isEqualTo(2);
        verify(logs).insert(argThat((CommitteeLog l)->l.getOperatorId()==7 && l.getBeforeJson().contains("其他") && l.getAfterJson().contains("新版结论")));
    }
    @Test void staleVersionCannotOverwriteOrAudit(){
        assertThatThrownBy(()->service.edit(10L,new CommitteeRequests.Edit("其他","overwrite",List.of(),2),user)).isInstanceOf(BusinessException.class).hasMessageContaining("已被修改");
        verify(records,never()).updateById(any(CommitteeRecord.class));verify(logs,never()).insert(any(CommitteeLog.class));
    }
    @Test void evenAdminCannotEditSomeoneElse(){
        user.setId(8L);when(permissions.isPlatformAdmin(8L)).thenReturn(true);
        assertThatThrownBy(()->service.edit(10L,new CommitteeRequests.Edit("其他","",List.of(),1),user)).isInstanceOf(BusinessException.class).hasMessageContaining("只能修改本人");
        verify(records,never()).updateById(any(CommitteeRecord.class));
    }
    @Test void pendingFilesRequireTheirOwnerAndOriginalPurpose(){
        var a=new CommitteeAttachment();a.setId(1L);a.setProjectId(3L);a.setUploaderId(7L);a.setStatus("PENDING");a.setExpiresAt(CommitteeService.now().plusHours(1));
        when(attachments.selectById(1L)).thenReturn(a);
        service.requireAttachment(1L,user);verify(permissions).requireSystemPermission(7L,3L,CommitteeService.SUBMIT);
        user.setId(8L);assertThatThrownBy(()->service.requireAttachment(1L,user)).isInstanceOf(BusinessException.class);
        user.setId(7L);a.setExpiresAt(CommitteeService.now().minusSeconds(1));assertThatThrownBy(()->service.requireAttachment(1L,user)).isInstanceOf(BusinessException.class);
    }
    @Test void historicalFilesRecheckRealRecordProject(){
        var a=new CommitteeAttachment();a.setId(1L);a.setRecordId(10L);a.setProjectId(3L);a.setStatus("HISTORICAL");when(attachments.selectById(1L)).thenReturn(a);
        doThrow(BusinessException.forbidden("项目已撤权")).when(permissions).requireSystemPermission(7L,3L,CommitteeService.VIEW);
        assertThatThrownBy(()->service.requireAttachment(1L,user)).isInstanceOf(BusinessException.class).hasMessageContaining("撤权");
        verify(files,never()).selectById(any());
    }
    @Test void rollbackCleansOnlyNewPhysicalObject(){
        var part=new MockMultipartFile("file","test.pdf","application/pdf","%PDF-1.7 test".getBytes());
        when(storage.store(anyString(),any())).thenReturn(new StoredFile("local","committee/test.pdf","test.pdf","application/pdf","pdf",part.getSize(),"hash"));
        when(files.insert(any(FileResource.class))).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();
        try{
            assertThatThrownBy(()->service.store(part,3L,null,CommitteeService.PENDING,7L)).isInstanceOf(BusinessException.class);
            verify(storage,never()).deleteQuietly(anyString(),anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).deleteQuietly("local","committee/test.pdf");
        }finally{TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void dedicatedPolicyHasIndependentBinaryLimitsAndRejectsScriptsAndSpoofedContainers(){
        for(var entry:Map.of("jpg",15L,"docx",100L,"mp4",500L).entrySet()){
            long limit=entry.getValue()*1024*1024;FileUploadPolicy.validateCommitteeMetadata("f."+entry.getKey(),limit);
            assertThatThrownBy(()->FileUploadPolicy.validateCommitteeMetadata("f."+entry.getKey(),limit+1)).isInstanceOf(BusinessException.class);
        }
        for(String ext:List.of("html","svg","js","exe","zip","mp3"))assertThatThrownBy(()->FileUploadPolicy.validateCommitteeAttachment(new MockMultipartFile("file","f."+ext,"application/octet-stream","test".getBytes()))).isInstanceOf(BusinessException.class);
        for(String ext:List.of("jpg","mp4","docx","xls"))assertThatThrownBy(()->FileUploadPolicy.validateCommitteeAttachment(new MockMultipartFile("file","f."+ext,"application/octet-stream","not a real file".getBytes()))).isInstanceOf(BusinessException.class);
    }
    @Test void dedicatedModuleCannotInheritQualityOrLegacySafety(){
        assertThat(BusinessModuleCodes.fromPermissionCode("safety_committee.view")).isEqualTo(BusinessModuleCodes.SAFETY_COMMITTEE);
        assertThat(BusinessModuleCodes.fromMenuCode("MINI_SAFETY_COMMITTEE")).isEqualTo(BusinessModuleCodes.SAFETY_COMMITTEE);
        assertThat(BusinessModuleCodes.fromPermissionCode("quality.view")).isNotEqualTo(BusinessModuleCodes.SAFETY_COMMITTEE);
        assertThat(BusinessModuleCodes.fromMenuCode("MINI_SAFETY")).isNull();
    }
}
