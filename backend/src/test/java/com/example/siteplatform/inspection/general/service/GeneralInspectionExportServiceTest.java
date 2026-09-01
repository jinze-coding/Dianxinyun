package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.inspection.general.dto.GeneralInspectionExportRequest;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionExportJob;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionRectification;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTask;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionTaskItem;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.EdgeInspectionExportJobVO;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneralInspectionExportServiceTest {
    @Mock private GeneralInspectionPermissionService permissionService;
    @Mock private SysUserMapper userMapper;
    @Mock private GeneralInspectionExportJobMapper exportJobMapper;
    @Mock private GeneralInspectionExportJobTaskMapper exportJobTaskMapper;
    @Mock private GeneralInspectionPointMapper pointMapper;
    @Mock private GeneralInspectionTaskMapper taskMapper;
    @Mock private GeneralInspectionTaskItemMapper taskItemMapper;
    @Mock private GeneralInspectionRectificationMapper rectificationMapper;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;
    @Mock private UserNotificationService notificationService;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private GeneralInspectionExportService service;

    private SysUser user;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setId(7L);
        user.setRealName("安全员");
        ReflectionTestUtils.setField(service, "maxTasks", 500);
        ReflectionTestUtils.setField(service, "maxPhotos", 800);
        ReflectionTestUtils.setField(service, "maxPhotoBytes", 120L * 1024 * 1024);
    }

    @Test
    void createIncludesCancelledRowsAndFreezesTheSelectedTaskIds() {
        GeneralInspectionExportRequest request = request();
        GeneralInspectionTask first = task(11L, "COMPLETED");
        GeneralInspectionTask cancelled = task(12L, "CANCELLED");
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(exportJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, cancelled));
        when(taskItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(rectificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(exportJobMapper.insert(any())).thenAnswer(invocation -> {
            GeneralInspectionExportJob job = invocation.getArgument(0);
            job.setId(90L);
            return 1;
        });
        when(exportJobTaskMapper.insert(any())).thenReturn(1);

        EdgeInspectionExportJobVO result = service.create(request, user);

        assertThat(result.getTaskCount()).isEqualTo(2);
        assertThat(result.getPointCount()).isEqualTo(1);
        verify(permissionService).requireExport(9L, user);
        verify(userMapper).selectByIdForUpdate(7L);
        verify(exportJobTaskMapper, times(2)).insert(any());
    }

    @Test
    void createRejectsFutureEndDateBeforeAnyDatabaseWrite() {
        GeneralInspectionExportRequest request = request();
        request.setEndDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> service.create(request, user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能晚于今天");

        verify(exportJobMapper, never()).insert(any());
    }

    @Test
    void listDetailAndDownloadAllPassThroughTheDedicatedEdgeExportGate() throws Exception {
        GeneralInspectionExportJob job = new GeneralInspectionExportJob();
        job.setId(90L);
        job.setProjectId(9L);
        job.setRequestedById(7L);
        job.setExportType("EDGE");
        job.setStatus("SUCCEEDED");
        job.setFileResourceId(80L);
        job.setExpiresTime(LocalDateTime.now().plusDays(1));
        FileResource exportFile = file(80L);
        exportFile.setDeleted(0);
        when(exportJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(job));
        when(exportJobMapper.selectById(90L)).thenReturn(job);
        when(permissionService.isPlatformAdmin(user)).thenReturn(false);
        when(fileMapper.selectById(80L)).thenReturn(exportFile);
        when(storageManager.load(exportFile)).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));

        assertThat(service.list(9L, user)).hasSize(1);
        assertThat(service.get(90L, user).getId()).isEqualTo(90L);
        assertThat(service.download(90L, user).resource().contentLength()).isEqualTo(3L);

        verify(permissionService, times(3)).requireExport(9L, user);
    }

    @Test
    void apachePoiCanReadSummaryPointPhotoAndRectificationSheetsWithThumbnails() throws Exception {
        GeneralInspectionTask task = task(11L, "CLOSED");
        task.setSubmittedTime(LocalDateTime.now().minusHours(1));
        task.setSubmittedByName("张勇");
        task.setOnTime(1);
        task.setOverallPhotoFileIds("1");
        task.setAbnormalCount(1);
        List<GeneralInspectionTaskItem> items = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            GeneralInspectionTaskItem item = new GeneralInspectionTaskItem();
            item.setId((long) index);
            item.setTaskId(task.getId());
            item.setItemKey("ITEM_0" + index);
            item.setItemName("固定检查项" + index);
            item.setSortOrder(index);
            item.setResult(index == 1 ? "ABNORMAL" : "NORMAL");
            if (index == 1) {
                item.setDescription("防护松动");
                item.setPhotoFileIds("2");
            }
            items.add(item);
        }
        GeneralInspectionRectification rectification = new GeneralInspectionRectification();
        rectification.setId(21L);
        rectification.setTaskId(task.getId());
        rectification.setTaskItemId(1L);
        rectification.setPointName(task.getPointName());
        rectification.setItemName("固定检查项1");
        rectification.setStatus("CLOSED");
        rectification.setRectificationPhotoFileIds("3");
        when(taskItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(items);
        when(rectificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rectification));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(file(1L), file(2L), file(3L)));
        when(storageManager.load(any(FileResource.class))).thenReturn(new ByteArrayResource(png()));

        Object data = ReflectionTestUtils.invokeMethod(service, "loadExportData", List.of(task));
        GeneralInspectionExportJob job = new GeneralInspectionExportJob();
        job.setStartDate(task.getOccurrenceDate());
        job.setEndDate(task.getOccurrenceDate());
        job.setRequestedByName("安全员");
        job.setTaskCount(1);
        job.setPhotoCount(3);
        byte[] bytes = ReflectionTestUtils.invokeMethod(service, "buildWorkbook",
                "北蔡小学", job, List.of(task), data);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("巡检汇总")).isNotNull();
            assertThat(workbook.getSheet("照片明细")).isNotNull();
            assertThat(workbook.getSheet("整改明细")).isNotNull();
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("照片明细").getDrawingPatriarch().getShapes()).hasSize(3);
            assertThat(workbook.getSheetAt(1).getDrawingPatriarch().getShapes()).hasSize(1);
        }
    }

    private GeneralInspectionExportRequest request() {
        GeneralInspectionExportRequest request = new GeneralInspectionExportRequest();
        request.setProjectId(9L);
        request.setStartDate(LocalDate.now().withDayOfMonth(1));
        request.setEndDate(LocalDate.now());
        request.setPointIds(List.of());
        return request;
    }

    private GeneralInspectionTask task(Long id, String status) {
        GeneralInspectionTask task = new GeneralInspectionTask();
        task.setId(id);
        task.setProjectId(9L);
        task.setPointId(100L);
        task.setPointCode("EDGE-001");
        task.setPointName("楼层东侧临边");
        task.setPointTypeCode("FLOOR_BALCONY_EAVE_EDGE");
        task.setPointTypeName("楼层、阳台及挑檐边");
        task.setTemplateName("楼层临边巡检表");
        task.setOccurrenceDate(LocalDate.now());
        task.setStartTime(LocalDate.now().atTime(8, 0));
        task.setDueTime(LocalDate.now().atTime(18, 0));
        task.setStatus(status);
        task.setAbnormalCount(0);
        return task;
    }

    private FileResource file(Long id) {
        FileResource file = new FileResource();
        file.setId(id);
        file.setFileSize(1024L);
        file.setOriginalFileName("演示照片" + id + ".png");
        file.setDeleted(0);
        return file;
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
