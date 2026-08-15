package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.dto.ProjectProfileDetailVO;
import com.example.siteplatform.project.dto.ProjectProfileUpdateRequest;
import com.example.siteplatform.project.dto.PublicProjectProfileVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectProfileServiceTest {
    @Mock private ProjectInfoMapper projectMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private ProjectPermissionService permissionService;
    @Mock private SystemPermissionService systemPermissionService;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private FileStorageManager storageManager;
    private ProjectProfileService service;
    private SysUser user;

    @BeforeEach
    void setUp() {
        service = new ProjectProfileService(projectMapper, fileMapper, permissionService,
                systemPermissionService, operationLogMapper, storageManager);
        user = new SysUser();
        user.setId(9L);
        user.setUsername("operator");
    }

    @Test
    void activeProjectMemberCanReadAndCanEditComesFromPlatformRole() {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProjectName("项目");
        project.setProfileVersion(2);
        when(projectMapper.selectById(3L)).thenReturn(project);
        when(systemPermissionService.isPlatformAdmin(9L)).thenReturn(false);

        ProjectProfileDetailVO result = service.getProfile(3L, user);

        verify(permissionService).checkProjectPermission(9L, 3L);
        assertEquals(2, result.getProfileVersion());
        assertEquals(false, result.isCanEdit());
    }

    @Test
    void projectAccessFailureIsNotBypassedForRead() {
        doThrow(BusinessException.forbidden("无项目权限"))
                .when(permissionService).checkProjectPermission(9L, 3L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getProfile(3L, user));

        assertEquals(403, exception.getCode());
    }

    @Test
    void publicProfileContainsAllowlistedFieldsWithoutInternalIdentifiersOrSensitiveData() throws Exception {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProjectName("公开项目");
        project.setShortName("项目简称");
        project.setPhase("建设中");
        project.setAddress("项目地址");
        project.setManager("内部经理");
        project.setManagerPhone("13800000000");
        project.setContractAmount(BigDecimal.valueOf(100));
        project.setFixedIpAddress("192.0.2.1");
        project.setBuildingArea(BigDecimal.valueOf(123.45));
        project.setProjectClassification("公开分类");
        project.setProjectCategory("内部类别");
        FileResource image = new FileResource();
        image.setId(77L);
        image.setProjectId(3L);
        image.setBusinessId(3L);
        image.setBusinessType(ProjectProfileService.FINAL_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED);
        image.setFileName("效果图.jpg");
        image.setOriginalFileName("效果图.jpg");
        image.setFileExtension("jpg");
        when(projectMapper.selectById(3L)).thenReturn(project);
        when(fileMapper.selectList(any())).thenReturn(List.of(image));

        PublicProjectProfileVO result = service.getPublicProfile(3L);

        assertThat(result.getProjectName()).isEqualTo("公开项目");
        assertThat(result.getBuildingArea()).isEqualByComparingTo("123.45");
        assertThat(result.getProjectClassification()).isEqualTo("公开分类");
        assertThat(result.getImages()).singleElement().satisfies(item -> {
            assertThat(item.getImageIndex()).isZero();
            assertThat(item.getMimeType()).isEqualTo("image/jpeg");
        });
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertThat(json).doesNotContain("projectId", "fileId", "managerPhone", "contractAmount", "fixedIpAddress", "projectCategory");
        assertThat(json).doesNotContain("13800000000", "192.0.2.1", "77", "内部类别");
    }

    @Test
    void publicImageUsesOnlyActiveProjectProfileImageByIndex() {
        FileResource image = new FileResource();
        image.setId(77L);
        image.setProjectId(3L);
        image.setBusinessId(3L);
        image.setBusinessType(ProjectProfileService.FINAL_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED);
        image.setFileName("效果图.webp");
        image.setOriginalFileName("效果图.webp");
        image.setFileExtension("webp");
        image.setFileSize(3L);
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(fileMapper.selectList(any())).thenReturn(List.of(image));
        when(storageManager.load(image)).thenReturn(resource);

        var result = service.getPublicProfileImage(3L, 0);

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.mediaType().toString()).isEqualTo("image/webp");
        assertThat(result.fileSize()).isEqualTo(3L);
        assertThrows(BusinessException.class, () -> service.getPublicProfileImage(3L, 1));
    }

    @Test
    void nonAdminCannotUpdate() {
        doThrow(BusinessException.forbidden("仅平台管理员"))
                .when(systemPermissionService).requirePlatformAdmin(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, completeRequest(), user));

        assertEquals(403, exception.getCode());
    }

    @Test
    void staleVersionReturnsConflictBeforeAnyWrite() {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProfileVersion(5);
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, completeRequest(), user));

        assertEquals(409, exception.getCode());
    }

    @Test
    void validUpdateBindsPendingImageAndSynchronizesLegacyArea() {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProfileVersion(0);
        project.setAddress("项目地址");
        FileResource image = new FileResource();
        image.setId(11L);
        image.setProjectId(3L);
        image.setBusinessType(ProjectProfileService.PENDING_IMAGE_TYPE);
        image.setUploaderId(9L);
        image.setStatus(FileStatus.UPLOADED);
        image.setFileExtension("jpeg");
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(image));
        when(projectMapper.updateById(project)).thenReturn(1);
        when(fileMapper.bindPendingProjectProfileImage(11L, 3L)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);
        when(systemPermissionService.isPlatformAdmin(9L)).thenReturn(true);

        ProjectProfileDetailVO result = service.updateProfile(3L, completeRequest(), user);

        assertEquals("123.45", project.getArea());
        assertEquals(1, result.getProfileVersion());
        verify(operationLogMapper).insert(any());
    }

    @Test
    void invalidRequiredAndNumericFieldsAreRejected() {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProfileVersion(0);
        project.setAddress("项目地址");
        ProjectProfileUpdateRequest request = completeRequest();
        request.setShortName(" ");
        request.setBuildingArea(BigDecimal.valueOf(-1));
        FileResource image = new FileResource();
        image.setId(11L); image.setProjectId(3L); image.setUploaderId(9L);
        image.setBusinessType(ProjectProfileService.PENDING_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED); image.setFileExtension("png");
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(image));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, request, user));

        assertEquals(400, exception.getCode());
    }

    @Test
    void crossProjectImageIsRejectedBeforeProjectWrite() {
        ProjectInfo project = projectWithVersion(0);
        FileResource image = pendingImage(11L, 99L);
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(image));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, completeRequest(), user));

        assertEquals(400, exception.getCode());
        verify(projectMapper, org.mockito.Mockito.never()).updateById(any());
    }

    @Test
    void removedExistingImageIsArchivedDuringSameUpdate() {
        ProjectInfo project = projectWithVersion(0);
        FileResource oldImage = new FileResource();
        oldImage.setId(10L); oldImage.setProjectId(3L); oldImage.setBusinessId(3L);
        oldImage.setBusinessType(ProjectProfileService.FINAL_IMAGE_TYPE);
        oldImage.setStatus(FileStatus.UPLOADED); oldImage.setFileExtension("jpeg");
        FileResource newImage = pendingImage(11L, 3L);
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectList(any())).thenReturn(List.of(oldImage), List.of(oldImage), List.of());
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(newImage));
        when(projectMapper.updateById(project)).thenReturn(1);
        when(fileMapper.archiveProjectProfileImage(10L, 3L)).thenReturn(1);
        when(fileMapper.bindPendingProjectProfileImage(11L, 3L)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);

        service.updateProfile(3L, completeRequest(), user);

        verify(fileMapper).archiveProjectProfileImage(10L, 3L);
        verify(fileMapper).bindPendingProjectProfileImage(11L, 3L);
    }

    @Test
    void auditWriteFailureAbortsProfileUpdate() {
        ProjectInfo project = projectWithVersion(0);
        FileResource image = pendingImage(11L, 3L);
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(image));
        when(projectMapper.updateById(project)).thenReturn(1);
        when(fileMapper.bindPendingProjectProfileImage(11L, 3L)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, completeRequest(), user));

        assertEquals(409, exception.getCode());
    }

    @Test
    void profileUpdateCannotChangeAddressOutsideLocationEndpoint() {
        ProjectInfo project = projectWithVersion(0);
        ProjectProfileUpdateRequest request = completeRequest();
        request.setAddress("另一处地址");
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProfile(3L, request, user));

        assertEquals(400, exception.getCode());
        assertEquals("请通过项目定位接口同步修改地址与导航点", exception.getMessage());
        verify(fileMapper, org.mockito.Mockito.never()).selectByIdsForUpdate(any());
        verify(projectMapper, org.mockito.Mockito.never()).updateById(any());
    }

    @Test
    void profileUpdateAcceptsAddressWithEquivalentOuterWhitespace() {
        ProjectInfo project = projectWithVersion(0);
        project.setAddress("  项目地址  ");
        FileResource image = pendingImage(11L, 3L);
        when(projectMapper.selectByIdForUpdate(3L)).thenReturn(project);
        when(fileMapper.selectByIdsForUpdate(List.of(11L))).thenReturn(List.of(image));
        when(projectMapper.updateById(project)).thenReturn(1);
        when(fileMapper.bindPendingProjectProfileImage(11L, 3L)).thenReturn(1);
        when(operationLogMapper.insert(any())).thenReturn(1);

        service.updateProfile(3L, completeRequest(), user);

        assertEquals("  项目地址  ", project.getAddress());
        verify(projectMapper).updateById(project);
    }

    private ProjectInfo projectWithVersion(int version) {
        ProjectInfo project = new ProjectInfo();
        project.setId(3L);
        project.setProfileVersion(version);
        project.setAddress("项目地址");
        return project;
    }

    private FileResource pendingImage(Long id, Long projectId) {
        FileResource image = new FileResource();
        image.setId(id); image.setProjectId(projectId); image.setUploaderId(9L);
        image.setBusinessType(ProjectProfileService.PENDING_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED); image.setFileExtension("jpeg");
        return image;
    }

    private ProjectProfileUpdateRequest completeRequest() {
        ProjectProfileUpdateRequest request = new ProjectProfileUpdateRequest();
        request.setProjectName("完整项目名称");
        request.setShortName("项目简称");
        request.setDirectCompany("直属公司");
        request.setManager("项目经理");
        request.setManagerPhone("13800000000");
        request.setAddress("项目地址");
        request.setEngineeringType("房屋建筑");
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2027, 1, 1));
        request.setPhase("施工中");
        request.setOwnerUnit("建设单位");
        request.setSupervisionUnit("监理单位");
        request.setDesignUnit("设计单位");
        request.setContractor("施工单位");
        request.setBuildingArea(new BigDecimal("123.45"));
        request.setLandArea(new BigDecimal("100"));
        request.setBuildingHeight(new BigDecimal("20"));
        request.setImageFileIds(List.of(11L));
        request.setExpectedVersion(0);
        return request;
    }
}
