package com.example.siteplatform.electricbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.dto.ElectricBoxLifecycleRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrPrintLogRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxQrRebindRequest;
import com.example.siteplatform.electricbox.dto.ElectricBoxRequest;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.entity.ElectricBoxQrLog;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.mapper.ElectricBoxQrLogMapper;
import com.example.siteplatform.electricbox.vo.ElectricBoxImportResultVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxImportRowVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxQrLogVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxVO;
import com.example.siteplatform.electricbox.vo.ElectricBoxUnifiedCodeVO;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectMemberService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ElectricBoxService {

    private static final int BOX_CODE_MAX_LENGTH = 64;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_REMOVED = "REMOVED";
    private static final String QR_BOUND = "BOUND";
    private static final String QR_DISABLED = "DISABLED";
    private static final String QR_REPLACED = "REPLACED";
    private static final String QR_TYPE_INTERNAL = "INTERNAL";
    private static final String QR_TYPE_PUBLIC = "PUBLIC";
    private static final String QR_TYPE_UNIFIED = "UNIFIED";
    private static final String ACTION_GENERATE = "GENERATE";
    private static final String ACTION_PRINT = "PRINT";
    private static final String ACTION_REBIND = "REBIND";
    private static final String ACTION_DISABLE = "DISABLE";
    private static final String ACTION_REMOVE = "REMOVE";

    @Autowired
    private ElectricBoxMapper electricBoxMapper;

    @Autowired
    private ElectricBoxQrLogMapper qrLogMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private InspectionRecordMapper inspectionRecordMapper;

    @Autowired
    private InspectionRectificationMapper inspectionRectificationMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ElectricBoxInspectionScopeService inspectionScopeService;

    @Autowired
    private WechatPlatformClient wechatPlatformClient;

    @Value("${wechat.mini-program.page:pages/scan-entry/index}")
    private String miniProgramPage;

    @Value("${wechat.mini-program.env-version:release}")
    private String miniProgramEnvVersion;

    @Value("${wechat.mini-program.public-fallback-url:http://localhost:3003/#/pages/scan-entry/index}")
    private String publicFallbackUrl;

    public List<ElectricBoxVO> list(Long projectId, String status, SysUser currentUser) {
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                    SystemPermissionCodes.INSPECTION_VIEW);
        }
        LambdaQueryWrapper<ElectricBox> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(ElectricBox::getProjectId, projectId);
        } else if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            List<ProjectInfo> projects = projectPermissionService.getUserProjects(currentUser.getId());
            if (projects.isEmpty()) {
                return List.of();
            }
            wrapper.in(ElectricBox::getProjectId, projects.stream().map(ProjectInfo::getId).toList());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ElectricBox::getStatus, trimToNull(status));
        }
        if (projectId != null && !projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.BOX_VIEW)) {
            wrapper.eq(ElectricBox::getResponsibleElectricianId, currentUser.getId());
        }
        wrapper.orderByAsc(ElectricBox::getBoxCode);
        return electricBoxMapper.selectList(wrapper).stream()
                .filter(box -> canViewBox(box, currentUser))
                .map(this::toVO)
                .toList();
    }

    public ElectricBoxVO getById(Long id, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), box.getProjectId());
        requireBoxViewPermission(box, currentUser);
        return toVO(box);
    }

    public ElectricBoxVO resolveQrCode(String rawQrCode, SysUser currentUser) {
        String qrCode = normalizeScannedCode(rawQrCode);
        ElectricBox box = electricBoxMapper.selectOne(new LambdaQueryWrapper<ElectricBox>()
                .and(wrapper -> wrapper.eq(ElectricBox::getQrCode, qrCode)
                        .or()
                        .eq(ElectricBox::getBoxCode, qrCode))
                .last("LIMIT 1"));
        if (box == null) {
            if (isRetiredQrCode(null, qrCode)) {
                throw BusinessException.of(410, "二维码已换绑或停用，请扫描现场最新二维码");
            }
            throw BusinessException.notFound("二维码未绑定电箱");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), box.getProjectId());
        requireBoxViewPermission(box, currentUser);
        return toVO(box);
    }

    @Transactional
    public ElectricBoxVO create(ElectricBoxRequest request, SysUser currentUser) {
        normalizeRequest(request);
        validateRequest(request);
        requireManagePermission(currentUser, request.getProjectId());
        ensureUnique(request.getProjectId(), request.getBoxCode(), request.getQrCode(), null);

        ElectricBox box = new ElectricBox();
        BeanUtils.copyProperties(request, box);
        box.setQrStatus(StringUtils.hasText(request.getQrStatus()) ? request.getQrStatus() : QR_BOUND);
        box.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : STATUS_ACTIVE);
        box.setPublicAccessEnabled(request.getPublicAccessEnabled() == null ? 1 : request.getPublicAccessEnabled());
        if (!StringUtils.hasText(box.getQrCode())) {
            box.setQrCode(generateInternalQrCode());
        }
        box.setPublicCode(generatePublicCode());
        box.setDeleted(0);
        box.setCreateTime(LocalDateTime.now());
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.insert(box);
        ensureBoxAssignees(box);
        recordQrLog(box, ACTION_GENERATE, QR_TYPE_INTERNAL, null, box.getQrCode(), currentUser, "新增电箱生成或绑定内部二维码");
        recordQrLog(box, ACTION_GENERATE, QR_TYPE_PUBLIC, null, box.getPublicCode(), currentUser, "新增电箱生成公开只读码");
        return toVO(box);
    }

    @Transactional
    public ElectricBoxVO update(Long id, ElectricBoxRequest request, SysUser currentUser) {
        ElectricBox existing = requireBox(id);
        requireManagePermission(currentUser, existing.getProjectId());
        normalizeRequest(request);
        Long projectId = request.getProjectId() != null ? request.getProjectId() : existing.getProjectId();
        if (!Objects.equals(projectId, existing.getProjectId())) {
            requireManagePermission(currentUser, projectId);
        }
        String boxCode = StringUtils.hasText(request.getBoxCode()) ? request.getBoxCode() : existing.getBoxCode();
        String qrCode = StringUtils.hasText(request.getQrCode()) ? request.getQrCode() : existing.getQrCode();
        Integer publicAccessEnabled = request.getPublicAccessEnabled() != null
                ? request.getPublicAccessEnabled()
                : existing.getPublicAccessEnabled();
        validateBoxCode(boxCode);
        if (!StringUtils.hasText(request.getInstallLocation()) && !StringUtils.hasText(existing.getInstallLocation())) {
            throw new BusinessException("安装位置不能为空");
        }
        ensureUnique(projectId, boxCode, qrCode, id);

        String oldQrCode = existing.getQrCode();
        BeanUtils.copyProperties(request, existing, "id", "publicCode", "deleted", "createTime");
        existing.setProjectId(projectId);
        existing.setBoxCode(boxCode);
        existing.setQrCode(qrCode);
        existing.setPublicAccessEnabled(publicAccessEnabled == null ? 1 : publicAccessEnabled);
        existing.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(existing);
        ensureBoxAssignees(existing);
        if (StringUtils.hasText(qrCode) && StringUtils.hasText(oldQrCode) && !Objects.equals(qrCode, oldQrCode)) {
            recordQrLog(existing, ACTION_REBIND, QR_TYPE_INTERNAL, oldQrCode, qrCode, currentUser, "编辑电箱二维码编码");
        }
        return toVO(existing);
    }

    @Transactional
    public ElectricBoxVO disable(Long id, ElectricBoxLifecycleRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireQrManagePermission(currentUser, box.getProjectId());
        String oldQrCode = box.getQrCode();
        box.setStatus(STATUS_INACTIVE);
        box.setQrStatus(QR_DISABLED);
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(box);
        recordQrLog(box, ACTION_DISABLE, QR_TYPE_INTERNAL, oldQrCode, oldQrCode, currentUser, resolveReason(request, "停用电箱和内部二维码"));
        return toVO(box);
    }

    @Transactional
    public ElectricBoxVO remove(Long id, ElectricBoxLifecycleRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireQrManagePermission(currentUser, box.getProjectId());
        int pendingRectificationCount = countOpenRectifications(box.getId());
        if (pendingRectificationCount > 0) {
            throw new BusinessException("该电箱存在未闭环整改，需先完成整改后再拆除");
        }
        String oldQrCode = box.getQrCode();
        String oldPublicCode = box.getPublicCode();
        box.setStatus(STATUS_REMOVED);
        box.setQrStatus(QR_DISABLED);
        box.setPublicAccessEnabled(0);
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(box);
        String reason = resolveReason(request, "拆除电箱，禁用内部巡检码并关闭公开扫码");
        recordQrLog(box, ACTION_REMOVE, QR_TYPE_INTERNAL, oldQrCode, oldQrCode, currentUser, reason);
        recordQrLog(box, ACTION_REMOVE, QR_TYPE_PUBLIC, oldPublicCode, oldPublicCode, currentUser, reason);
        return toVO(box);
    }

    @Transactional
    public ElectricBoxVO rebindQrCode(Long id, ElectricBoxQrRebindRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requirePublicAccessPermission(currentUser, box.getProjectId());
        if (STATUS_REMOVED.equals(box.getStatus())) {
            throw new BusinessException("已拆除电箱不可换绑二维码");
        }
        String newQrCode = request == null ? null : trimToNull(request.getQrCode());
        if (!StringUtils.hasText(newQrCode)) {
            newQrCode = generateInternalQrCode();
        }
        validateQrCode(newQrCode);
        if (Objects.equals(newQrCode, box.getQrCode())) {
            throw new BusinessException("新二维码编码不能与当前编码相同");
        }
        ensureUnique(box.getProjectId(), box.getBoxCode(), newQrCode, box.getId());
        String oldQrCode = box.getQrCode();
        box.setQrCode(newQrCode);
        box.setQrStatus(QR_BOUND);
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(box);
        recordQrLog(box, ACTION_REBIND, QR_TYPE_INTERNAL, oldQrCode, newQrCode, currentUser, request == null ? null : request.getReason());
        return toVO(box);
    }

    public ElectricBoxUnifiedCodeVO getUnifiedCode(Long id, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireQrManagePermission(currentUser, box.getProjectId());
        String sceneCode = "B:" + box.getPublicCode();
        String officialImage = wechatPlatformClient.generateUnlimitedCode(sceneCode, miniProgramPage, miniProgramEnvVersion);
        String resolvedFallbackUrl = resolveDevelopmentFallbackUrl();
        String separator = resolvedFallbackUrl.contains("?") ? "&" : "?";
        String fallbackPayload = resolvedFallbackUrl + separator + "scene=" + java.net.URLEncoder.encode(sceneCode, StandardCharsets.UTF_8);
        ElectricBoxUnifiedCodeVO vo = new ElectricBoxUnifiedCodeVO();
        vo.setElectricBoxId(box.getId());
        vo.setBoxCode(box.getBoxCode());
        vo.setBoxName(box.getBoxName());
        vo.setSceneCode(sceneCode);
        vo.setPublicCode(box.getPublicCode());
        vo.setCodeType(officialImage == null ? "DEVELOPMENT_H5_QR" : "WECHAT_MINI_PROGRAM_CODE");
        vo.setImageMimeType(officialImage == null ? "image/svg+xml" : "image/png");
        vo.setImageContent(officialImage == null ? generateQrSvg(fallbackPayload) : officialImage);
        vo.setHint(officialImage == null
                ? "开发预览码，目标地址：" + fallbackPayload + "。手机需与电脑处于同一局域网，且小程序H5预览服务已启动"
                : "正式微信小程序码：内部巡检与外部月表共用同一码");
        return vo;
    }

    private String resolveDevelopmentFallbackUrl() {
        String configured = StringUtils.hasText(publicFallbackUrl)
                ? publicFallbackUrl.trim()
                : "http://localhost:3003/#/pages/scan-entry/index";
        if (!configured.contains("localhost") && !configured.contains("127.0.0.1")) {
            return configured;
        }
        String lanAddress = findLanIpv4Address();
        if (!StringUtils.hasText(lanAddress)) {
            return configured;
        }
        return configured
                .replace("localhost", lanAddress)
                .replace("127.0.0.1", lanAddress);
    }

    private String findLanIpv4Address() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            interfaces.sort(Comparator.comparingInt(item -> item.getName().startsWith("en") ? 0 : 1));
            for (NetworkInterface networkInterface : interfaces) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // 无可用局域网地址时保留显式配置，不影响正式小程序码生成。
        }
        return null;
    }

    @Transactional
    public ElectricBoxUnifiedCodeVO rotateUnifiedCode(Long id, ElectricBoxQrRebindRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireQrManagePermission(currentUser, box.getProjectId());
        if (STATUS_REMOVED.equals(box.getStatus())) {
            throw new BusinessException("已拆除电箱不可换码");
        }
        String oldPublicCode = box.getPublicCode();
        box.setPublicCode(generatePublicCode());
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(box);
        recordQrLog(box, ACTION_REBIND, QR_TYPE_UNIFIED, oldPublicCode, box.getPublicCode(), currentUser,
                request == null ? null : request.getReason());
        return getUnifiedCode(id, currentUser);
    }

    @Transactional
    public List<ElectricBoxQrLogVO> recordPrintLog(Long id, ElectricBoxQrPrintLogRequest request, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireManagePermission(currentUser, box.getProjectId());
        List<String> qrTypes = normalizeQrTypes(request == null ? null : request.getQrTypes());
        List<ElectricBoxQrLogVO> result = new ArrayList<>();
        for (String qrType : qrTypes) {
            String value = (QR_TYPE_PUBLIC.equals(qrType) || QR_TYPE_UNIFIED.equals(qrType)) ? box.getPublicCode() : box.getQrCode();
            ElectricBoxQrLog log = recordQrLog(box, ACTION_PRINT, qrType, null, value, currentUser,
                    request == null ? null : request.getReason());
            result.add(toLogVO(log));
        }
        return result;
    }

    public List<ElectricBoxQrLogVO> listQrLogs(Long id, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), box.getProjectId());
        requireBoxViewPermission(box, currentUser);
        return qrLogMapper.selectList(new LambdaQueryWrapper<ElectricBoxQrLog>()
                        .eq(ElectricBoxQrLog::getElectricBoxId, id)
                        .orderByDesc(ElectricBoxQrLog::getCreateTime)
                        .orderByDesc(ElectricBoxQrLog::getId))
                .stream()
                .map(this::toLogVO)
                .toList();
    }

    @Transactional
    public ElectricBoxVO setPublicAccess(Long id, boolean enabled, SysUser currentUser) {
        ElectricBox box = requireBox(id);
        requireManagePermission(currentUser, box.getProjectId());
        if (STATUS_REMOVED.equals(box.getStatus()) && enabled) {
            throw new BusinessException("已拆除电箱不可启用公开扫码");
        }
        box.setPublicAccessEnabled(enabled ? 1 : 0);
        box.setUpdateTime(LocalDateTime.now());
        electricBoxMapper.updateById(box);
        return toVO(box);
    }

    public byte[] buildImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("电箱台账导入");
            Row header = sheet.createRow(0);
            String[] headers = {
                    "电箱编号*", "电箱名称", "安装位置*", "负责电工账号", "负责电工姓名",
                    "安全负责人账号", "安全负责人姓名", "内部二维码编码", "公开扫码启用", "备注"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, i == 2 ? 6000 : 4200);
            }
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("EB-001");
            sample.createCell(1).setCellValue("二级电箱 1");
            sample.createCell(2).setCellValue("一层东侧材料通道");
            sample.createCell(3).setCellValue("electrician_001");
            sample.createCell(4).setCellValue("张电工");
            sample.createCell(5).setCellValue("safety_001");
            sample.createCell(6).setCellValue("王安全");
            sample.createCell(7).setCellValue("");
            sample.createCell(8).setCellValue("是");
            sample.createCell(9).setCellValue("示例行可删除");
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("导入模板生成失败：" + e.getMessage());
        }
    }

    @Transactional
    public ElectricBoxImportResultVO importBoxes(Long projectId, MultipartFile file, boolean dryRun, SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        requireManagePermission(currentUser, projectId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }
        List<ImportRow> rows = parseImportRows(file);
        ElectricBoxImportResultVO result = validateImportRows(projectId, rows);
        if (dryRun || result.getErrorRows() > 0) {
            return result;
        }
        for (int i = 0; i < rows.size(); i++) {
            ImportRow row = rows.get(i);
            ElectricBoxImportRowVO rowVO = result.getRows().get(i);
            ElectricBoxRequest request = new ElectricBoxRequest();
            request.setProjectId(projectId);
            request.setBoxCode(row.boxCode);
            request.setBoxName(row.boxName);
            request.setInstallLocation(row.installLocation);
            request.setResponsibleElectricianId(row.responsibleElectricianId);
            request.setResponsibleElectricianName(StringUtils.hasText(row.responsibleElectricianName) ? row.responsibleElectricianName : row.responsibleElectricianResolvedName);
            request.setSafetyManagerId(row.safetyManagerId);
            request.setSafetyManagerName(StringUtils.hasText(row.safetyManagerName) ? row.safetyManagerName : row.safetyManagerResolvedName);
            request.setQrCode(row.qrCode);
            request.setStatus(STATUS_ACTIVE);
            request.setQrStatus(QR_BOUND);
            request.setPublicAccessEnabled(row.publicAccessEnabled);
            request.setRemark(row.remark);
            ElectricBoxVO created = create(request, currentUser);
            rowVO.setElectricBoxId(created.getId());
        }
        result.setSuccessRows(rows.size());
        return result;
    }

    public String generateQrSvg(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new BusinessException("二维码内容不能为空");
        }
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(payload.trim(), BarcodeFormat.QR_CODE, 0, 0, hints);
            int module = 8;
            int size = matrix.getWidth() * module;
            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                    .append(size)
                    .append(' ')
                    .append(size)
                    .append("\" width=\"")
                    .append(size)
                    .append("\" height=\"")
                    .append(size)
                    .append("\" role=\"img\" aria-label=\"QR code\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        svg.append("<rect x=\"")
                                .append(x * module)
                                .append("\" y=\"")
                                .append(y * module)
                                .append("\" width=\"")
                                .append(module)
                                .append("\" height=\"")
                                .append(module)
                                .append("\" fill=\"#111\"/>");
                    }
                }
            }
            svg.append("</svg>");
            return svg.toString();
        } catch (WriterException e) {
            throw new BusinessException("二维码生成失败：" + e.getMessage());
        }
    }

    public ElectricBox requireBox(Long id) {
        ElectricBox box = electricBoxMapper.selectById(id);
        if (box == null) {
            throw BusinessException.notFound("电箱不存在");
        }
        return box;
    }

    private void validateRequest(ElectricBoxRequest request) {
        if (request == null) {
            throw new BusinessException("电箱信息不能为空");
        }
        if (request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        validateBoxCode(request.getBoxCode());
        if (!StringUtils.hasText(request.getInstallLocation())) {
            throw new BusinessException("安装位置不能为空");
        }
        validateQrCode(request.getQrCode());
    }

    private void normalizeRequest(ElectricBoxRequest request) {
        if (request == null) {
            return;
        }
        request.setBoxCode(trimToNull(request.getBoxCode()));
        request.setBoxName(trimToNull(request.getBoxName()));
        request.setInstallLocation(trimToNull(request.getInstallLocation()));
        request.setResponsibleElectricianName(trimToNull(request.getResponsibleElectricianName()));
        request.setSafetyManagerName(trimToNull(request.getSafetyManagerName()));
        request.setQrCode(trimToNull(request.getQrCode()));
        request.setQrStatus(trimToNull(request.getQrStatus()));
        request.setStatus(trimToNull(request.getStatus()));
        request.setRemark(trimToNull(request.getRemark()));
    }

    private void validateBoxCode(String boxCode) {
        if (!StringUtils.hasText(boxCode)) {
            throw new BusinessException("电箱编号不能为空");
        }
        if (boxCode.length() > BOX_CODE_MAX_LENGTH) {
            throw new BusinessException("电箱编号不能超过64个字符");
        }
    }

    private void validateQrCode(String qrCode) {
        if (StringUtils.hasText(qrCode) && qrCode.length() > 100) {
            throw new BusinessException("二维码编码不能超过100个字符");
        }
    }

    private void ensureUnique(Long projectId, String boxCode, String qrCode, Long excludeId) {
        LambdaQueryWrapper<ElectricBox> codeWrapper = new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId)
                .eq(ElectricBox::getBoxCode, boxCode);
        if (excludeId != null) {
            codeWrapper.ne(ElectricBox::getId, excludeId);
        }
        if (electricBoxMapper.selectCount(codeWrapper) > 0) {
            throw new BusinessException("同一项目下电箱编号已存在");
        }

        if (StringUtils.hasText(qrCode)) {
            LambdaQueryWrapper<ElectricBox> qrWrapper = new LambdaQueryWrapper<ElectricBox>()
                    .eq(ElectricBox::getProjectId, projectId)
                    .eq(ElectricBox::getQrCode, qrCode);
            if (excludeId != null) {
                qrWrapper.ne(ElectricBox::getId, excludeId);
            }
            if (electricBoxMapper.selectCount(qrWrapper) > 0) {
                throw new BusinessException("同一项目下二维码编码已存在");
            }
            if (isRetiredQrCode(projectId, qrCode)) {
                throw new BusinessException("该二维码编码已换绑或停用，不可再次绑定");
            }
        }
    }

    private boolean isRetiredQrCode(Long projectId, String qrCode) {
        if (!StringUtils.hasText(qrCode)) {
            return false;
        }
        LambdaQueryWrapper<ElectricBoxQrLog> wrapper = new LambdaQueryWrapper<ElectricBoxQrLog>()
                .eq(ElectricBoxQrLog::getOldQrCode, qrCode)
                .in(ElectricBoxQrLog::getActionType, ACTION_REBIND, ACTION_DISABLE, ACTION_REMOVE);
        if (projectId != null) {
            wrapper.eq(ElectricBoxQrLog::getProjectId, projectId);
        }
        return qrLogMapper.selectCount(wrapper) > 0;
    }

    private void requireManagePermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_MANAGE);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.BOX_MANAGE)) {
            throw BusinessException.forbidden("无电箱管理权限");
        }
    }

    private void requireQrManagePermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_MANAGE);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.BOX_QR_MANAGE)) {
            throw BusinessException.forbidden("无电箱二维码管理权限");
        }
    }

    private void requirePublicAccessPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_MANAGE);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.BOX_PUBLIC_ACCESS)) {
            throw BusinessException.forbidden("无外部公开扫码管理权限");
        }
    }

    private void requireBoxViewPermission(ElectricBox box, SysUser currentUser) {
        if (canViewBox(box, currentUser)) {
            return;
        }
        throw BusinessException.forbidden("无电箱访问权限");
    }

    private boolean canViewBox(ElectricBox box, SysUser currentUser) {
        if (!projectPermissionService.hasSystemPermission(currentUser.getId(), box.getProjectId(),
                SystemPermissionCodes.INSPECTION_VIEW)) {
            return false;
        }
        if (projectPermissionService.hasInspectionPermission(currentUser.getId(), box.getProjectId(), InspectionPermissionCodes.BOX_VIEW)) {
            return true;
        }
        return Objects.equals(box.getResponsibleElectricianId(), currentUser.getId());
    }

    private void ensureBoxAssignees(ElectricBox box) {
        projectMemberService.ensureProjectMember(
                box.getProjectId(),
                box.getResponsibleElectricianId(),
                ProjectPermissionService.ROLE_USER
        );
        projectMemberService.ensureProjectMember(
                box.getProjectId(),
                box.getSafetyManagerId(),
                ProjectPermissionService.ROLE_SAFETY_ADMIN
        );
    }

    private ElectricBoxVO toVO(ElectricBox box) {
        ElectricBoxVO vo = new ElectricBoxVO();
        BeanUtils.copyProperties(box, vo);
        InspectionRecord latestRecord = getLatestRecord(box.getId());
        InspectionRecord todayRecord = getTodayDailyRecord(box.getProjectId(), box.getId());
        int pendingRectificationCount = countOpenRectifications(box.getId());
        vo.setLastCheckDate(latestRecord == null ? null : latestRecord.getCheckDate());
        vo.setPendingRectificationCount(pendingRectificationCount);
        vo.setTodayStatus(resolveTodayStatus(box, todayRecord, pendingRectificationCount));
        var scope = inspectionScopeService.getCurrentForBox(box);
        vo.setInspectionRequired(scope.getEffectiveToday());
        vo.setScopeEffectiveDate(scope.getEffectiveDate());
        vo.setScopeEndDate(scope.getEndDate());
        return vo;
    }

    private ElectricBoxQrLogVO toLogVO(ElectricBoxQrLog log) {
        ElectricBoxQrLogVO vo = new ElectricBoxQrLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private InspectionRecord getLatestRecord(Long electricBoxId) {
        return inspectionRecordMapper.selectOne(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getElectricBoxId, electricBoxId)
                .orderByDesc(InspectionRecord::getCheckDate)
                .orderByDesc(InspectionRecord::getId)
                .last("LIMIT 1"));
    }

    private InspectionRecord getTodayDailyRecord(Long projectId, Long electricBoxId) {
        return inspectionRecordMapper.selectOne(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getElectricBoxId, electricBoxId)
                .eq(InspectionRecord::getSource, "ELECTRICIAN_DAILY")
                .eq(InspectionRecord::getCheckDate, LocalDate.now())
                .ne(InspectionRecord::getStatus, "REVIEW_REJECTED")
                .orderByDesc(InspectionRecord::getId)
                .last("LIMIT 1"));
    }

    private int countOpenRectifications(Long electricBoxId) {
        Long count = inspectionRectificationMapper.selectCount(new LambdaQueryWrapper<InspectionRectification>()
                .eq(InspectionRectification::getElectricBoxId, electricBoxId)
                .ne(InspectionRectification::getStatus, "CLOSED"));
        return count == null ? 0 : Math.toIntExact(count);
    }

    String resolveTodayStatus(ElectricBox box, InspectionRecord todayRecord, int pendingRectificationCount) {
        if (!STATUS_ACTIVE.equals(box.getStatus())) {
            return "UNCHECKED";
        }
        if (todayRecord == null) {
            return "UNCHECKED";
        }
        if (pendingRectificationCount > 0) {
            return "ABNORMAL";
        }
        return todayRecord.getAbnormalCount() != null && todayRecord.getAbnormalCount() > 0 ? "ABNORMAL" : "CHECKED";
    }

    private ElectricBoxQrLog recordQrLog(ElectricBox box, String actionType, String qrType, String oldQrCode,
                                         String newQrCode, SysUser currentUser, String reason) {
        ElectricBoxQrLog log = new ElectricBoxQrLog();
        log.setProjectId(box.getProjectId());
        log.setElectricBoxId(box.getId());
        log.setBoxCode(box.getBoxCode());
        log.setActionType(actionType);
        log.setQrType(qrType);
        log.setOldQrCode(oldQrCode);
        log.setNewQrCode(newQrCode);
        log.setOperatorUserId(currentUser == null ? null : currentUser.getId());
        log.setOperatorUsername(currentUser == null ? null : currentUser.getUsername());
        log.setReason(trimToNull(reason));
        log.setCreateTime(LocalDateTime.now());
        qrLogMapper.insert(log);
        return log;
    }

    private List<String> normalizeQrTypes(List<String> qrTypes) {
        if (qrTypes == null || qrTypes.isEmpty()) {
            return List.of(QR_TYPE_INTERNAL, QR_TYPE_PUBLIC);
        }
        List<String> result = new ArrayList<>();
        for (String item : qrTypes) {
            String normalized = trimToNull(item);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            normalized = normalized.toUpperCase(Locale.ROOT);
            if ("INTERNAL".equals(normalized) || "PUBLIC".equals(normalized) || "UNIFIED".equals(normalized)) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? List.of(QR_TYPE_INTERNAL, QR_TYPE_PUBLIC) : result;
    }

    private ElectricBoxImportResultVO validateImportRows(Long projectId, List<ImportRow> rows) {
        ElectricBoxImportResultVO result = new ElectricBoxImportResultVO();
        result.setTotalRows(rows.size());
        Set<String> importBoxCodes = new HashSet<>();
        Set<String> importQrCodes = new HashSet<>();
        for (ImportRow row : rows) {
            ElectricBoxImportRowVO vo = new ElectricBoxImportRowVO();
            vo.setRowNumber(row.rowNumber);
            vo.setBoxCode(row.boxCode);
            vo.setBoxName(row.boxName);
            vo.setInstallLocation(row.installLocation);
            vo.setQrCode(row.qrCode);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            if (!StringUtils.hasText(row.boxCode)) {
                errors.add("电箱编号不能为空");
            } else if (row.boxCode.length() > BOX_CODE_MAX_LENGTH) {
                errors.add("电箱编号不能超过64个字符");
            } else if (!importBoxCodes.add(row.boxCode)) {
                errors.add("导入文件内电箱编号重复");
            } else if (electricBoxMapper.selectCount(new LambdaQueryWrapper<ElectricBox>()
                    .eq(ElectricBox::getProjectId, projectId)
                    .eq(ElectricBox::getBoxCode, row.boxCode)) > 0) {
                errors.add("同一项目下电箱编号已存在");
            }
            if (!StringUtils.hasText(row.installLocation)) {
                errors.add("安装位置不能为空");
            }
            if (StringUtils.hasText(row.qrCode)) {
                if (row.qrCode.length() > 100) {
                    errors.add("二维码编码不能超过100个字符");
                } else if (!importQrCodes.add(row.qrCode)) {
                    errors.add("导入文件内二维码编码重复");
                } else if (electricBoxMapper.selectCount(new LambdaQueryWrapper<ElectricBox>()
                        .eq(ElectricBox::getProjectId, projectId)
                        .eq(ElectricBox::getQrCode, row.qrCode)) > 0) {
                    errors.add("同一项目下二维码编码已存在");
                } else if (isRetiredQrCode(projectId, row.qrCode)) {
                    errors.add("二维码编码已换绑或停用，不可再次绑定");
                }
            }
            resolveImportUsers(row, warnings);

            if (!errors.isEmpty()) {
                vo.setLevel("ERROR");
                vo.setMessage(String.join("；", errors));
                result.setErrorRows(result.getErrorRows() + 1);
            } else if (!warnings.isEmpty()) {
                vo.setLevel("WARN");
                vo.setMessage(String.join("；", warnings));
                result.setWarningRows(result.getWarningRows() + 1);
            } else {
                vo.setLevel("OK");
                vo.setMessage("校验通过");
            }
            result.getRows().add(vo);
        }
        if (result.getErrorRows() == 0) {
            result.setSuccessRows(rows.size());
        }
        return result;
    }

    private void resolveImportUsers(ImportRow row, List<String> warnings) {
        SysUser electrician = resolveUser(row.responsibleElectricianUsername, row.responsibleElectricianName);
        if (electrician != null) {
            row.responsibleElectricianId = electrician.getId();
            row.responsibleElectricianResolvedName = StringUtils.hasText(electrician.getRealName()) ? electrician.getRealName() : electrician.getUsername();
        } else if (StringUtils.hasText(row.responsibleElectricianUsername) || StringUtils.hasText(row.responsibleElectricianName)) {
            warnings.add("负责电工未匹配到系统账号，仅保留姓名文本");
        }
        SysUser safetyManager = resolveUser(row.safetyManagerUsername, row.safetyManagerName);
        if (safetyManager != null) {
            row.safetyManagerId = safetyManager.getId();
            row.safetyManagerResolvedName = StringUtils.hasText(safetyManager.getRealName()) ? safetyManager.getRealName() : safetyManager.getUsername();
        } else if (StringUtils.hasText(row.safetyManagerUsername) || StringUtils.hasText(row.safetyManagerName)) {
            warnings.add("安全负责人未匹配到系统账号，仅保留姓名文本");
        }
    }

    private SysUser resolveUser(String username, String realName) {
        if (StringUtils.hasText(username)) {
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username)
                    .last("LIMIT 1"));
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(realName)) {
            return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRealName, realName)
                    .last("LIMIT 1"));
        }
        return null;
    }

    private List<ImportRow> parseImportRows(MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new BusinessException("导入文件没有工作表");
            }
            List<ImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                ImportRow importRow = new ImportRow();
                importRow.rowNumber = i + 1;
                importRow.boxCode = cellText(row, 0);
                importRow.boxName = cellText(row, 1);
                importRow.installLocation = cellText(row, 2);
                importRow.responsibleElectricianUsername = cellText(row, 3);
                importRow.responsibleElectricianName = cellText(row, 4);
                importRow.safetyManagerUsername = cellText(row, 5);
                importRow.safetyManagerName = cellText(row, 6);
                importRow.qrCode = cellText(row, 7);
                importRow.publicAccessEnabled = parsePublicAccess(cellText(row, 8));
                importRow.remark = cellText(row, 9);
                rows.add(importRow);
            }
            if (rows.isEmpty()) {
                throw new BusinessException("导入文件没有可导入的数据行");
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessException("导入文件读取失败：" + e.getMessage());
        }
    }

    private boolean isBlankRow(Row row) {
        for (int i = 0; i < 10; i++) {
            if (StringUtils.hasText(cellText(row, i))) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        cell.setCellType(CellType.STRING);
        return trimToNull(cell.getStringCellValue());
    }

    private Integer parsePublicAccess(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("0".equals(normalized) || "否".equals(normalized) || "停用".equals(normalized)
                || "false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized)) {
            return 0;
        }
        return 1;
    }

    private String normalizeScannedCode(String rawCode) {
        String code = trimToNull(rawCode);
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("二维码编码不能为空");
        }
        String prefix = "site-platform://electric-box/";
        if (code.startsWith(prefix)) {
            return trimToNull(code.substring(prefix.length()));
        }
        return code;
    }

    private String generateInternalQrCode() {
        return "EBQR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String generatePublicCode() {
        return "PUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String resolveReason(ElectricBoxLifecycleRequest request, String fallback) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            return fallback;
        }
        return request.getReason();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static class ImportRow {
        private Integer rowNumber;
        private String boxCode;
        private String boxName;
        private String installLocation;
        private String responsibleElectricianUsername;
        private String responsibleElectricianName;
        private Long responsibleElectricianId;
        private String responsibleElectricianResolvedName;
        private String safetyManagerUsername;
        private String safetyManagerName;
        private Long safetyManagerId;
        private String safetyManagerResolvedName;
        private String qrCode;
        private Integer publicAccessEnabled = 1;
        private String remark;
    }
}
