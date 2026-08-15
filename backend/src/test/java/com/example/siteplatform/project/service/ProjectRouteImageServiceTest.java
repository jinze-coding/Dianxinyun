package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.security.FileUploadPolicy;
import com.example.siteplatform.file.storage.FileStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRouteImageServiceTest {
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;

    @Test
    void replaceLocksPendingThenArchivesCurrentAndBindsExactlyOneImage() {
        ProjectRouteImageService service = service();
        FileResource pending = pending(9L, 7L, 3L);
        FileResource current = finalImage(8L, 7L);
        when(fileMapper.selectByIdsForUpdate(List.of(9L))).thenReturn(List.of(pending));
        when(fileMapper.selectActiveProjectRouteImagesForUpdate(7L)).thenReturn(List.of(current));
        when(fileMapper.archiveProjectRouteImage(8L, 7L)).thenReturn(1);
        when(fileMapper.bindPendingProjectRouteImage(9L, 7L)).thenReturn(1);

        String action = service.applyLocationAction(7L, " replace ", 9L, user(3L));

        assertThat(action).isEqualTo(ProjectRouteImageService.ACTION_REPLACE);
        InOrder writes = inOrder(fileMapper);
        writes.verify(fileMapper).archiveProjectRouteImage(8L, 7L);
        writes.verify(fileMapper).bindPendingProjectRouteImage(9L, 7L);
    }

    @Test
    void removeArchivesEveryUnexpectedActiveRowSoOnlyZeroCanRemain() {
        ProjectRouteImageService service = service();
        FileResource first = finalImage(8L, 7L);
        FileResource second = finalImage(9L, 7L);
        when(fileMapper.selectActiveProjectRouteImagesForUpdate(7L)).thenReturn(List.of(first, second));
        when(fileMapper.archiveProjectRouteImage(8L, 7L)).thenReturn(1);
        when(fileMapper.archiveProjectRouteImage(9L, 7L)).thenReturn(1);

        assertThat(service.applyLocationAction(7L, "REMOVE", null, user(3L)))
                .isEqualTo(ProjectRouteImageService.ACTION_REMOVE);

        verify(fileMapper).archiveProjectRouteImage(8L, 7L);
        verify(fileMapper).archiveProjectRouteImage(9L, 7L);
    }

    @Test
    void keepDoesNotTouchFileRowsAndRejectsInjectedFileId() {
        ProjectRouteImageService service = service();

        assertThat(service.applyLocationAction(7L, null, null, user(3L)))
                .isEqualTo(ProjectRouteImageService.ACTION_KEEP);
        verifyNoInteractions(fileMapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyLocationAction(7L, "KEEP", 9L, user(3L)));
        assertThat(error.getMessage()).contains("不能指定新文件");
    }

    @Test
    void replaceRejectsAnotherProjectUploaderOrOversizedFileBeforeMutatingCurrentImage() {
        ProjectRouteImageService service = service();
        FileResource pending = pending(9L, 99L, 3L);
        when(fileMapper.selectByIdsForUpdate(List.of(9L))).thenReturn(List.of(pending));
        assertThrows(BusinessException.class,
                () -> service.applyLocationAction(7L, "REPLACE", 9L, user(3L)));

        pending.setProjectId(7L);
        pending.setUploaderId(99L);
        assertThrows(BusinessException.class,
                () -> service.applyLocationAction(7L, "REPLACE", 9L, user(3L)));

        pending.setUploaderId(3L);
        pending.setFileSize(FileUploadPolicy.MAX_IMAGE_BYTES + 1);
        assertThrows(BusinessException.class,
                () -> service.applyLocationAction(7L, "REPLACE", 9L, user(3L)));

        verify(fileMapper, never()).selectActiveProjectRouteImagesForUpdate(7L);
        verify(fileMapper, never()).bindPendingProjectRouteImage(9L, 7L);
    }

    @Test
    void affectedRowMismatchIsAControlledConflict() {
        ProjectRouteImageService service = service();
        when(fileMapper.selectByIdsForUpdate(List.of(9L))).thenReturn(List.of(pending(9L, 7L, 3L)));
        when(fileMapper.selectActiveProjectRouteImagesForUpdate(7L)).thenReturn(List.of());
        when(fileMapper.bindPendingProjectRouteImage(9L, 7L)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.applyLocationAction(7L, "REPLACE", 9L, user(3L)));

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("绑定未生效");
    }

    @Test
    void metadataAndPublicContentExposeOnlySafeImageProperties() {
        ProjectRouteImageService service = service();
        FileResource image = finalImage(8L, 7L);
        image.setFileName("项目东门路线.webp");
        image.setOriginalFileName("route.webp");
        image.setFileExtension("webp");
        image.setMimeType("image/webp");
        when(fileMapper.selectActiveProjectRouteImage(7L)).thenReturn(image);
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(storageManager.load(image)).thenReturn(resource);

        var metadata = service.activeMetadata(7L);
        var content = service.publicImage(7L);

        assertThat(metadata.getFileId()).isEqualTo(8L);
        assertThat(metadata.getFileName()).isEqualTo("项目东门路线.webp");
        assertThat(metadata.getFileSize()).isEqualTo(1024L);
        assertThat(metadata.getMimeType()).isEqualTo("image/webp");
        assertThat(content.resource()).isSameAs(resource);
        assertThat(content.extension()).isEqualTo("webp");
        assertThat(content.mediaType().toString()).isEqualTo("image/webp");
    }

    private ProjectRouteImageService service() {
        return new ProjectRouteImageService(fileMapper, storageManager);
    }

    private FileResource pending(Long id, Long projectId, Long uploaderId) {
        FileResource image = new FileResource();
        image.setId(id);
        image.setProjectId(projectId);
        image.setUploaderId(uploaderId);
        image.setBusinessType(ProjectRouteImageService.PENDING_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED);
        image.setFileExtension("jpg");
        image.setFileSize(1024L);
        return image;
    }

    private FileResource finalImage(Long id, Long projectId) {
        FileResource image = new FileResource();
        image.setId(id);
        image.setProjectId(projectId);
        image.setBusinessId(projectId);
        image.setBusinessType(ProjectRouteImageService.FINAL_IMAGE_TYPE);
        image.setStatus(FileStatus.UPLOADED);
        image.setFileName("route.jpg");
        image.setFileExtension("jpg");
        image.setFileSize(1024L);
        return image;
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }
}
