package com.example.siteplatform.document.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.document.dto.DocumentIncomingItemRequest;
import com.example.siteplatform.document.dto.DocumentIncomingPublishRequest;
import com.example.siteplatform.document.entity.DocumentCirculationEvent;
import com.example.siteplatform.document.entity.DocumentDistributionBatch;
import com.example.siteplatform.document.entity.DocumentDistributionItem;
import com.example.siteplatform.document.entity.DocumentDistributionRecipient;
import com.example.siteplatform.document.entity.DocumentIncomingBatch;
import com.example.siteplatform.document.entity.DocumentIncomingItem;
import com.example.siteplatform.document.entity.ProjectDocument;
import com.example.siteplatform.document.entity.ProjectDocumentVersion;
import com.example.siteplatform.document.mapper.DocumentCirculationEventMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionBatchMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionItemMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientItemMapper;
import com.example.siteplatform.document.mapper.DocumentDistributionRecipientMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingBatchMapper;
import com.example.siteplatform.document.mapper.DocumentIncomingItemMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentMapper;
import com.example.siteplatform.document.mapper.ProjectDocumentVersionMapper;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.mapper.SysUserProjectRoleMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentCirculationServiceTest {
    private final DocumentIncomingBatchMapper incomingBatchMapper = mock(DocumentIncomingBatchMapper.class);
    private final DocumentIncomingItemMapper incomingItemMapper = mock(DocumentIncomingItemMapper.class);
    private final DocumentDistributionBatchMapper batchMapper = mock(DocumentDistributionBatchMapper.class);
    private final DocumentDistributionItemMapper itemMapper = mock(DocumentDistributionItemMapper.class);
    private final DocumentDistributionRecipientMapper recipientMapper = mock(DocumentDistributionRecipientMapper.class);
    private final DocumentDistributionRecipientItemMapper recipientItemMapper = mock(DocumentDistributionRecipientItemMapper.class);
    private final DocumentCirculationEventMapper eventMapper = mock(DocumentCirculationEventMapper.class);
    private final ProjectDocumentMapper documentMapper = mock(ProjectDocumentMapper.class);
    private final ProjectDocumentVersionMapper versionMapper = mock(ProjectDocumentVersionMapper.class);
    private final FileResourceMapper fileMapper = mock(FileResourceMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserProjectMapper userProjectMapper = mock(SysUserProjectMapper.class);
    private final SysUserProjectRoleMapper userProjectRoleMapper = mock(SysUserProjectRoleMapper.class);
    private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
    private final UserNotificationService notificationService = mock(UserNotificationService.class);
    private final DocumentSceneService sceneService = mock(DocumentSceneService.class);
    private final FileStorageManager storageManager = mock(FileStorageManager.class);
    private DocumentCirculationService service;
    private SysUser user;
    private DocumentDistributionBatch batch;
    private DocumentDistributionRecipient recipient;

    @BeforeEach
    void setUp() {
        service = new DocumentCirculationService(incomingBatchMapper, incomingItemMapper, batchMapper,
                itemMapper, recipientMapper, recipientItemMapper, eventMapper, documentMapper, versionMapper,
                fileMapper, userMapper, userProjectMapper, userProjectRoleMapper, permissionService,
                notificationService, sceneService, storageManager, new ObjectMapper());
        user = new SysUser(); user.setId(8L); user.setUsername("13800000008"); user.setRealName("接收人");
        batch = new DocumentDistributionBatch(); batch.setId(20L); batch.setProjectId(3L);
        batch.setDistributionNo("FF-20"); batch.setDeadline(LocalDateTime.now().plusDays(1));
        batch.setStatus("PUBLISHED"); batch.setQrStatus("ACTIVE"); batch.setQrSceneDigest("digest");
        batch.setElectronicSignatureRequired(0); batch.setPaperSignatureRequired(1); batch.setVersion(0);
        recipient = new DocumentDistributionRecipient(); recipient.setId(30L); recipient.setBatchId(20L);
        recipient.setProjectId(3L); recipient.setUserId(8L); recipient.setChannel("PAPER");
        recipient.setStatus("PENDING"); recipient.setRealNameSnapshot("接收人");
        when(batchMapper.selectForUpdate(20L)).thenReturn(batch);
        when(recipientMapper.selectOne(any())).thenReturn(recipient);
        when(recipientMapper.selectForUpdate(30L)).thenReturn(recipient);
        when(permissionService.getProjectAccessStatus(8L, 3L)).thenReturn("ACTIVE");
        when(permissionService.hasSystemPermission(8L, 3L, SystemPermissionCodes.DOCUMENT_VIEW)).thenReturn(true);
        when(eventMapper.insert(any())).thenReturn(1);
    }

    @Test
    void paperReceiptRejectsWrongSceneBeforeReadingBatchItemsOrWritingReceipt() {
        when(sceneService.normalizeScene("wrong-scene")).thenReturn("wrong-scene");
        when(sceneService.digest("wrong-scene")).thenReturn("other-digest");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(20L, "wrong-scene", null, user, null));

        assertEquals(403, exception.getCode());
        verify(recipientMapper, never()).updateById(any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void repeatedConfirmedReceiptIsIdempotentAndDoesNotCreateAnotherSignature() {
        recipient.setStatus("CONFIRMED");
        when(itemMapper.selectList(any())).thenReturn(List.of());

        assertEquals("CONFIRMED", service.confirm(20L, null, null, user, null)
                .getCurrentRecipient().getStatus());

        verify(recipientMapper, never()).updateById(any());
        verify(fileMapper, never()).insert(any());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void exactVersionAccessUsesOnlyAVerifiedDistributionBatchHint() {
        ProjectDocument document = new ProjectDocument();
        document.setId(40L); document.setProjectId(3L); document.setDocumentType("DRAWING");
        ProjectDocumentVersion version = new ProjectDocumentVersion();
        version.setId(50L); version.setDocumentId(40L); version.setVersionNo(2);
        DocumentDistributionItem item = new DocumentDistributionItem();
        item.setBatchId(20L); item.setDocumentId(40L); item.setVersionId(50L);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        service.recordDocumentAccess(document, version, user, false, true, null, 20L);

        ArgumentCaptor<DocumentCirculationEvent> event = ArgumentCaptor.forClass(DocumentCirculationEvent.class);
        verify(eventMapper).insert(event.capture());
        assertEquals(20L, event.getValue().getDistributionBatchId());
        assertEquals(30L, event.getValue().getRecipientId());
        assertEquals(50L, event.getValue().getVersionId());
        assertEquals("DOWNLOAD", event.getValue().getEventType());
    }

    @Test
    void duplicateExternalRevisionChecksTheWholeVersionHistoryIgnoringCaseAndWhitespace() {
        ProjectDocumentVersion revisionA = new ProjectDocumentVersion();
        revisionA.setExternalRevision(" Rev.A ");
        ProjectDocumentVersion revisionB = new ProjectDocumentVersion();
        revisionB.setExternalRevision("Rev.B");
        when(versionMapper.selectList(any())).thenReturn(List.of(revisionA, revisionB));

        assertTrue(service.externalRevisionAlreadyUsed(40L, "rev.a"));
        assertFalse(service.externalRevisionAlreadyUsed(40L, "Rev.C"));
    }

    @Test
    void multiFilePublishFailureStopsAllDownstreamArtifactsInsideATransaction() throws Exception {
        DocumentIncomingBatch incoming = incomingBatch();
        when(incomingBatchMapper.selectForUpdate(10L)).thenReturn(incoming);
        Map<Long, FileResource> files = Map.of(101L, pendingFile(101L), 102L, pendingFile(102L));
        when(fileMapper.selectById(any())).thenAnswer(invocation -> files.get(invocation.getArgument(0)));
        List<DocumentIncomingItem> staged = new ArrayList<>();
        when(incomingItemMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(staged));
        when(incomingItemMapper.deleteDraftItems(10L)).thenAnswer(invocation -> { staged.clear(); return 0; });
        AtomicInteger itemIds = new AtomicInteger(1000);
        when(incomingItemMapper.insert(any())).thenAnswer(invocation -> {
            DocumentIncomingItem item = invocation.getArgument(0);
            item.setId((long) itemIds.incrementAndGet()); staged.add(item); return 1;
        });
        when(incomingItemMapper.updateById(any())).thenReturn(1);
        when(documentMapper.selectList(any())).thenReturn(List.of());
        AtomicInteger documentIds = new AtomicInteger(2000);
        when(documentMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectDocument) invocation.getArgument(0)).setId((long) documentIds.incrementAndGet()); return 1;
        });
        when(documentMapper.updateById(any())).thenReturn(1);
        when(fileMapper.bindDocumentIncomingFile(any(), any(), any(), any())).thenReturn(1);
        AtomicInteger versionWrites = new AtomicInteger();
        when(versionMapper.insert(any())).thenAnswer(invocation -> {
            ProjectDocumentVersion version = invocation.getArgument(0);
            version.setId(3000L + versionWrites.incrementAndGet());
            return versionWrites.get() == 1 ? 1 : 0;
        });

        DocumentIncomingPublishRequest request = new DocumentIncomingPublishRequest();
        request.setExpectedVersion(0);
        request.setItems(List.of(incomingItemRequest(101L, "A-01", "NEW_DOCUMENT", null),
                incomingItemRequest(102L, "A-02", "NEW_DOCUMENT", null)));

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.publishIncoming(10L, request, user, null));

        assertEquals(409, conflict.getCode());
        assertTrue(DocumentCirculationService.class.getMethod("publishIncoming", Long.class,
                        DocumentIncomingPublishRequest.class, SysUser.class, jakarta.servlet.http.HttpServletRequest.class)
                .isAnnotationPresent(Transactional.class));
        verify(batchMapper, never()).insert(any());
        verify(incomingBatchMapper, never()).updateById(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void concurrentVersionSwitchConflictReturns409BeforeChangingTheCurrentPointer() {
        DocumentIncomingBatch incoming = incomingBatch();
        when(incomingBatchMapper.selectForUpdate(10L)).thenReturn(incoming);
        FileResource file = pendingFile(101L);
        when(fileMapper.selectById(101L)).thenReturn(file);
        List<DocumentIncomingItem> staged = new ArrayList<>();
        when(incomingItemMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(staged));
        when(incomingItemMapper.deleteDraftItems(10L)).thenAnswer(invocation -> { staged.clear(); return 0; });
        when(incomingItemMapper.insert(any())).thenAnswer(invocation -> {
            DocumentIncomingItem item = invocation.getArgument(0); item.setId(1001L); staged.add(item); return 1;
        });
        ProjectDocument existing = new ProjectDocument();
        existing.setId(200L); existing.setProjectId(3L); existing.setDocumentNo("A-01");
        existing.setDocumentType("DRAWING"); existing.setCurrentVersionId(300L);
        ProjectDocumentVersion previous = new ProjectDocumentVersion();
        previous.setId(300L); previous.setDocumentId(200L); previous.setVersionNo(1);
        previous.setExternalRevision("Rev.A"); previous.setVersionStatus("CURRENT");
        when(documentMapper.selectForUpdate(200L)).thenReturn(existing);
        when(versionMapper.selectById(300L)).thenReturn(previous);
        when(versionMapper.selectList(any())).thenReturn(List.of(previous));
        when(versionMapper.selectMaxVersionNo(200L)).thenReturn(1);
        when(versionMapper.insert(any())).thenAnswer(invocation -> {
            ((ProjectDocumentVersion) invocation.getArgument(0)).setId(301L); return 1;
        });
        when(versionMapper.markSuperseded(300L, 301L)).thenReturn(0);

        DocumentIncomingPublishRequest request = new DocumentIncomingPublishRequest();
        request.setExpectedVersion(0);
        request.setItems(List.of(incomingItemRequest(101L, "A-01", "NEW_VERSION", 200L)));

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.publishIncoming(10L, request, user, null));

        assertEquals(409, conflict.getCode());
        verify(documentMapper, never()).updateById(any());
        verify(batchMapper, never()).insert(any());
        verify(incomingBatchMapper, never()).updateById(any());
    }

    private DocumentIncomingBatch incomingBatch() {
        DocumentIncomingBatch incoming = new DocumentIncomingBatch();
        incoming.setId(10L); incoming.setProjectId(3L); incoming.setIncomingNo("SW-10");
        incoming.setStatus("DRAFT"); incoming.setVersion(0);
        return incoming;
    }

    private FileResource pendingFile(Long id) {
        FileResource file = new FileResource();
        file.setId(id); file.setProjectId(3L); file.setBusinessType("DOCUMENT_INCOMING_PENDING");
        file.setBusinessId(10L); file.setUploaderId(8L); file.setStatus(FileStatus.UPLOADED); file.setDeleted(0);
        return file;
    }

    private DocumentIncomingItemRequest incomingItemRequest(Long fileId, String documentNo,
                                                             String matchMode, Long targetDocumentId) {
        DocumentIncomingItemRequest request = new DocumentIncomingItemRequest();
        request.setFileResourceId(fileId); request.setTitle("图纸" + documentNo); request.setDocumentNo(documentNo);
        request.setDocumentType("DRAWING"); request.setExternalRevision("Rev.B");
        request.setMatchMode(matchMode); request.setTargetDocumentId(targetDocumentId);
        return request;
    }
}
