package com.example.siteplatform.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.DocumentFolderCreateRequest;
import com.example.siteplatform.document.dto.DocumentFolderUpdateRequest;
import com.example.siteplatform.document.entity.DocumentFolder;
import com.example.siteplatform.document.mapper.DocumentFolderMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.vo.DocumentFolderVO;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentFolderService {
    private final DocumentFolderMapper folderMapper;
    private final ProjectDocumentMapper documentMapper;
    private final ProjectPermissionService permissionService;

    public DocumentFolderService(DocumentFolderMapper folderMapper,
                                 ProjectDocumentMapper documentMapper,
                                 ProjectPermissionService permissionService) {
        this.folderMapper = folderMapper;
        this.documentMapper = documentMapper;
        this.permissionService = permissionService;
    }

    public List<DocumentFolderVO> list(Long projectId, SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        return folderMapper.selectList(new LambdaQueryWrapper<DocumentFolder>()
                        .eq(DocumentFolder::getProjectId, projectId)
                        .orderByAsc(DocumentFolder::getSortNo)
                        .orderByAsc(DocumentFolder::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public DocumentFolderVO create(DocumentFolderCreateRequest request, SysUser currentUser) {
        checkManage(currentUser, request.getProjectId());
        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        if (parentId != 0L) throw new BusinessException("资料目录仅支持一级目录");
        String name = normalizeName(request.getFolderName());
        assertNameAvailable(request.getProjectId(), 0L, name, null);

        DocumentFolder folder = new DocumentFolder();
        folder.setProjectId(request.getProjectId());
        folder.setParentId(0L);
        folder.setFolderName(name);
        folder.setSortNo(0);
        folder.setCreatedBy(currentUser.getId());
        folder.setDeleted(0);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.insert(folder);
        return toVO(folder);
    }

    @Transactional
    public DocumentFolderVO update(Long id, DocumentFolderUpdateRequest request, SysUser currentUser) {
        DocumentFolder folder = requireFolder(id);
        checkManage(currentUser, folder.getProjectId());
        String name = normalizeName(request.getFolderName());
        assertNameAvailable(folder.getProjectId(), folder.getParentId(), name, id);
        folder.setFolderName(name);
        folder.setUpdateTime(LocalDateTime.now());
        folderMapper.updateById(folder);
        return toVO(folder);
    }

    @Transactional
    public void delete(Long id, SysUser currentUser) {
        DocumentFolder folder = requireFolder(id);
        checkManage(currentUser, folder.getProjectId());
        if (folderMapper.countAllChildren(id) > 0 || documentMapper.countAllByFolder(id) > 0) {
            throw new BusinessException("目录非空，不能删除");
        }
        folderMapper.deleteById(id);
    }

    public void validateFolder(Long projectId, Long folderId) {
        if (folderId == null || folderId == 0L) return;
        DocumentFolder folder = requireFolder(folderId);
        if (!projectId.equals(folder.getProjectId())) throw new BusinessException("目录不属于当前作业区域");
        if (folder.getParentId() != null && folder.getParentId() != 0L) {
            throw new BusinessException("资料目录仅支持一级目录，请先完成目录数据迁移");
        }
    }

    private void assertNameAvailable(Long projectId, Long parentId, String name, Long excludedId) {
        LambdaQueryWrapper<DocumentFolder> wrapper = new LambdaQueryWrapper<DocumentFolder>()
                .eq(DocumentFolder::getProjectId, projectId)
                .eq(DocumentFolder::getParentId, parentId)
                .eq(DocumentFolder::getFolderName, name);
        if (excludedId != null) wrapper.ne(DocumentFolder::getId, excludedId);
        if (folderMapper.selectCount(wrapper) > 0) throw new BusinessException("同级目录下已存在同名目录");
    }

    private DocumentFolder requireFolder(Long id) {
        DocumentFolder folder = folderMapper.selectById(id);
        if (folder == null) throw BusinessException.notFound("目录不存在");
        return folder;
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) throw new BusinessException("目录名称不能为空");
        if (name.length() > 100) throw new BusinessException("目录名称不能超过100个字符");
        return name;
    }

    private void checkManage(SysUser currentUser, Long projectId) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        if (!permissionService.isPlatformAdmin(currentUser.getId())
                && !permissionService.canManageProject(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("仅项目管理员可管理资料目录");
        }
    }

    private DocumentFolderVO toVO(DocumentFolder folder) {
        DocumentFolderVO vo = new DocumentFolderVO();
        vo.setId(folder.getId());
        vo.setProjectId(folder.getProjectId());
        vo.setParentId(folder.getParentId());
        vo.setFolderName(folder.getFolderName());
        vo.setSortNo(folder.getSortNo());
        vo.setDocumentCount(documentMapper.selectCount(new LambdaQueryWrapper<com.example.siteplatform.document.entity.ProjectDocument>()
                .eq(com.example.siteplatform.document.entity.ProjectDocument::getFolderId, folder.getId())));
        vo.setUpdateTime(folder.getUpdateTime());
        return vo;
    }
}
