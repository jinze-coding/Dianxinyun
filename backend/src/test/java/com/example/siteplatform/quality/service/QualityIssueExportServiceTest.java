package com.example.siteplatform.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.storage.FileStorageManager;
import com.example.siteplatform.notification.service.UserNotificationService;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.quality.dto.QualityIssueExportRequest;
import com.example.siteplatform.quality.entity.QualityIssue;
import com.example.siteplatform.quality.entity.QualityIssueExportJob;
import com.example.siteplatform.quality.mapper.QualityIssueExportJobItemMapper;
import com.example.siteplatform.quality.mapper.QualityIssueExportJobMapper;
import com.example.siteplatform.quality.mapper.QualityIssueMapper;
import com.example.siteplatform.quality.mapper.QualityWeeklyInspectionMapper;
import com.example.siteplatform.quality.vo.QualityIssueExportJobVO;
import org.apache.poi.ss.usermodel.PictureData;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFSheet;
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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityIssueExportServiceTest {

    @Mock private QualityIssueService issueService;
    @Mock private SysUserMapper userMapper;
    @Mock private QualityIssueMapper issueMapper;
    @Mock private QualityIssueExportJobMapper jobMapper;
    @Mock private QualityIssueExportJobItemMapper itemMapper;
    @Mock private QualityWeeklyInspectionMapper weeklyInspectionMapper;
    @Mock private FileResourceMapper fileMapper;
    @Mock private FileStorageManager storageManager;
    @Mock private ProjectInfoMapper projectInfoMapper;
    @Mock private ProjectPermissionService projectPermissionService;
    @Mock private UserNotificationService notificationService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private QualityIssueExportService service;

    private SysUser user;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setId(7L);
        user.setUsername("quality-user");
        ReflectionTestUtils.setField(service, "maxIssues", 300);
        ReflectionTestUtils.setField(service, "maxPhotos", 800);
        ReflectionTestUtils.setField(service, "maxPhotoBytes", 120L * 1024 * 1024);
    }

    @Test
    void createFreezesTheMatchedIssueIdsAndSerializesThePerUserActiveLimit() {
        QualityIssue first = issue(11L, LocalDate.now().minusDays(1));
        QualityIssue second = issue(12L, LocalDate.now());
        QualityIssueExportRequest request = request();
        when(issueService.listIssueEntitiesForExport(
                request.getProjectId(), "ALL", null, "ALL",
                request.getStartDate(), request.getEndDate(), user)).thenReturn(List.of(first, second));
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(jobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(fileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(jobMapper.insert(any())).thenAnswer(invocation -> {
            QualityIssueExportJob job = invocation.getArgument(0);
            job.setId(90L);
            return 1;
        });
        when(itemMapper.insert(any())).thenReturn(1);

        QualityIssueExportJobVO result = service.create(request, user);

        assertEquals(90L, result.getId());
        assertEquals(2, result.getIssueCount());
        verify(userMapper).selectByIdForUpdate(7L);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(any());
    }

    @Test
    void createRejectsMoreThanTwoActiveJobsAfterTakingTheUserLock() {
        QualityIssueExportRequest request = request();
        when(issueService.listIssueEntitiesForExport(
                request.getProjectId(), "ALL", null, "ALL",
                request.getStartDate(), request.getEndDate(), user)).thenReturn(List.of());
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user);
        when(jobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request, user));

        assertEquals(409, error.getCode());
        verify(jobMapper, never()).insert(any());
    }

    @Test
    void createRejects301IssuesBeforeWritingTheJob() {
        QualityIssueExportRequest request = request();
        List<QualityIssue> issues = new ArrayList<>();
        for (long id = 1; id <= 301; id++) issues.add(issue(id, LocalDate.now()));
        when(issueService.listIssueEntitiesForExport(
                request.getProjectId(), "ALL", null, "ALL",
                request.getStartDate(), request.getEndDate(), user)).thenReturn(issues);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request, user));

        assertEquals(413, error.getCode());
        verify(userMapper, never()).selectByIdForUpdate(any());
        verify(jobMapper, never()).insert(any());
    }

    @Test
    void schedulerSkipsCleanlyUntilTheQualityExportMigrationIsApplied() {
        when(jobMapper.countAppliedMigration()).thenReturn(0);

        service.processPendingJobs();

        verify(jobMapper, never()).selectNextPendingForUpdate();
    }

    @Test
    void jpegAndPngPayloadsKeepTheOriginalBytesWithoutThumbnailReplacement() throws Exception {
        QualityIssue issue = issue(11L, LocalDate.now());
        byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, 1, 2, 3, (byte) 0xff, (byte) 0xd9};
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 4, 5, 6};

        assertArrayEquals(jpeg, payloadBytes(file("jpg"), jpeg, issue));
        assertArrayEquals(png, payloadBytes(file("png"), png, issue));
    }

    @Test
    void workbookKeepsEachIssueWithItsPhotosInTheDetailColumnsAndTwoSheetLayout() throws Exception {
        LocalDate recordDate = LocalDate.of(2026, 8, 28);
        QualityIssue first = issue(11L, recordDate);
        first.setTitle("首个问题");
        first.setRectificationPhotoFileIds("4");
        QualityIssue second = issue(12L, recordDate);
        second.setTitle("只有整改前照片");
        QualityIssue third = issue(13L, recordDate);
        third.setTitle("没有照片");

        byte[] firstBeforePng = image("png", Color.BLUE);
        byte[] firstAfterJpeg = image("jpg", Color.GREEN);
        byte[] secondBeforeJpeg = image("jpg", Color.RED);
        byte[] firstBeforeJpeg = image("jpg", Color.ORANGE);
        FileResource beforeOne = file(1L, 11L, "QUALITY_ISSUE", "png", firstBeforePng);
        FileResource beforeTwo = file(2L, 11L, "QUALITY_ISSUE", "jpg", firstBeforeJpeg);
        FileResource beforeThree = file(3L, 12L, "QUALITY_ISSUE", "jpg", secondBeforeJpeg);
        FileResource afterOne = file(4L, 11L, "QUALITY_RECTIFICATION", "jpg", firstAfterJpeg);
        when(fileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(beforeOne, beforeTwo, beforeThree));
        when(fileMapper.selectBatchIds(any())).thenReturn(List.of(afterOne));
        Map<Long, byte[]> bytesByFileId = Map.of(
                1L, firstBeforePng,
                2L, firstBeforeJpeg,
                3L, secondBeforeJpeg,
                4L, firstAfterJpeg);
        when(storageManager.load(any(FileResource.class))).thenAnswer(invocation -> {
            FileResource requested = invocation.getArgument(0);
            return new ByteArrayResource(bytesByFileId.get(requested.getId()));
        });

        List<QualityIssue> issues = List.of(first, second, third);
        Object photoPlan = ReflectionTestUtils.invokeMethod(service, "loadPhotoPlan", issues, true);
        QualityIssueExportJob job = new QualityIssueExportJob();
        job.setStartDate(recordDate);
        job.setEndDate(recordDate);
        job.setIssueSource("ALL");
        job.setIssueStatus("ALL");
        job.setRequestedByName("质量员");
        ProjectInfo project = new ProjectInfo();
        project.setProjectName("测试项目");
        Path target = Files.createTempFile("quality-inline-photo-test-", ".xlsx");
        try {
            ReflectionTestUtils.invokeMethod(service, "buildWorkbook",
                    target, project, job, issues, Map.of(), photoPlan);

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(Files.readAllBytes(target)))) {
                assertEquals(List.of("按日汇总", "问题明细"),
                        IntStream.range(0, workbook.getNumberOfSheets())
                                .mapToObj(workbook::getSheetName).toList());
                assertEquals(2, workbook.getNumberOfSheets());
                assertEquals(-1, workbook.getSheetIndex("照片对比"));

                XSSFSheet detail = workbook.getSheet("问题明细");
                assertIssueRows(detail, 1, 2, "Q-11");
                assertIssueRows(detail, 3, 3, "Q-12");
                assertIssueRows(detail, 4, 4, "Q-13");
                assertEquals("整改前照片", detail.getRow(0).getCell(18).getStringCellValue());
                assertEquals("最新整改后照片", detail.getRow(0).getCell(19).getStringCellValue());
                assertEquals("尚无最新整改后照片", detail.getRow(3).getCell(19).getStringCellValue());
                assertEquals("无", detail.getRow(4).getCell(18).getStringCellValue());
                assertEquals("尚无最新整改后照片", detail.getRow(4).getCell(19).getStringCellValue());
                assertEquals(110F, detail.getRow(1).getHeightInPoints(), 0.01F);
                assertEquals(110F, detail.getRow(2).getHeightInPoints(), 0.01F);
                assertEquals(110F, detail.getRow(3).getHeightInPoints(), 0.01F);
                assertEquals(56F, detail.getRow(4).getHeightInPoints(), 0.01F);
                assertEquals(30 * 256, detail.getColumnWidth(18));
                assertEquals(30 * 256, detail.getColumnWidth(19));

                XSSFDrawing drawing = detail.getDrawingPatriarch();
                assertNotNull(drawing);
                List<XSSFPicture> pictures = drawing.getShapes().stream()
                        .map(XSSFPicture.class::cast).toList();
                assertEquals(4, pictures.size());
                assertAnchor(pictures.get(0), 18, 19, 1, 2);
                assertAnchor(pictures.get(1), 19, 20, 1, 2);
                assertAnchor(pictures.get(2), 18, 19, 2, 3);
                assertAnchor(pictures.get(3), 18, 19, 3, 4);

                List<? extends PictureData> media = workbook.getAllPictures();
                assertEquals(4, media.size());
                assertArrayEquals(firstBeforePng, media.get(0).getData());
                assertArrayEquals(firstAfterJpeg, media.get(1).getData());
                assertArrayEquals(firstBeforeJpeg, media.get(2).getData());
                assertArrayEquals(secondBeforeJpeg, media.get(3).getData());

                assertFalse(detail.getCTWorksheet().isSetAutoFilter());
                assertNotNull(detail.getPaneInformation());
                assertTrue(detail.getPaneInformation().isFreezePane());
                assertEquals(1, detail.getPaneInformation().getHorizontalSplitPosition());
                assertFalse(detail.isDisplayGridlines());
                assertFalse(detail.isPrintGridlines());
                assertTrue(detail.getPrintSetup().getLandscape());
                assertEquals(1, detail.getPrintSetup().getFitWidth());
                assertEquals(0, detail.getPrintSetup().getFitHeight());
                assertTrue(detail.getCTWorksheet().getSheetPr().getPageSetUpPr().getFitToPage());
                assertEquals(new CellRangeAddress(0, 0, -1, -1), detail.getRepeatingRows());
                assertEquals(0, detail.getRowBreaks().length);
            }
        } finally {
            Files.deleteIfExists(target);
        }
    }

    private byte[] payloadBytes(FileResource file, byte[] original, QualityIssue issue) throws Exception {
        Object payload = ReflectionTestUtils.invokeMethod(service, "picturePayload", file, original, issue);
        Method bytes = payload.getClass().getDeclaredMethod("bytes");
        bytes.setAccessible(true);
        return (byte[]) bytes.invoke(payload);
    }

    private QualityIssueExportRequest request() {
        QualityIssueExportRequest request = new QualityIssueExportRequest();
        request.setProjectId(9L);
        request.setStartDate(LocalDate.now().withDayOfMonth(1));
        request.setEndDate(LocalDate.now());
        request.setSource("ALL");
        request.setStatus("ALL");
        return request;
    }

    private QualityIssue issue(Long id, LocalDate recordDate) {
        QualityIssue issue = new QualityIssue();
        issue.setId(id);
        issue.setProjectId(9L);
        issue.setIssueNo("Q-" + id);
        issue.setRecordDate(recordDate);
        issue.setStatus(QualityIssueService.STATUS_PENDING);
        return issue;
    }

    private void assertIssueRows(XSSFSheet detail, int firstRow, int lastRow, String issueNo) {
        assertEquals(issueNo, detail.getRow(firstRow).getCell(2).getStringCellValue());
        if (firstRow == lastRow) {
            return;
        }
        for (int column = 0; column < 18; column++) {
            CellRangeAddress expected = new CellRangeAddress(firstRow, lastRow, column, column);
            assertTrue(detail.getMergedRegions().contains(expected),
                    () -> "问题字段必须在照片行内纵向合并：" + expected.formatAsString());
        }
    }

    private void assertAnchor(XSSFPicture picture, int col1, int col2, int row1, int row2) {
        XSSFClientAnchor anchor = picture.getClientAnchor();
        assertEquals(col1, anchor.getCol1());
        assertEquals(col2, anchor.getCol2());
        assertEquals(row1, anchor.getRow1());
        assertEquals(row2, anchor.getRow2());
    }

    private byte[] image(String format, Color color) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }

    private FileResource file(Long id, Long issueId, String businessType,
                              String extension, byte[] bytes) {
        FileResource file = file(extension);
        file.setId(id);
        file.setProjectId(9L);
        file.setBusinessId(issueId);
        file.setBusinessType(businessType);
        file.setStatus("UPLOADED");
        file.setDeleted(0);
        file.setFileSize((long) bytes.length);
        file.setOriginalFileName("photo-" + id + "." + extension);
        return file;
    }

    private FileResource file(String extension) {
        FileResource file = new FileResource();
        file.setId(1L);
        file.setFileExtension(extension);
        file.setOriginalFileName("photo." + extension);
        return file;
    }
}
