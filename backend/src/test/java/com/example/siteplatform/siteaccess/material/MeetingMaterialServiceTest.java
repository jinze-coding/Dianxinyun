package com.example.siteplatform.siteaccess.material;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.*;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.siteaccess.entity.*;
import com.example.siteplatform.siteaccess.mapper.*;
import com.example.siteplatform.siteaccess.service.VisitorDataCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class MeetingMaterialServiceTest {
    MeetingMaterialMapper materials=mock(MeetingMaterialMapper.class);
    MeetingMaterialVersionMapper versions=mock(MeetingMaterialVersionMapper.class);
    MeetingMaterialPreviewMapper previews=mock(MeetingMaterialPreviewMapper.class);
    SiteVisitInvitationMapper invitations=mock(SiteVisitInvitationMapper.class);
    ProjectInfoMapper projects=mock(ProjectInfoMapper.class);
    ProjectPermissionService permissions=mock(ProjectPermissionService.class);
    FileResourceMapper files=mock(FileResourceMapper.class);
    FileStorageManager storage=mock(FileStorageManager.class);
    SiteMeetingVisitAuditLogMapper audits=mock(SiteMeetingVisitAuditLogMapper.class);
    MeetingMaterialService service;
    MeetingMaterial material;
    MeetingMaterialVersion version;
    SiteVisitInvitation meeting;
    SysUser user=new SysUser();
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "material"), MeetingMaterial.class);
        var env=new MockEnvironment(); env.setActiveProfiles("test");
        service=new MeetingMaterialService(materials,versions,previews,invitations,projects,permissions,files,storage,audits,new VisitorDataCryptoService("",env),new ObjectMapper().findAndRegisterModules());
        user.setId(7L); user.setRealName("测试人员");
        meeting=new SiteVisitInvitation(); meeting.setId(19L); meeting.setInviteType("MEETING"); meeting.setProjectId(3L); meeting.setStatus("OPEN"); meeting.setPurpose("会后资料"); meeting.setVisitEndTime(LocalDateTime.now().minusDays(1));
        when(invitations.selectById(19L)).thenReturn(meeting); when(invitations.selectForUpdate(19L)).thenReturn(meeting);
        var project=new ProjectInfo(); project.setProjectStatus("normal"); when(projects.selectById(3L)).thenReturn(project);
        material=new MeetingMaterial(); material.setId(1L); material.setInvitationId(19L); material.setProjectId(3L); material.setCurrentVersionId(10L); material.setPublishedVersionId(10L); material.setStatus("ACTIVE"); material.setVersion(4); material.setTitle("议程"); material.setCategory("AGENDA"); material.setDescription("");
        when(materials.selectById(1L)).thenReturn(material); when(materials.lock(1L)).thenReturn(material); when(materials.update(any(),any())).thenReturn(1); when(audits.insert(any(SiteMeetingVisitAuditLog.class))).thenReturn(1);
        version=new MeetingMaterialVersion(); version.setId(10L); version.setMaterialId(1L); version.setInvitationId(19L); version.setProjectId(3L); version.setFileId(9L); version.setVersionNo(1); version.setCreateTime(LocalDateTime.now()); version.setPublicCode("a".repeat(32));
        when(versions.selectById(10L)).thenReturn(version);
        var file=new FileResource(); file.setFileName("议程.pdf"); file.setFileSize(30L); file.setFileExtension("pdf"); when(files.selectById(9L)).thenReturn(file);
        var preview=new MeetingMaterialPreview(); preview.setStatus("READY"); preview.setKind("PDF"); when(previews.selectOne(any())).thenReturn(preview);
    }
    @Test void endedMeetingMaterialsRemainPublicWithoutReopeningMeeting() {
        when(invitations.selectOne(any())).thenReturn(meeting); when(materials.selectList(any())).thenReturn(List.of(material)); when(versions.selectOne(any())).thenReturn(version);
        assertThat(service.publicResolve("valid_invite_token_123").get("ended")).isEqualTo(true);
        assertThat(service.publicVersion("a".repeat(32))).isSameAs(version);
        verify(invitations,never()).updateById(any(SiteVisitInvitation.class));
        meeting.setStatus("VOIDED"); assertThatThrownBy(()->service.publicVersion("a".repeat(32))).isInstanceOf(BusinessException.class);
    }
    @Test void internalReplacementDoesNotPublishItselfAndPublishingRevokesOldAddress() {
        when(versions.selectOne(any())).thenReturn(version); material.setCurrentVersionId(11L);
        assertThat(service.publicVersion("a".repeat(32))).isSameAs(version);
        var next=new MeetingMaterialVersion(); next.setId(11L); next.setFileId(9L); when(versions.selectById(11L)).thenReturn(next);
        service.publish(1L,new MeetingMaterialRequests.Publication(4,11L),user);
        assertThat(material.getPublishedVersionId()).isEqualTo(11L);
        assertThatThrownBy(()->service.publicVersion("a".repeat(32))).isInstanceOf(BusinessException.class);
    }
    @Test void withdrawalImmediatelyDeniesOriginalPublicAddressWithoutRemovingVersions() {
        when(versions.selectOne(any())).thenReturn(version);
        service.state(1L,new MeetingMaterialRequests.State(4,true),user);
        assertThat(material.getPublishedVersionId()).isNull();
        assertThatThrownBy(()->service.publicVersion("a".repeat(32))).isInstanceOf(BusinessException.class);
        verify(versions,never()).delete(any());
    }
    @Test void staleMetadataOrProjectPermissionFailureCannotWriteOrDiscloseFiles() {
        assertThatThrownBy(()->service.edit(1L,new MeetingMaterialRequests.Edit("new","OTHER","",3),user)).isInstanceOf(BusinessException.class);
        verify(materials,never()).update(any(),any());
        doThrow(BusinessException.forbidden("项目无权限")).when(permissions).requireSystemPermission(7L,3L,"site_access.view");
        assertThatThrownBy(()->service.requireVersion(10L,user)).isInstanceOf(BusinessException.class);
    }
    @Test void databaseFailureCleansNewPhysicalFileOnlyAfterRollback() {
        var upload=new MockMultipartFile("file","new.pdf","application/pdf","%PDF-1.7 test".getBytes());
        var stored=new StoredFile("local","meeting-materials/new.pdf","new.pdf","application/pdf","pdf",upload.getSize(),"sha");
        when(storage.store(anyString(),any())).thenReturn(stored); when(files.insert(any(FileResource.class))).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(()->service.store(upload,3L,1L,"MEETING_MATERIAL",7L)).isInstanceOf(BusinessException.class);
            verify(storage,never()).deleteQuietly(anyString(),anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).deleteQuietly("local","meeting-materials/new.pdf");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
