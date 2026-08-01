package com.example.siteplatform.document.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.DocumentFolderCreateRequest;
import com.example.siteplatform.document.entity.DocumentFolder;
import com.example.siteplatform.document.mapper.DocumentFolderMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentFolderServiceTest {
    @Mock
    private DocumentFolderMapper folderMapper;
    @Mock
    private ProjectDocumentMapper documentMapper;
    @Mock
    private ProjectPermissionService permissionService;

    private DocumentFolderService service;
    private SysUser admin;

    @BeforeEach
    void setUp() {
        service = new DocumentFolderService(folderMapper, documentMapper, permissionService);
        admin = new SysUser();
        admin.setId(2L);
        admin.setRealName("项目管理员");
        lenient().when(folderMapper.insert(any())).thenReturn(1);
    }

    @Test
    void createsRootFolderButRejectsChildFolder() {
        when(permissionService.canManageProject(2L, 1L)).thenReturn(true);
        when(folderMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        DocumentFolderCreateRequest root = request(0L, "施工方案");
        assertEquals("施工方案", service.create(root, admin).getFolderName());

        DocumentFolderCreateRequest child = request(10L, "子目录");
        assertThrows(BusinessException.class, () -> service.create(child, admin));
    }

    @Test
    void ordinaryMemberCannotManageFolders() {
        SysUser member = new SysUser();
        member.setId(9L);
        when(permissionService.canManageProject(9L, 1L)).thenReturn(false);
        when(permissionService.isPlatformAdmin(9L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.create(request(0L, "施工图纸"), member));
    }

    @Test
    void createReturnsConflictWhenFolderInsertDidNotTakeEffect() {
        when(permissionService.canManageProject(2L, 1L)).thenReturn(true);
        when(folderMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(folderMapper.insert(any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(0L, "施工图纸"), admin));

        assertEquals(409, error.getCode());
        verify(documentMapper, never()).selectCount(any());
    }

    private DocumentFolderCreateRequest request(Long parentId, String name) {
        DocumentFolderCreateRequest request = new DocumentFolderCreateRequest();
        request.setProjectId(1L);
        request.setParentId(parentId);
        request.setFolderName(name);
        return request;
    }
}
