package com.example.siteplatform.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.file.service.FileResourceService;
import com.example.siteplatform.inspection.dto.InspectionItemRequest;
import com.example.siteplatform.inspection.dto.InspectionRecordRequest;
import com.example.siteplatform.inspection.dto.InspectionReviewAssignRequest;
import com.example.siteplatform.inspection.dto.InspectionReviewRequest;
import com.example.siteplatform.inspection.dto.RectificationAssignRequest;
import com.example.siteplatform.inspection.dto.RectificationCompleteRequest;
import com.example.siteplatform.inspection.dto.RectificationEscalateRequest;
import com.example.siteplatform.inspection.dto.RectificationReviewRequest;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRecordItem;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import com.example.siteplatform.inspection.entity.InspectionRectificationReviewLog;
import com.example.siteplatform.inspection.entity.InspectionReviewLog;
import com.example.siteplatform.inspection.entity.InspectionTemplate;
import com.example.siteplatform.inspection.mapper.InspectionRecordItemMapper;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationReviewLogMapper;
import com.example.siteplatform.inspection.mapper.InspectionReviewLogMapper;
import com.example.siteplatform.inspection.mapper.InspectionTemplateMapper;
import com.example.siteplatform.inspection.vo.InspectionMonthSummaryVO;
import com.example.siteplatform.inspection.vo.InspectionRectificationReviewLogVO;
import com.example.siteplatform.inspection.vo.InspectionRectificationVO;
import com.example.siteplatform.inspection.vo.InspectionRecordItemVO;
import com.example.siteplatform.inspection.vo.InspectionRecordVO;
import com.example.siteplatform.inspection.vo.InspectionReviewLogVO;
import com.example.siteplatform.inspection.vo.InspectionTodoVO;
import com.example.siteplatform.inspection.vo.PublicElectricBoxSummaryVO;
import com.example.siteplatform.inspection.vo.PublicElectricBoxMonthlyVO;
import com.example.siteplatform.inspection.vo.PublicInspectionMonthRowVO;
import com.example.siteplatform.inspection.vo.PublicInspectionRecordVO;
import com.example.siteplatform.project.constant.InspectionPermissionCodes;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.entity.SysUserProject;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.mapper.SysUserProjectMapper;
import com.example.siteplatform.project.service.ProjectMemberService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.notification.service.WechatNotificationService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class InspectionService {

    public static final String TEMPLATE_ELECTRIC_BOX_DAILY = "ELECTRIC_BOX_DAILY";
    public static final String SOURCE_ELECTRICIAN_DAILY = "ELECTRICIAN_DAILY";
    public static final String SOURCE_SAFETY_SPOT_CHECK = "SAFETY_SPOT_CHECK";

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_REVIEW_PENDING = "REVIEW_PENDING";
    private static final String STATUS_REVIEW_PASSED = "REVIEW_PASSED";
    private static final String STATUS_REVIEW_REJECTED = "REVIEW_REJECTED";
    private static final String STATUS_RECTIFICATION_PENDING = "RECTIFICATION_PENDING";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String RECTIFICATION_PENDING = "PENDING";
    private static final String RECTIFICATION_COMPLETED = "COMPLETED";
    private static final String RECTIFICATION_CLOSED = "CLOSED";
    private static final String RECTIFICATION_REJECTED = "REJECTED";
    private static final String ESCALATION_NONE = "NONE";
    private static final String ESCALATION_REMINDED = "REMINDED";
    private static final String ESCALATION_ESCALATED = "ESCALATED";
    private static final int REVIEW_DEADLINE_HOURS = 24;
    private static final int RECTIFICATION_RECHECK_DAYS = 3;

    private static final List<String> REQUIRED_ITEM_CODES = List.of(
            "APPEARANCE",
            "LEAKAGE_PROTECTOR",
            "FUSE",
            "PROTECTIVE_ZERO",
            "SOCKET_220V",
            "SOCKET_380V"
    );

    private static final Set<String> ALLOWED_ITEM_RESULTS = Set.of("NORMAL", "ABNORMAL", "NA");
    private static final int MAX_PHOTO_COUNT_PER_GROUP = 20;
    private static final int REMARK_MAX_LENGTH = 1000;
    private static final int ITEM_DESCRIPTION_MAX_LENGTH = 500;

    private static final Map<String, String> EXPORT_ITEM_NAMES = new LinkedHashMap<>();

    static {
        EXPORT_ITEM_NAMES.put("APPEARANCE", "内外观");
        EXPORT_ITEM_NAMES.put("LEAKAGE_PROTECTOR", "漏电保护器");
        EXPORT_ITEM_NAMES.put("FUSE", "熔断");
        EXPORT_ITEM_NAMES.put("PROTECTIVE_ZERO", "保护接零");
        EXPORT_ITEM_NAMES.put("SOCKET_220V", "220V插座");
        EXPORT_ITEM_NAMES.put("SOCKET_380V", "380V插座");
    }

    @Autowired
    private ElectricBoxMapper electricBoxMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ProjectInfoMapper projectInfoMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private InspectionTemplateMapper inspectionTemplateMapper;

    @Autowired
    private InspectionRecordMapper inspectionRecordMapper;

    @Autowired
    private InspectionRecordItemMapper inspectionRecordItemMapper;

    @Autowired
    private InspectionRectificationMapper inspectionRectificationMapper;

    @Autowired
    private InspectionReviewLogMapper inspectionReviewLogMapper;

    @Autowired
    private InspectionRectificationReviewLogMapper inspectionRectificationReviewLogMapper;

    @Autowired
    private ElectricBoxInspectionScopeService inspectionScopeService;

    @Autowired
    private ProjectInspectionSettingService projectInspectionSettingService;

    @Autowired
    private WechatNotificationService wechatNotificationService;

    @Autowired
    private FileResourceService fileResourceService;

    public List<InspectionTemplate> listTemplates() {
        return inspectionTemplateMapper.selectList(new LambdaQueryWrapper<InspectionTemplate>()
                .eq(InspectionTemplate::getStatus, "ACTIVE")
                .orderByAsc(InspectionTemplate::getId));
    }

    public List<InspectionRecordVO> listRecords(Long projectId, Long electricBoxId, String status, String month,
                                                String reviewScope, Boolean reviewOverdue, SysUser currentUser) {
        return listRecords(projectId, electricBoxId, status, month, null, reviewScope, reviewOverdue, currentUser);
    }

    public List<InspectionRecordVO> listRecords(Long projectId, Long electricBoxId, String status, String month,
                                                String checkDate, String reviewScope, Boolean reviewOverdue,
                                                SysUser currentUser) {
        ensureSinglePeriodFilter(month, checkDate);
        LocalDate targetDate = StringUtils.hasText(checkDate) ? parseCheckDate(checkDate) : null;
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .ne(InspectionRecord::getStatus, "DRAFT");
        applyProjectScope(wrapper, projectId, currentUser);
        if (electricBoxId != null) {
            wrapper.eq(InspectionRecord::getElectricBoxId, electricBoxId);
        }
        if (StringUtils.hasText(status) && !STATUS_COMPLETED.equalsIgnoreCase(status.trim())) {
            wrapper.eq(InspectionRecord::getStatus, status);
        }
        if (targetDate != null) {
            wrapper.eq(InspectionRecord::getCheckDate, targetDate);
        } else if (StringUtils.hasText(month)) {
            YearMonth yearMonth = parseMonth(month);
            wrapper.between(InspectionRecord::getCheckDate, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        }
        if (projectId != null
                && !projectPermissionService.hasAnyInspectionPermission(currentUser.getId(), projectId,
                InspectionPermissionCodes.INSPECTION_RECORD_VIEW,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)) {
            wrapper.eq(InspectionRecord::getInspectorId, currentUser.getId());
        }
        wrapper.orderByDesc(InspectionRecord::getCheckDate).orderByDesc(InspectionRecord::getId);
        return inspectionRecordMapper.selectList(wrapper).stream()
                .filter(record -> canViewRecord(record, currentUser))
                .map(record -> toRecordVO(record, null, false))
                .toList();
    }

    @Transactional
    public InspectionRecordVO createRecord(InspectionRecordRequest request, SysUser currentUser) {
        validateRecordRequest(request);
        ElectricBox box = electricBoxMapper.selectByIdForUpdate(request.getElectricBoxId());
        if (box == null) {
            throw BusinessException.notFound("电箱不存在");
        }
        if (!Objects.equals(box.getProjectId(), request.getProjectId())) {
            throw new BusinessException("电箱不属于当前项目");
        }
        if (!"ACTIVE".equals(box.getStatus())) {
            throw BusinessException.forbidden("停用或已拆除电箱不可巡检");
        }
        String templateCode = TEMPLATE_ELECTRIC_BOX_DAILY;
        String source = SOURCE_ELECTRICIAN_DAILY;
        if (!SOURCE_ELECTRICIAN_DAILY.equals(source)) {
            throw new BusinessException("极简巡检模式只支持电箱日检");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), box.getProjectId());
        requireDailySubmitPermission(currentUser, box.getProjectId());
        LocalDate checkDate = request.getCheckDate() != null ? request.getCheckDate() : LocalDate.now();
        ensureCheckDateAllowed(checkDate);
        if (SOURCE_ELECTRICIAN_DAILY.equals(source)) {
            if (!inspectionScopeService.isRequired(box, checkDate)) {
                throw BusinessException.forbidden("该电箱在检查日期不属于日检范围");
            }
            ensureDailyUnique(box.getProjectId(), box.getId(), templateCode, source, checkDate);
        }

        InspectionRecord record = new InspectionRecord();
        record.setProjectId(box.getProjectId());
        record.setElectricBoxId(box.getId());
        record.setTemplateCode(templateCode);
        record.setSource(source);
        record.setProblemCategory(normalizeProblemCategory(request.getProblemCategory()));
        record.setCheckDate(checkDate);
        record.setInspectorId(currentUser.getId());
        record.setInspectorName(currentUser.getRealName());
        record.setOuterPhotoFileIds(joinIds(request.getOuterPhotoFileIds()));
        record.setInnerPhotoFileIds(joinIds(request.getInnerPhotoFileIds()));
        record.setAbnormalCount(countAbnormal(request.getItems()));
        record.setRemark(request.getRemark());
        record.setDeleted(0);
        markCompleted(record);
        requireSingleWrite(inspectionRecordMapper.insert(record), "巡检记录新增");
        fileResourceService.validateAndBind(currentUser, record.getProjectId(), request.getOuterPhotoFileIds(),
                "inspection_record", "inspection_record", record.getId());
        fileResourceService.validateAndBind(currentUser, record.getProjectId(), request.getInnerPhotoFileIds(),
                "inspection_record", "inspection_record", record.getId());

        List<InspectionRecordItem> items = new ArrayList<>();
        for (InspectionItemRequest itemRequest : request.getItems()) {
            InspectionRecordItem item = new InspectionRecordItem();
            item.setRecordId(record.getId());
            item.setItemCode(itemRequest.getItemCode());
            item.setItemName(EXPORT_ITEM_NAMES.get(itemRequest.getItemCode()));
            item.setResult(itemRequest.getResult());
            item.setDescription(itemRequest.getDescription());
            item.setDeleted(0);
            requireSingleWrite(inspectionRecordItemMapper.insert(item), "巡检检查项新增");
            items.add(item);
        }
        return toRecordVO(record, box, items, false);
    }

    @Transactional
    public InspectionRecordVO submitRecord(Long id, SysUser currentUser) {
        InspectionRecord record = requireRecord(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), record.getProjectId());
        requireDailySubmitPermission(currentUser, record.getProjectId());
        if (!Objects.equals(record.getInspectorId(), currentUser.getId())
                && !projectPermissionService.hasInspectionPermission(currentUser.getId(), record.getProjectId(), InspectionPermissionCodes.INSPECTION_RECORD_VIEW)) {
            throw BusinessException.forbidden("只能提交自己的检查记录");
        }
        markCompleted(record);
        requireSingleWrite(inspectionRecordMapper.updateById(record), "巡检记录提交");
        return toRecordVO(record, null, false);
    }

    public InspectionRecordVO reviewRecord(Long id, InspectionReviewRequest request, SysUser currentUser) {
        if (request == null) {
            request = new InspectionReviewRequest();
        }
        InspectionRecord record = requireRecord(id);
        refreshReviewOverdueState(record);
        requireReviewActionPermission(currentUser, record);
        String action = normalizeAction(request.getReviewAction());
        String comment = trimToNull(request.getComment());
        if (("REJECT".equals(action) || "RECTIFY".equals(action)) && !StringUtils.hasText(comment)) {
            throw new BusinessException("退回或转整改必须填写复核意见");
        }
        boolean wasOverdue = isReviewOverdueNow(record) || Integer.valueOf(1).equals(record.getReviewOverdue());
        record.setReviewerId(currentUser.getId());
        record.setReviewerName(currentUser.getRealName());
        record.setReviewTime(LocalDateTime.now());
        record.setReviewComment(comment);
        record.setReviewOverdue(wasOverdue ? 1 : 0);

        if ("PASS".equals(action)) {
            record.setStatus(STATUS_REVIEW_PASSED);
            record.setReviewStatus("PASSED");
        } else if ("REJECT".equals(action)) {
            record.setStatus(STATUS_REVIEW_REJECTED);
            record.setReviewStatus("REJECTED");
        } else if ("RECTIFY".equals(action)) {
            record.setStatus(STATUS_RECTIFICATION_PENDING);
            record.setReviewStatus("RECTIFICATION_REQUIRED");
            createRectification(record, request);
        } else {
            throw new BusinessException("复核动作只支持 PASS、REJECT、RECTIFY");
        }
        inspectionRecordMapper.updateById(record);
        writeReviewLog(record, action, currentUser, comment, null, null, null, null);
        if ("REJECT".equals(action)) {
            wechatNotificationService.notifyUser(record.getInspectorId(), "REVIEW_REJECTED", "INSPECTION_RECORD", record.getId(),
                    "日检已退回修改");
        }
        return toRecordVO(record, null, false);
    }

    public InspectionRecordVO assignReviewer(Long id, InspectionReviewAssignRequest request, SysUser currentUser) {
        if (request == null) {
            request = new InspectionReviewAssignRequest();
        }
        InspectionRecord record = requireRecord(id);
        requireReviewAssignPermission(currentUser, record.getProjectId());
        if (!STATUS_REVIEW_PENDING.equals(record.getStatus())) {
            throw new BusinessException("只有待复核记录可以改派复核人");
        }
        Long oldReviewerId = record.getAssignedReviewerId();
        String oldReviewerName = record.getAssignedReviewerName();
        Long reviewerId = request.getReviewerId();
        String reviewerName = null;
        if (reviewerId != null) {
            if (!projectPermissionService.hasInspectionPermission(reviewerId, record.getProjectId(), InspectionPermissionCodes.INSPECTION_REVIEW)) {
                throw BusinessException.forbidden("被分配人没有当前项目复核权限");
            }
            SysUser reviewer = userMapper.selectById(reviewerId);
            if (reviewer == null || (reviewer.getDeleted() != null && reviewer.getDeleted() == 1)) {
                throw BusinessException.notFound("被分配人不存在");
            }
            reviewerName = StringUtils.hasText(reviewer.getRealName()) ? reviewer.getRealName() : reviewer.getUsername();
        }
        record.setAssignedReviewerId(reviewerId);
        record.setAssignedReviewerName(reviewerName);
        inspectionRecordMapper.updateById(record);
        writeReviewLog(record, reviewerId == null ? "UNASSIGN" : "REASSIGN", currentUser,
                trimToNull(request.getComment()), oldReviewerId, oldReviewerName, reviewerId, reviewerName);
        return toRecordVO(record, null, false);
    }

    public List<InspectionReviewLogVO> listReviewLogs(Long id, SysUser currentUser) {
        InspectionRecord record = requireRecord(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), record.getProjectId());
        if (!canViewRecord(record, currentUser)) {
            throw BusinessException.forbidden("无复核日志访问权限");
        }
        return queryReviewLogs(id);
    }

    public InspectionRecordVO getRecord(Long id, SysUser currentUser) {
        InspectionRecord record = requireRecord(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), record.getProjectId());
        if (!canViewRecord(record, currentUser)) {
            throw BusinessException.forbidden("无检查记录访问权限");
        }
        return toRecordVO(record, null, false);
    }

    public List<InspectionTodoVO> listTodos(Long requestedProjectId, SysUser currentUser) {
        List<ProjectInfo> projects;
        if (requestedProjectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), requestedProjectId);
            ProjectInfo project = projectInfoMapper.selectById(requestedProjectId);
            projects = project == null ? List.of() : List.of(project);
        } else {
            projects = projectPermissionService.isPlatformAdmin(currentUser.getId())
                    ? projectInfoMapper.selectList(null)
                    : projectPermissionService.getUserProjects(currentUser.getId());
        }
        AtomicLong idGenerator = new AtomicLong(1);
        List<InspectionTodoVO> todos = new ArrayList<>();
        for (ProjectInfo project : projects) {
            Long projectId = project.getId();
            String projectName = StringUtils.hasText(project.getShortName()) ? project.getShortName() : project.getProjectName();
            boolean canSubmitDaily = projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId,
                    InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)
                    && projectPermissionService.hasSystemPermission(currentUser.getId(), projectId,
                    SystemPermissionCodes.INSPECTION_SUBMIT);
            if (canSubmitDaily) {
                appendInspectionTodos(todos, idGenerator, projectId, projectName);
            }
        }
        return todos;
    }

    private void appendInspectionTodos(List<InspectionTodoVO> todos, AtomicLong idGenerator, Long projectId,
                                       String projectName) {
        LambdaQueryWrapper<ElectricBox> wrapper = new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId)
                .eq(ElectricBox::getStatus, "ACTIVE")
                .orderByAsc(ElectricBox::getBoxCode);
        for (ElectricBox box : electricBoxMapper.selectList(wrapper)) {
            if (!inspectionScopeService.isRequired(box, LocalDate.now())) {
                continue;
            }
            if (hasTodayDailyRecord(projectId, box.getId())) {
                continue;
            }
            InspectionTodoVO todo = baseTodo(idGenerator, "INSPECTION", projectId, projectName, box.getBoxCode(),
                    box.getInstallLocation(), box.getId(), "warning");
            todo.setTitle(box.getBoxCode() + " 今日待巡检");
            todo.setDueText(LocalDate.now().toString());
            todos.add(todo);
        }
    }

    private void appendReviewTodos(List<InspectionTodoVO> todos, AtomicLong idGenerator, Long projectId,
                                   String projectName, SysUser currentUser) {
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getStatus, STATUS_REVIEW_PENDING)
                .orderByAsc(InspectionRecord::getReviewDueTime)
                .orderByDesc(InspectionRecord::getId);
        if (!canOverrideReviewAssignment(currentUser, projectId)) {
            wrapper.and(w -> w.isNull(InspectionRecord::getAssignedReviewerId)
                    .or()
                    .eq(InspectionRecord::getAssignedReviewerId, currentUser.getId()));
        }
        List<InspectionRecord> records = inspectionRecordMapper.selectList(wrapper);
        for (InspectionRecord record : records) {
            refreshReviewOverdueState(record);
            ElectricBox box = electricBoxMapper.selectById(record.getElectricBoxId());
            InspectionTodoVO todo = baseTodo(idGenerator, "REVIEW", projectId, projectName,
                    box == null ? "" : box.getBoxCode(), box == null ? "" : box.getInstallLocation(),
                    record.getId(), Integer.valueOf(1).equals(record.getReviewOverdue())
                            || (record.getAbnormalCount() != null && record.getAbnormalCount() > 0)
                            ? "danger"
                            : "normal");
            todo.setTitle((box == null ? "检查记录" : box.getBoxCode()) + " 待安全复核");
            todo.setDueText(formatReviewDueText(record));
            todo.setReviewDueTime(record.getReviewDueTime());
            todo.setAssignedReviewerId(record.getAssignedReviewerId());
            todo.setAssignedReviewerName(record.getAssignedReviewerName());
            todo.setReviewOverdue(record.getReviewOverdue());
            todos.add(todo);
        }
    }

    private void appendRectificationTodos(List<InspectionTodoVO> todos, AtomicLong idGenerator, Long projectId,
                                          String projectName, SysUser currentUser, boolean manager) {
        LambdaQueryWrapper<InspectionRectification> wrapper = new LambdaQueryWrapper<InspectionRectification>()
                .eq(InspectionRectification::getProjectId, projectId)
                .in(InspectionRectification::getStatus, List.of(RECTIFICATION_PENDING, RECTIFICATION_REJECTED))
                .orderByAsc(InspectionRectification::getDeadline)
                .orderByDesc(InspectionRectification::getId);
        if (!manager) {
            wrapper.eq(InspectionRectification::getAssigneeId, currentUser.getId());
        }
        for (InspectionRectification rectification : inspectionRectificationMapper.selectList(wrapper)) {
            ElectricBox box = electricBoxMapper.selectById(rectification.getElectricBoxId());
            InspectionTodoVO todo = baseTodo(idGenerator, "RECTIFICATION", projectId, projectName, rectification.getBoxCode(),
                    box == null ? "" : box.getInstallLocation(), rectification.getId(), "danger");
            todo.setTitle(rectification.getBoxCode() + " 异常整改");
            todo.setDueText(rectification.getDeadline() == null ? "尽快处理" : rectification.getDeadline() + " 前");
            todos.add(todo);
        }
    }

    private void appendRecheckTodos(List<InspectionTodoVO> todos, AtomicLong idGenerator, Long projectId,
                                    String projectName) {
        List<InspectionRectification> rectifications = inspectionRectificationMapper.selectList(
                new LambdaQueryWrapper<InspectionRectification>()
                        .eq(InspectionRectification::getProjectId, projectId)
                        .eq(InspectionRectification::getStatus, RECTIFICATION_COMPLETED)
                        .orderByAsc(InspectionRectification::getDeadline)
                        .orderByDesc(InspectionRectification::getId));
        for (InspectionRectification rectification : rectifications) {
            ElectricBox box = electricBoxMapper.selectById(rectification.getElectricBoxId());
            InspectionTodoVO todo = baseTodo(idGenerator, "RECHECK", projectId, projectName, rectification.getBoxCode(),
                    box == null ? "" : box.getInstallLocation(), rectification.getId(), "warning");
            todo.setTitle(rectification.getBoxCode() + " 整改完成待复查");
            todo.setDueText("今天");
            todos.add(todo);
        }
    }

    private InspectionTodoVO baseTodo(AtomicLong idGenerator, String type, Long projectId, String projectName, String boxCode,
                                     String installLocation, Long targetId, String priority) {
        InspectionTodoVO todo = new InspectionTodoVO();
        todo.setId(idGenerator.getAndIncrement());
        todo.setType(type);
        todo.setProjectId(projectId);
        todo.setProjectName(projectName);
        todo.setBoxCode(boxCode);
        todo.setInstallLocation(installLocation);
        todo.setTargetId(targetId);
        todo.setPriority(priority);
        return todo;
    }

    private boolean hasTodayDailyRecord(Long projectId, Long electricBoxId) {
        return inspectionRecordMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getElectricBoxId, electricBoxId)
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .eq(InspectionRecord::getCheckDate, LocalDate.now())) > 0;
    }

    public List<InspectionRectificationVO> listRectifications(Long projectId, String status, SysUser currentUser) {
        LambdaQueryWrapper<InspectionRectification> wrapper = new LambdaQueryWrapper<>();
        applyRectificationProjectScope(wrapper, projectId, currentUser);
        if (StringUtils.hasText(status)) {
            wrapper.eq(InspectionRectification::getStatus, status);
        }
        if (projectId != null && !projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.RECTIFICATION_VIEW)) {
            wrapper.eq(InspectionRectification::getAssigneeId, currentUser.getId());
        }
        wrapper.orderByAsc(InspectionRectification::getDeadline).orderByDesc(InspectionRectification::getId);
        return inspectionRectificationMapper.selectList(wrapper).stream()
                .filter(rectification -> canViewRectification(rectification, currentUser))
                .map(this::toRectificationVO)
                .toList();
    }

    public InspectionRectificationVO getRectification(Long id, SysUser currentUser) {
        InspectionRectification rectification = requireRectification(id);
        projectPermissionService.checkProjectPermission(currentUser.getId(), rectification.getProjectId());
        projectPermissionService.requireSystemPermission(currentUser.getId(), rectification.getProjectId(),
                SystemPermissionCodes.INSPECTION_VIEW);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), rectification.getProjectId(), InspectionPermissionCodes.RECTIFICATION_VIEW)
                && !Objects.equals(rectification.getAssigneeId(), currentUser.getId())) {
            throw BusinessException.forbidden("无整改任务访问权限");
        }
        return toRectificationVO(rectification);
    }

    public InspectionRectificationVO completeRectification(Long id, RectificationCompleteRequest request,
                                                           SysUser currentUser) {
        InspectionRectification rectification = requireRectification(id);
        projectPermissionService.requireSystemPermission(currentUser.getId(), rectification.getProjectId(),
                SystemPermissionCodes.INSPECTION_MANAGE);
        boolean manager = projectPermissionService.hasInspectionPermission(currentUser.getId(), rectification.getProjectId(), InspectionPermissionCodes.RECTIFICATION_REVIEW);
        if (!manager && !Objects.equals(rectification.getAssigneeId(), currentUser.getId())) {
            throw BusinessException.forbidden("只能处理分配给自己的整改任务");
        }
        if (!RECTIFICATION_PENDING.equals(rectification.getStatus())
                && !RECTIFICATION_REJECTED.equals(rectification.getStatus())) {
            throw new BusinessException("只有待整改或复查退回状态可以提交整改");
        }
        String feedback = request == null ? null : trimToNull(request.getFeedback());
        if (!StringUtils.hasText(feedback)) {
            throw new BusinessException("整改说明不能为空");
        }
        List<Long> photoFileIds = request == null ? Collections.emptyList() : request.getPhotoFileIds();
        if (photoFileIds == null || photoFileIds.stream().noneMatch(Objects::nonNull)) {
            throw new BusinessException("整改照片不能为空");
        }
        String fromStatus = rectification.getStatus();
        String photoIds = joinIds(photoFileIds);
        rectification.setStatus(RECTIFICATION_COMPLETED);
        rectification.setFeedback(feedback);
        rectification.setRectificationPhotoFileIds(photoIds);
        rectification.setCompletedTime(LocalDateTime.now());
        rectification.setCloseTime(null);
        inspectionRectificationMapper.updateById(rectification);
        writeRectificationLog(rectification, "COMPLETE", fromStatus, RECTIFICATION_COMPLETED, currentUser, feedback, photoIds);
        ElectricBox box = electricBoxMapper.selectById(rectification.getElectricBoxId());
        if (box != null && box.getSafetyManagerId() != null) {
            wechatNotificationService.notifyUser(box.getSafetyManagerId(), "RECHECK_PENDING", "RECTIFICATION", rectification.getId(),
                    box.getBoxCode() + " 整改待复查");
        }
        return toRectificationVO(rectification);
    }

    public InspectionRectificationVO assignRectification(Long id, RectificationAssignRequest request, SysUser currentUser) {
        if (request == null) {
            request = new RectificationAssignRequest();
        }
        InspectionRectification rectification = requireRectification(id);
        requireRectificationReviewPermission(currentUser, rectification.getProjectId());
        if (RECTIFICATION_CLOSED.equals(rectification.getStatus())) {
            throw new BusinessException("已关闭整改不可改派");
        }
        if (request.getAssigneeId() == null && request.getDeadline() == null) {
            throw new BusinessException("请选择整改人或整改期限");
        }
        String fromStatus = rectification.getStatus();
        String oldAssigneeName = rectification.getAssigneeName();
        if (request.getAssigneeId() != null) {
            Assignee assignee = resolveAssignee(request.getAssigneeId(), rectification.getProjectId());
            rectification.setAssigneeId(assignee.id());
            rectification.setAssigneeName(assignee.name());
            projectMemberService.ensureProjectMember(rectification.getProjectId(), assignee.id(), ProjectPermissionService.ROLE_USER);
        }
        if (request.getDeadline() != null) {
            rectification.setDeadline(validateRectificationDeadline(request.getDeadline()));
        }
        rectification.setEscalationStatus(ESCALATION_NONE);
        rectification.setEscalationTime(null);
        rectification.setEscalationNote(null);
        inspectionRectificationMapper.updateById(rectification);
        String comment = trimToNull(request.getComment());
        if (!StringUtils.hasText(comment)) {
            comment = "改派整改任务：" + (oldAssigneeName == null ? "未指定" : oldAssigneeName)
                    + " -> " + (rectification.getAssigneeName() == null ? "未指定" : rectification.getAssigneeName());
        }
        writeRectificationLog(rectification, "ASSIGN", fromStatus, rectification.getStatus(), currentUser, comment, null);
        return toRectificationVO(rectification);
    }

    public InspectionRectificationVO escalateRectification(Long id, RectificationEscalateRequest request, SysUser currentUser) {
        InspectionRectification rectification = requireRectification(id);
        requireRectificationReviewPermission(currentUser, rectification.getProjectId());
        if (RECTIFICATION_CLOSED.equals(rectification.getStatus())) {
            throw new BusinessException("已关闭整改不可升级提醒");
        }
        if (RECTIFICATION_COMPLETED.equals(rectification.getStatus())) {
            throw new BusinessException("已提交待复查整改不需要升级提醒");
        }
        boolean overdue = isRectificationOverdue(rectification);
        String note = request == null ? null : trimToNull(request.getNote());
        rectification.setEscalationStatus(overdue ? ESCALATION_ESCALATED : ESCALATION_REMINDED);
        rectification.setEscalationTime(LocalDateTime.now());
        rectification.setEscalationNote(StringUtils.hasText(note)
                ? note
                : overdue ? "整改已逾期，已升级提醒项目负责人和整改人" : "整改未逾期，已发送跟进提醒");
        inspectionRectificationMapper.updateById(rectification);
        writeRectificationLog(rectification, overdue ? "ESCALATE" : "REMIND",
                rectification.getStatus(), rectification.getStatus(), currentUser, rectification.getEscalationNote(), null);
        return toRectificationVO(rectification);
    }

    public InspectionRectificationVO closeRectification(Long id, RectificationReviewRequest request, SysUser currentUser) {
        InspectionRectification rectification = requireRectification(id);
        requireRectificationReviewPermission(currentUser, rectification.getProjectId());
        if (!RECTIFICATION_COMPLETED.equals(rectification.getStatus())) {
            throw new BusinessException("只有待复查整改可以关闭");
        }
        String fromStatus = rectification.getStatus();
        String comment = request == null ? null : trimToNull(request.getComment());
        LocalDateTime now = LocalDateTime.now();
        rectification.setStatus(RECTIFICATION_CLOSED);
        rectification.setReviewerId(currentUser.getId());
        rectification.setReviewerName(displayUserName(currentUser));
        rectification.setReviewTime(now);
        rectification.setReviewComment(comment);
        rectification.setCloseTime(now);
        inspectionRectificationMapper.updateById(rectification);
        writeRectificationLog(rectification, "CLOSE", fromStatus, RECTIFICATION_CLOSED, currentUser, comment, null);
        closeRecordIfAllRectificationsClosed(rectification.getInspectionRecordId());
        return toRectificationVO(rectification);
    }

    public InspectionRectificationVO rejectRectification(Long id, RectificationReviewRequest request, SysUser currentUser) {
        InspectionRectification rectification = requireRectification(id);
        requireRectificationReviewPermission(currentUser, rectification.getProjectId());
        if (!RECTIFICATION_COMPLETED.equals(rectification.getStatus())) {
            throw new BusinessException("只有待复查整改可以退回");
        }
        String comment = request == null ? null : trimToNull(request.getComment());
        if (!StringUtils.hasText(comment)) {
            throw new BusinessException("复查退回原因不能为空");
        }
        String fromStatus = rectification.getStatus();
        rectification.setStatus(RECTIFICATION_REJECTED);
        rectification.setReviewerId(currentUser.getId());
        rectification.setReviewerName(displayUserName(currentUser));
        rectification.setReviewTime(LocalDateTime.now());
        rectification.setReviewComment(comment);
        rectification.setRejectCount((rectification.getRejectCount() == null ? 0 : rectification.getRejectCount()) + 1);
        LocalDate recheckBase = rectification.getRecheckDeadline() != null
                ? rectification.getRecheckDeadline()
                : rectification.getDeadline() != null ? rectification.getDeadline() : LocalDate.now();
        rectification.setRecheckDeadline(recheckBase.plusDays(RECTIFICATION_RECHECK_DAYS));
        rectification.setCloseTime(null);
        inspectionRectificationMapper.updateById(rectification);
        writeRectificationLog(rectification, "REJECT", fromStatus, RECTIFICATION_REJECTED, currentUser, comment, null);
        wechatNotificationService.notifyUser(rectification.getAssigneeId(), "RECHECK_REJECTED", "RECTIFICATION", rectification.getId(),
                "整改复查已退回");
        return toRectificationVO(rectification);
    }

    public InspectionMonthSummaryVO getMonthSummary(Long projectId, Long boxId, String month, SysUser currentUser) {
        return getMonthSummary(projectId, boxId, month, null, currentUser);
    }

    public InspectionMonthSummaryVO getMonthSummary(Long projectId, Long boxId, String month, String checkDate,
                                                     SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        ensureSinglePeriodFilter(month, checkDate);
        LocalDate targetDate = StringUtils.hasText(checkDate) ? parseCheckDate(checkDate) : null;
        YearMonth yearMonth = targetDate == null ? parseMonth(month) : YearMonth.from(targetDate);
        String normalizedMonth = yearMonth.toString();
        requireSummaryPermission(currentUser, projectId);
        List<ElectricBox> boxes = queryBoxes(projectId, boxId);
        List<InspectionRecordVO> visibleRecords = listRecords(
                projectId,
                boxId,
                null,
                targetDate == null ? normalizedMonth : null,
                targetDate == null ? null : targetDate.toString(),
                null,
                null,
                currentUser);
        List<InspectionRecord> aggregateRecords = querySummaryRecords(
                projectId, boxId, yearMonth, targetDate);
        LocalDate cutoffDate = LocalDate.now();
        Map<Long, ElectricBox> boxById = boxes.stream()
                .collect(Collectors.toMap(ElectricBox::getId, box -> box, (left, right) -> left));
        int shouldCheck;
        long checked;
        int abnormal;
        if (targetDate != null) {
            Set<Long> requiredBoxIds = boxes.stream()
                    .filter(box -> inspectionScopeService.isRequired(box, targetDate))
                    .map(ElectricBox::getId)
                    .collect(Collectors.toSet());
            shouldCheck = requiredBoxIds.size();
            checked = aggregateRecords.stream()
                    .filter(record -> targetDate.equals(record.getCheckDate()))
                    .filter(record -> requiredBoxIds.contains(record.getElectricBoxId()))
                    .map(record -> record.getElectricBoxId() + ":" + record.getCheckDate())
                    .distinct()
                    .count();
            abnormal = (int) aggregateRecords.stream()
                    .filter(record -> targetDate.equals(record.getCheckDate()))
                    .filter(record -> requiredBoxIds.contains(record.getElectricBoxId()))
                    .filter(record -> record.getAbnormalCount() != null && record.getAbnormalCount() > 0)
                    .map(record -> record.getElectricBoxId() + ":" + record.getCheckDate())
                    .distinct()
                    .count();
        } else {
            shouldCheck = boxes.stream()
                    .mapToInt(box -> inspectionScopeService.countRequiredDaysThrough(box, yearMonth, cutoffDate))
                    .sum();
            checked = aggregateRecords.stream()
                    .filter(record -> record.getCheckDate() != null && !record.getCheckDate().isAfter(cutoffDate))
                    .filter(record -> yearMonth.equals(YearMonth.from(record.getCheckDate())))
                    .filter(record -> {
                        ElectricBox recordBox = boxById.get(record.getElectricBoxId());
                        return recordBox != null && inspectionScopeService.isRequired(recordBox, record.getCheckDate());
                    })
                    .map(record -> record.getElectricBoxId() + ":" + record.getCheckDate())
                    .distinct()
                    .count();
            abnormal = (int) aggregateRecords.stream()
                    .filter(record -> record.getCheckDate() != null && !record.getCheckDate().isAfter(cutoffDate))
                    .filter(record -> yearMonth.equals(YearMonth.from(record.getCheckDate())))
                    .filter(record -> {
                        ElectricBox recordBox = boxById.get(record.getElectricBoxId());
                        return recordBox != null && inspectionScopeService.isRequired(recordBox, record.getCheckDate());
                    })
                    .filter(record -> record.getAbnormalCount() != null && record.getAbnormalCount() > 0)
                    .map(record -> record.getElectricBoxId() + ":" + record.getCheckDate())
                    .distinct()
                    .count();
        }

        InspectionMonthSummaryVO summary = new InspectionMonthSummaryVO();
        summary.setProjectId(projectId);
        summary.setElectricBoxId(boxId);
        summary.setMonth(normalizedMonth);
        summary.setPeriodType(targetDate == null ? "MONTH" : "DAY");
        summary.setPeriodValue(targetDate == null ? normalizedMonth : targetDate.toString());
        summary.setShouldCheck(shouldCheck);
        summary.setChecked((int) checked);
        summary.setMissed(Math.max(shouldCheck - (int) checked, 0));
        summary.setAbnormal(abnormal);
        summary.setOpenRectification(0);
        summary.setRecords(visibleRecords);
        return summary;
    }

    public PublicElectricBoxSummaryVO getPublicSummary(String publicCode) {
        if (!StringUtils.hasText(publicCode)) {
            throw new BusinessException("公开码不能为空");
        }
        ElectricBox box = electricBoxMapper.selectOne(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getPublicCode, publicCode)
                .last("LIMIT 1"));
        if (box == null) {
            throw BusinessException.notFound("未找到电箱公开信息");
        }
        if (!Integer.valueOf(1).equals(box.getPublicAccessEnabled())) {
            throw BusinessException.forbidden("该电箱公开扫码访问已停用");
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);
        List<InspectionRecord> records = inspectionRecordMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getElectricBoxId, box.getId())
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .ge(InspectionRecord::getCheckDate, startDate)
                .le(InspectionRecord::getCheckDate, endDate)
                .orderByDesc(InspectionRecord::getCheckDate)
                .orderByDesc(InspectionRecord::getId));
        Set<LocalDate> requiredDays = inspectionScopeService.requiredDates(box, startDate, endDate);
        Map<LocalDate, InspectionRecord> latestRecordByDate = new LinkedHashMap<>();
        records.stream()
                .filter(record -> record.getCheckDate() != null)
                .forEach(record -> latestRecordByDate.putIfAbsent(record.getCheckDate(), record));
        long checkedDays = requiredDays.stream().filter(latestRecordByDate::containsKey).count();
        long abnormalDays = requiredDays.stream()
                .map(latestRecordByDate::get)
                .filter(Objects::nonNull)
                .filter(record -> record.getAbnormalCount() != null && record.getAbnormalCount() > 0)
                .count();
        ProjectInfo project = projectInfoMapper.selectById(box.getProjectId());

        PublicElectricBoxSummaryVO summary = new PublicElectricBoxSummaryVO();
        summary.setProjectShortName(project != null && StringUtils.hasText(project.getShortName())
                ? project.getShortName()
                : "项目现场");
        summary.setBoxCode(box.getBoxCode());
        summary.setBoxName(box.getBoxName());
        summary.setInstallLocation(box.getInstallLocation());
        summary.setStatus(box.getStatus());
        summary.setRangeStartDate(startDate);
        summary.setRangeEndDate(endDate);
        summary.setLatestCheckDate(records.isEmpty() ? null : records.get(0).getCheckDate());
        summary.setShouldCheckDays(requiredDays.size());
        summary.setCheckedDays((int) checkedDays);
        summary.setMissedDays(Math.max(requiredDays.size() - (int) checkedDays, 0));
        summary.setAbnormalCount((int) abnormalDays);
        summary.setOpenRectificationCount(0);
        summary.setRecentRecords(records.stream()
                .limit(8)
                .map(this::toPublicRecordVO)
                .toList());
        return summary;
    }

    public PublicElectricBoxMonthlyVO getPublicMonthly(String publicCode, String month) {
        ElectricBox box = requirePublicBox(publicCode);
        YearMonth targetMonth = parseMonth(month);
        YearMonth currentMonth = YearMonth.now();
        if (targetMonth.isAfter(currentMonth) || targetMonth.isBefore(currentMonth.minusMonths(11))) {
            throw new BusinessException("公开月表只允许查看最近12个月");
        }

        List<InspectionRecord> records = inspectionRecordMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getElectricBoxId, box.getId())
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .between(InspectionRecord::getCheckDate, targetMonth.atDay(1), targetMonth.atEndOfMonth())
                .orderByAsc(InspectionRecord::getCheckDate)
                .orderByDesc(InspectionRecord::getId));
        Map<LocalDate, InspectionRecord> recordByDate = new LinkedHashMap<>();
        for (InspectionRecord record : records) {
            recordByDate.putIfAbsent(record.getCheckDate(), record);
        }
        Map<Long, List<InspectionRecordItem>> itemByRecord = records.isEmpty()
                ? Collections.emptyMap()
                : inspectionRecordItemMapper.selectList(new LambdaQueryWrapper<InspectionRecordItem>()
                        .in(InspectionRecordItem::getRecordId, records.stream().map(InspectionRecord::getId).toList()))
                .stream().collect(Collectors.groupingBy(InspectionRecordItem::getRecordId));
        Map<Long, List<InspectionRectification>> rectificationByRecord = records.isEmpty()
                ? Collections.emptyMap()
                : inspectionRectificationMapper.selectList(new LambdaQueryWrapper<InspectionRectification>()
                        .in(InspectionRectification::getInspectionRecordId, records.stream().map(InspectionRecord::getId).toList()))
                .stream().collect(Collectors.groupingBy(InspectionRectification::getInspectionRecordId));
        Set<LocalDate> requiredDates = inspectionScopeService.requiredDates(
                box, targetMonth.atDay(1), targetMonth.atEndOfMonth());

        List<PublicInspectionMonthRowVO> rows = new ArrayList<>();
        int shouldCheck = 0;
        int checked = 0;
        int abnormal = 0;
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
            LocalDate date = targetMonth.atDay(day);
            boolean required = requiredDates.contains(date);
            InspectionRecord record = recordByDate.get(date);
            PublicInspectionMonthRowVO row = buildPublicMonthRow(date, required, record,
                    record == null ? List.of() : itemByRecord.getOrDefault(record.getId(), List.of()),
                    record == null ? List.of() : rectificationByRecord.getOrDefault(record.getId(), List.of()));
            rows.add(row);
            boolean dueDate = !date.isAfter(today);
            if (required && dueDate) shouldCheck++;
            if (required && dueDate && record != null) checked++;
            if (required && dueDate && record != null
                    && record.getAbnormalCount() != null && record.getAbnormalCount() > 0) {
                abnormal++;
            }
        }

        ProjectInfo project = projectInfoMapper.selectById(box.getProjectId());
        PublicElectricBoxMonthlyVO result = new PublicElectricBoxMonthlyVO();
        result.setProjectName(project == null ? "项目现场" : project.getProjectName());
        result.setProjectShortName(project != null && StringUtils.hasText(project.getShortName())
                ? project.getShortName() : result.getProjectName());
        result.setBoxCode(box.getBoxCode());
        result.setBoxName(box.getBoxName());
        result.setInstallLocation(box.getInstallLocation());
        result.setStatus(box.getStatus());
        result.setMonth(targetMonth.toString());
        result.setShouldCheckDays(shouldCheck);
        result.setCheckedDays(checked);
        result.setMissedDays(Math.max(shouldCheck - checked, 0));
        result.setAbnormalDays(abnormal);
        result.setOpenRectificationCount(0);
        result.setRows(rows);
        return result;
    }

    public ExportFile exportRecords(Long projectId, String templateCode, String month, Long boxId,
                                    Long inspectorId, String result, SysUser currentUser) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        ElectricBox singleBox = null;
        if (boxId == null) {
            requireSummaryExportPermission(currentUser, projectId);
        } else {
            singleBox = requireBox(boxId);
            if (!Objects.equals(singleBox.getProjectId(), projectId)) {
                throw BusinessException.forbidden("电箱不属于当前项目");
            }
            requireSingleBoxExportPermission(currentUser, projectId);
        }
        YearMonth yearMonth = parseMonth(month);
        List<ElectricBox> boxes = queryBoxes(projectId, boxId);
        String normalizedResult = boxId == null && StringUtils.hasText(result) ? result.trim().toUpperCase() : null;
        if (normalizedResult != null && !List.of("NORMAL", "ABNORMAL").contains(normalizedResult)) {
            throw new BusinessException("巡检结果筛选值无效");
        }
        Long exportInspectorId = boxId == null ? inspectorId : null;
        boolean filteredExport = exportInspectorId != null || normalizedResult != null;
        LambdaQueryWrapper<InspectionRecord> recordQuery = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getTemplateCode,
                        StringUtils.hasText(templateCode) ? templateCode : TEMPLATE_ELECTRIC_BOX_DAILY)
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .ne(InspectionRecord::getStatus, "DRAFT")
                .between(InspectionRecord::getCheckDate, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        if (boxId != null) {
            recordQuery.eq(InspectionRecord::getElectricBoxId, boxId);
        }
        if (exportInspectorId != null) {
            recordQuery.eq(InspectionRecord::getInspectorId, exportInspectorId);
        }
        if ("ABNORMAL".equals(normalizedResult)) {
            recordQuery.gt(InspectionRecord::getAbnormalCount, 0);
        } else if ("NORMAL".equals(normalizedResult)) {
            recordQuery.and(wrapper -> wrapper.isNull(InspectionRecord::getAbnormalCount)
                    .or().eq(InspectionRecord::getAbnormalCount, 0));
        }
        List<InspectionRecord> records = inspectionRecordMapper.selectList(recordQuery
                .orderByAsc(InspectionRecord::getElectricBoxId)
                .orderByAsc(InspectionRecord::getCheckDate));
        if (filteredExport) {
            Set<Long> matchedBoxIds = records.stream().map(InspectionRecord::getElectricBoxId).collect(Collectors.toSet());
            boxes = boxes.stream().filter(box -> matchedBoxIds.contains(box.getId())).toList();
        }
        List<InspectionRectification> rectifications = List.of();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (singleBox != null) {
                ProjectInfo project = projectInfoMapper.selectById(projectId);
                writeSingleBoxMonthlySheet(workbook, singleBox, project, records, yearMonth);
                workbook.write(outputStream);
                String fileName = singleBox.getBoxCode() + "-电箱检查记录表-" + yearMonth + ".xlsx";
                return new ExportFile(fileName, outputStream.toByteArray());
            }
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            writeSummarySheet(workbook, headerStyle, boxes, records, rectifications, yearMonth,
                    filteredExport, exportInspectorId, normalizedResult);
            for (ElectricBox box : boxes) {
                writeBoxSheet(workbook, headerStyle, box, records, rectifications, yearMonth, filteredExport);
            }
            workbook.write(outputStream);
            String fileName = "电箱巡检记录-" + projectId + "-" + yearMonth + ".xlsx";
            return new ExportFile(fileName, outputStream.toByteArray());
        } catch (IOException e) {
            throw new BusinessException("导出巡检记录失败");
        }
    }

    private void writeSingleBoxMonthlySheet(Workbook workbook, ElectricBox box, ProjectInfo project,
                                            List<InspectionRecord> records, YearMonth yearMonth) {
        Sheet sheet = workbook.createSheet(safeSheetName(box.getBoxCode()));
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
        sheet.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 20);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle metaStyle = workbook.createCellStyle();
        metaStyle.setFont(boldFont);
        metaStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle headerStyle = createMonthlyTableStyle(workbook, true);
        CellStyle bodyStyle = createMonthlyTableStyle(workbook, false);
        CellStyle remarkStyle = createMonthlyTableStyle(workbook, false);
        remarkStyle.setAlignment(HorizontalAlignment.LEFT);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(34);
        titleRow.createCell(0).setCellValue("电箱检查记录表");
        titleRow.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

        String projectName = project == null || !StringUtils.hasText(project.getProjectName())
                ? "项目现场"
                : project.getProjectName();
        Row projectRow = sheet.createRow(1);
        projectRow.setHeightInPoints(24);
        projectRow.createCell(0).setCellValue("工程名称：" + projectName);
        projectRow.getCell(0).setCellStyle(metaStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 8));

        Row boxRow = sheet.createRow(2);
        boxRow.setHeightInPoints(24);
        boxRow.createCell(0).setCellValue("电箱编号：" + box.getBoxCode());
        boxRow.getCell(0).setCellStyle(metaStyle);
        boxRow.createCell(3).setCellValue("电箱名称：" + (StringUtils.hasText(box.getBoxName()) ? box.getBoxName() : "—"));
        boxRow.getCell(3).setCellStyle(metaStyle);
        boxRow.createCell(6).setCellValue("检查月份：" + yearMonth);
        boxRow.getCell(6).setCellStyle(metaStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 3, 5));
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 6, 8));

        Row locationRow = sheet.createRow(3);
        locationRow.setHeightInPoints(22);
        locationRow.createCell(0).setCellValue("安装位置：" + (StringUtils.hasText(box.getInstallLocation()) ? box.getInstallLocation() : "—"));
        locationRow.getCell(0).setCellStyle(metaStyle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 8));

        String[] headers = {"日期", "内外观", "漏电保护器", "熔断", "保护接零", "220V插座", "380V插座", "检查人", "备注"};
        Row headerRow = sheet.createRow(4);
        headerRow.setHeightInPoints(26);
        for (int column = 0; column < headers.length; column++) {
            headerRow.createCell(column).setCellValue(headers[column]);
            headerRow.getCell(column).setCellStyle(headerStyle);
        }

        Map<LocalDate, InspectionRecord> recordByDate = records.stream()
                .filter(record -> Objects.equals(record.getElectricBoxId(), box.getId()))
                .collect(Collectors.toMap(InspectionRecord::getCheckDate, record -> record, (left, right) -> right));
        Map<Long, Map<String, InspectionRecordItem>> itemsByRecord = loadExportItems(records);
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            InspectionRecord record = recordByDate.get(date);
            boolean required = inspectionScopeService.isRequired(box, date);
            Row row = sheet.createRow(day + 4);
            row.setHeightInPoints(24);
            row.createCell(0).setCellValue(date.getYear() + "." + date.getMonthValue() + "." + date.getDayOfMonth());
            row.getCell(0).setCellStyle(bodyStyle);
            if (record == null) {
                String value = !required ? "非巡检范围" : date.isAfter(today) ? "—" : "未检";
                for (int column = 1; column <= 6; column++) {
                    row.createCell(column).setCellValue(value);
                    row.getCell(column).setCellStyle(bodyStyle);
                }
                row.createCell(7).setCellValue("");
                row.getCell(7).setCellStyle(bodyStyle);
                row.createCell(8).setCellValue(!required ? "非巡检范围" : date.isAfter(today) ? "" : "未检");
                row.getCell(8).setCellStyle(remarkStyle);
                continue;
            }
            Map<String, InspectionRecordItem> itemMap = itemsByRecord.getOrDefault(record.getId(), Map.of());
            int column = 1;
            for (String itemCode : EXPORT_ITEM_NAMES.keySet()) {
                row.createCell(column).setCellValue(displayResult(itemMap.get(itemCode)));
                row.getCell(column).setCellStyle(bodyStyle);
                column++;
            }
            row.createCell(7).setCellValue(record.getInspectorName());
            row.getCell(7).setCellStyle(bodyStyle);
            row.createCell(8).setCellValue(buildExportRemark(record, itemMap, List.of()));
            row.getCell(8).setCellStyle(remarkStyle);
        }

        int[] widths = {14, 13, 16, 11, 14, 13, 13, 13, 34};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.setAutobreaks(true);
        sheet.setMargin(Sheet.LeftMargin, 0.25);
        sheet.setMargin(Sheet.RightMargin, 0.25);
        sheet.setMargin(Sheet.TopMargin, 0.4);
        sheet.setMargin(Sheet.BottomMargin, 0.4);
    }

    private CellStyle createMonthlyTableStyle(Workbook workbook, boolean header) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_80_PERCENT.getIndex());
        if (header) {
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        return style;
    }

    private Map<Long, Map<String, InspectionRecordItem>> loadExportItems(List<InspectionRecord> records) {
        List<Long> recordIds = records.stream().map(InspectionRecord::getId).filter(Objects::nonNull).toList();
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        return inspectionRecordItemMapper.selectList(new LambdaQueryWrapper<InspectionRecordItem>()
                        .in(InspectionRecordItem::getRecordId, recordIds))
                .stream()
                .collect(Collectors.groupingBy(InspectionRecordItem::getRecordId,
                        Collectors.toMap(InspectionRecordItem::getItemCode, item -> item, (left, right) -> right)));
    }

    private void writeSummarySheet(Workbook workbook, CellStyle headerStyle, List<ElectricBox> boxes,
                                   List<InspectionRecord> records, List<InspectionRectification> rectifications,
                                   YearMonth yearMonth, boolean filteredExport, Long inspectorId, String result) {
        Sheet sheet = workbook.createSheet("巡检汇总");
        String[] headers = filteredExport
                ? new String[]{"电箱编号", "安装位置", "筛选记录数", "异常记录数", "巡检员筛选", "结果筛选"}
                : new String[]{"电箱编号", "安装位置", "应检天数", "已检天数", "漏检天数", "异常次数"};
        writeHeader(sheet, headerStyle, headers);
        int rowIndex = 1;
        for (ElectricBox box : boxes) {
            List<InspectionRecord> boxRecords = records.stream()
                    .filter(record -> Objects.equals(record.getElectricBoxId(), box.getId()))
                    .toList();
            int checkedDays = (int) boxRecords.stream().map(InspectionRecord::getCheckDate).distinct().count();
            int abnormal = (int) boxRecords.stream().filter(record -> record.getAbnormalCount() != null
                    && record.getAbnormalCount() > 0).count();
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(box.getBoxCode());
            row.createCell(1).setCellValue(box.getInstallLocation());
            if (filteredExport) {
                String inspectorName = boxRecords.stream().map(InspectionRecord::getInspectorName)
                        .filter(StringUtils::hasText).findFirst().orElse(inspectorId == null ? "全部" : "用户" + inspectorId);
                row.createCell(2).setCellValue(checkedDays);
                row.createCell(3).setCellValue(abnormal);
                row.createCell(4).setCellValue(inspectorName);
                row.createCell(5).setCellValue("ABNORMAL".equals(result) ? "有异常" : "NORMAL".equals(result) ? "正常" : "全部");
            } else {
                int shouldCheck = inspectionScopeService.countRequiredDays(box, yearMonth);
                row.createCell(2).setCellValue(shouldCheck);
                row.createCell(3).setCellValue(checkedDays);
                row.createCell(4).setCellValue(Math.max(shouldCheck - checkedDays, 0));
                row.createCell(5).setCellValue(abnormal);
            }
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void writeBoxSheet(Workbook workbook, CellStyle headerStyle, ElectricBox box, List<InspectionRecord> records,
                               List<InspectionRectification> rectifications, YearMonth yearMonth, boolean filteredExport) {
        Sheet sheet = workbook.createSheet(safeSheetName(box.getBoxCode()));
        String[] headers = {"日期", "内外观", "漏电保护器", "熔断", "保护接零", "220V插座", "380V插座", "检查人", "备注"};
        writeHeader(sheet, headerStyle, headers);
        Map<LocalDate, InspectionRecord> dateRecordMap = records.stream()
                .filter(record -> Objects.equals(record.getElectricBoxId(), box.getId()))
                .collect(Collectors.toMap(InspectionRecord::getCheckDate, record -> record, (left, right) -> right));
        int rowIndex = 1;
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            InspectionRecord record = dateRecordMap.get(date);
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(date.toString());
            if (record == null) {
                for (int column = 1; column <= 6; column++) {
                    row.createCell(column).setCellValue(filteredExport ? "—" : "未检");
                }
                row.createCell(7).setCellValue("");
                row.createCell(8).setCellValue(filteredExport ? "不在当前筛选结果" : "未检");
                continue;
            }
            Map<String, InspectionRecordItem> itemMap = inspectionRecordItemMapper.selectList(
                            new LambdaQueryWrapper<InspectionRecordItem>()
                                    .eq(InspectionRecordItem::getRecordId, record.getId()))
                    .stream()
                    .collect(Collectors.toMap(InspectionRecordItem::getItemCode, item -> item, (left, right) -> right));
            int column = 1;
            for (String itemCode : EXPORT_ITEM_NAMES.keySet()) {
                row.createCell(column++).setCellValue(displayResult(itemMap.get(itemCode)));
            }
            row.createCell(7).setCellValue(record.getInspectorName());
            row.createCell(8).setCellValue(buildExportRemark(record, itemMap, rectifications));
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) {
            headerRow.createCell(index).setCellValue(headers[index]);
            headerRow.getCell(index).setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private String buildExportRemark(InspectionRecord record, Map<String, InspectionRecordItem> itemMap,
                                     List<InspectionRectification> rectifications) {
        List<String> remarks = new ArrayList<>();
        if (StringUtils.hasText(record.getRemark())) {
            remarks.add(record.getRemark());
        }
        itemMap.values().stream()
                .filter(this::isAbnormal)
                .map(item -> item.getItemName() + "异常" + (StringUtils.hasText(item.getDescription())
                        ? "：" + item.getDescription()
                        : ""))
                .forEach(remarks::add);
        return String.join("；", remarks);
    }

    private void applyReviewFilters(LambdaQueryWrapper<InspectionRecord> wrapper, String reviewScope,
                                    Boolean reviewOverdue, SysUser currentUser) {
        String normalizedScope = StringUtils.hasText(reviewScope) ? reviewScope.trim().toUpperCase() : "";
        if ("MINE".equals(normalizedScope) || "MY".equals(normalizedScope) || "MY_REVIEW".equals(normalizedScope)) {
            wrapper.eq(InspectionRecord::getAssignedReviewerId, currentUser.getId());
        } else if ("UNASSIGNED".equals(normalizedScope)) {
            wrapper.isNull(InspectionRecord::getAssignedReviewerId);
        } else if ("ASSIGNED".equals(normalizedScope)) {
            wrapper.isNotNull(InspectionRecord::getAssignedReviewerId);
        }
        if (reviewOverdue != null) {
            LocalDateTime now = LocalDateTime.now();
            if (reviewOverdue) {
                wrapper.and(condition -> condition
                        .eq(InspectionRecord::getReviewOverdue, 1)
                        .or()
                        .lt(InspectionRecord::getReviewDueTime, now));
            } else {
                wrapper.and(condition -> condition
                        .isNull(InspectionRecord::getReviewOverdue)
                        .or()
                        .eq(InspectionRecord::getReviewOverdue, 0))
                        .and(condition -> condition
                                .isNull(InspectionRecord::getReviewDueTime)
                                .or()
                                .ge(InspectionRecord::getReviewDueTime, now));
            }
        }
    }

    private void prepareReviewPending(InspectionRecord record, ElectricBox box) {
        ReviewAssignment assignment = resolveReviewAssignment(box);
        record.setStatus(STATUS_REVIEW_PENDING);
        record.setReviewStatus("PENDING");
        record.setReviewerId(null);
        record.setReviewerName(null);
        record.setReviewTime(null);
        record.setReviewComment(null);
        Integer reviewHours = projectInspectionSettingService.findOrDefault(box.getProjectId()).getReviewDueHours();
        record.setReviewDueTime(LocalDateTime.now().plusHours(reviewHours == null ? REVIEW_DEADLINE_HOURS : reviewHours));
        record.setReviewOverdue(0);
        record.setAssignedReviewerId(assignment.reviewerId());
        record.setAssignedReviewerName(assignment.reviewerName());
    }

    private ReviewAssignment resolveReviewAssignment(ElectricBox box) {
        if (box.getSafetyManagerId() != null
                && projectPermissionService.hasInspectionPermission(box.getSafetyManagerId(), box.getProjectId(), InspectionPermissionCodes.INSPECTION_REVIEW)) {
            return new ReviewAssignment(box.getSafetyManagerId(), resolveUserName(box.getSafetyManagerId(), box.getSafetyManagerName()));
        }
        return userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                        .eq(SysUserProject::getProjectId, box.getProjectId()))
                .stream()
                .filter(member -> projectPermissionService.hasInspectionPermission(member.getUserId(), box.getProjectId(), InspectionPermissionCodes.INSPECTION_REVIEW))
                .sorted(Comparator.comparingInt(member -> reviewCandidateRank(member.getProjectRoleCode())))
                .map(member -> new ReviewAssignment(member.getUserId(), resolveUserName(member.getUserId(), null)))
                .findFirst()
                .orElse(new ReviewAssignment(null, null));
    }

    private int reviewCandidateRank(String projectRoleCode) {
        String normalized = StringUtils.hasText(projectRoleCode) ? projectRoleCode.trim().toUpperCase() : "";
        if (ProjectPermissionService.ROLE_SAFETY_ADMIN.equals(normalized)) {
            return 0;
        }
        if (ProjectPermissionService.ROLE_PROJECT_ADMIN.equals(normalized)) {
            return 1;
        }
        return 2;
    }

    private String resolveUserName(Long userId, String fallback) {
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            if (user != null) {
                return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
            }
        }
        return fallback;
    }

    private void refreshReviewOverdueState(InspectionRecord record) {
        if (record == null || !STATUS_REVIEW_PENDING.equals(record.getStatus()) || record.getReviewDueTime() == null) {
            return;
        }
        if (isReviewOverdueNow(record) && !Integer.valueOf(1).equals(record.getReviewOverdue())) {
            record.setReviewOverdue(1);
            inspectionRecordMapper.updateById(record);
            writeReviewLog(record, "OVERDUE", null, "复核超过24小时未处理", null, null, null, null);
        }
    }

    private boolean isReviewOverdueNow(InspectionRecord record) {
        return record.getReviewDueTime() != null && LocalDateTime.now().isAfter(record.getReviewDueTime());
    }

    private String formatReviewDueText(InspectionRecord record) {
        if (Integer.valueOf(1).equals(record.getReviewOverdue())) {
            return "复核已逾期";
        }
        if (record.getReviewDueTime() == null) {
            return "24小时内";
        }
        return "截止 " + record.getReviewDueTime().toString().replace('T', ' ').substring(0, 16);
    }

    private void writeAssignmentLog(InspectionRecord record, Long oldReviewerId, String oldReviewerName,
                                    SysUser operator, String comment) {
        String action = record.getAssignedReviewerId() == null ? "UNASSIGN" : oldReviewerId == null ? "ASSIGN" : "REASSIGN";
        writeReviewLog(record, action, operator, comment, oldReviewerId, oldReviewerName,
                record.getAssignedReviewerId(), record.getAssignedReviewerName());
    }

    private void writeReviewLog(InspectionRecord record, String actionType, SysUser operator, String comment,
                                Long fromReviewerId, String fromReviewerName, Long toReviewerId, String toReviewerName) {
        InspectionReviewLog log = new InspectionReviewLog();
        log.setRecordId(record.getId());
        log.setProjectId(record.getProjectId());
        log.setElectricBoxId(record.getElectricBoxId());
        log.setActionType(actionType);
        log.setFromReviewerId(fromReviewerId);
        log.setFromReviewerName(fromReviewerName);
        log.setToReviewerId(toReviewerId);
        log.setToReviewerName(toReviewerName);
        log.setOperatorId(operator == null ? null : operator.getId());
        log.setOperatorName(displayUserName(operator));
        log.setComment(comment);
        log.setDeleted(0);
        inspectionReviewLogMapper.insert(log);
    }

    private void writeRectificationLog(InspectionRectification rectification, String actionType, String fromStatus,
                                       String toStatus, SysUser operator, String comment, String photoFileIds) {
        InspectionRectificationReviewLog log = new InspectionRectificationReviewLog();
        log.setRectificationId(rectification.getId());
        log.setProjectId(rectification.getProjectId());
        log.setElectricBoxId(rectification.getElectricBoxId());
        log.setInspectionRecordId(rectification.getInspectionRecordId());
        log.setActionType(actionType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(operator == null ? null : operator.getId());
        log.setOperatorName(displayUserName(operator));
        log.setComment(comment);
        log.setPhotoFileIds(photoFileIds);
        log.setDeleted(0);
        inspectionRectificationReviewLogMapper.insert(log);
    }

    private List<InspectionReviewLogVO> queryReviewLogs(Long recordId) {
        return inspectionReviewLogMapper.selectList(new LambdaQueryWrapper<InspectionReviewLog>()
                        .eq(InspectionReviewLog::getRecordId, recordId)
                        .orderByAsc(InspectionReviewLog::getCreateTime)
                        .orderByAsc(InspectionReviewLog::getId))
                .stream()
                .map(this::toReviewLogVO)
                .toList();
    }

    private InspectionReviewLogVO toReviewLogVO(InspectionReviewLog log) {
        InspectionReviewLogVO vo = new InspectionReviewLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private List<InspectionRectificationReviewLogVO> queryRectificationReviewLogs(Long rectificationId) {
        return inspectionRectificationReviewLogMapper.selectList(new LambdaQueryWrapper<InspectionRectificationReviewLog>()
                        .eq(InspectionRectificationReviewLog::getRectificationId, rectificationId)
                        .orderByAsc(InspectionRectificationReviewLog::getCreateTime)
                        .orderByAsc(InspectionRectificationReviewLog::getId))
                .stream()
                .map(this::toRectificationReviewLogVO)
                .toList();
    }

    private InspectionRectificationReviewLogVO toRectificationReviewLogVO(InspectionRectificationReviewLog log) {
        InspectionRectificationReviewLogVO vo = new InspectionRectificationReviewLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private String displayUserName(SysUser user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private void requireReviewActionPermission(SysUser currentUser, InspectionRecord record) {
        if (!STATUS_REVIEW_PENDING.equals(record.getStatus())) {
            throw new BusinessException("只有待复核记录可以执行复核");
        }
        requireInspectionManager(currentUser, record.getProjectId());
        if (record.getAssignedReviewerId() == null
                || Objects.equals(record.getAssignedReviewerId(), currentUser.getId())
                || canOverrideReviewAssignment(currentUser, record.getProjectId())) {
            return;
        }
        throw BusinessException.forbidden("该记录已分配给其他安全员复核");
    }

    private void requireReviewAssignPermission(SysUser currentUser, Long projectId) {
        if (canOverrideReviewAssignment(currentUser, projectId)) {
            return;
        }
        throw BusinessException.forbidden("无复核改派权限");
    }

    private boolean canOverrideReviewAssignment(SysUser currentUser, Long projectId) {
        return projectPermissionService.isPlatformAdmin(currentUser.getId())
                || projectPermissionService.canManageProject(currentUser.getId(), projectId)
                || projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.PERMISSION_MANAGE);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeProblemCategory(String value) {
        String category = trimToNull(value);
        if (category == null) {
            return null;
        }
        if (category.length() > 50) {
            throw new BusinessException("问题分类长度不能超过50个字符");
        }
        return category.toUpperCase();
    }

    private String resolveProblemCategory(String requestCategory, String recordCategory, InspectionRecordItem problemItem) {
        String category = normalizeProblemCategory(requestCategory);
        if (category != null) {
            return category;
        }
        category = normalizeProblemCategory(recordCategory);
        if (category != null) {
            return category;
        }
        return problemItem == null ? "OTHER" : normalizeProblemCategory(problemItem.getItemCode());
    }

    private LocalDate validateRectificationDeadline(LocalDate deadline) {
        if (deadline == null) {
            return LocalDate.now().plusDays(3);
        }
        if (deadline.isBefore(LocalDate.now())) {
            throw new BusinessException("整改期限不能早于今天");
        }
        return deadline;
    }

    private Assignee resolveAssignee(Long assigneeId, Long projectId) {
        projectPermissionService.checkProjectPermission(assigneeId, projectId);
        SysUser assignee = userMapper.selectById(assigneeId);
        if (assignee == null || (assignee.getDeleted() != null && assignee.getDeleted() == 1)) {
            throw BusinessException.notFound("整改人不存在");
        }
        return new Assignee(assigneeId, displayUserName(assignee));
    }

    private boolean isRectificationOverdue(InspectionRectification rectification) {
        return rectification.getDeadline() != null
                && !RECTIFICATION_CLOSED.equals(rectification.getStatus())
                && !RECTIFICATION_COMPLETED.equals(rectification.getStatus())
                && rectification.getDeadline().isBefore(LocalDate.now());
    }

    private String displayResult(InspectionRecordItem item) {
        if (item == null) {
            return "未填";
        }
        return isAbnormal(item) ? "异常" : "正常";
    }

    private record Assignee(Long id, String name) {
    }

    private void createRectification(InspectionRecord record, InspectionReviewRequest request) {
        ElectricBox box = requireBox(record.getElectricBoxId());
        List<InspectionRecordItem> items = inspectionRecordItemMapper.selectList(new LambdaQueryWrapper<InspectionRecordItem>()
                .eq(InspectionRecordItem::getRecordId, record.getId()));
        InspectionRecordItem problemItem = items.stream().filter(this::isAbnormal).findFirst().orElse(null);

        InspectionRectification rectification = new InspectionRectification();
        rectification.setProjectId(record.getProjectId());
        rectification.setElectricBoxId(record.getElectricBoxId());
        rectification.setInspectionRecordId(record.getId());
        rectification.setRecordItemId(problemItem == null ? null : problemItem.getId());
        rectification.setBoxCode(box.getBoxCode());
        rectification.setProblemDesc(buildProblemDesc(problemItem, request.getComment()));
        rectification.setProblemCategory(resolveProblemCategory(request.getProblemCategory(), record.getProblemCategory(), problemItem));
        rectification.setRequirement(StringUtils.hasText(request.getRequirement())
                ? request.getRequirement()
                : "请在3天内完成整改并上传整改照片");
        Long assigneeId = request.getAssigneeId() != null
                ? request.getAssigneeId()
                : box.getResponsibleElectricianId();
        String assigneeName = box.getResponsibleElectricianName();
        if (assigneeId != null) {
            Assignee assignee = resolveAssignee(assigneeId, record.getProjectId());
            assigneeName = assignee.name();
        }
        rectification.setAssigneeId(assigneeId);
        rectification.setAssigneeName(assigneeName);
        rectification.setDeadline(request.getDeadline() == null
                ? LocalDate.now().plusDays(3)
                : validateRectificationDeadline(request.getDeadline()));
        rectification.setStatus(RECTIFICATION_PENDING);
        rectification.setRejectCount(0);
        rectification.setEscalationStatus(ESCALATION_NONE);
        rectification.setDeleted(0);
        inspectionRectificationMapper.insert(rectification);
        projectMemberService.ensureProjectMember(record.getProjectId(), rectification.getAssigneeId(), ProjectPermissionService.ROLE_USER);
        writeRectificationLog(rectification, "CREATE", null, RECTIFICATION_PENDING, null, "创建整改任务", null);
        wechatNotificationService.notifyUser(rectification.getAssigneeId(), "RECTIFICATION_PENDING", "RECTIFICATION", rectification.getId(),
                box.getBoxCode() + " 待整改，截止 " + rectification.getDeadline());
    }

    private String buildProblemDesc(InspectionRecordItem problemItem, String comment) {
        if (StringUtils.hasText(comment)) {
            return comment;
        }
        if (problemItem == null) {
            return "巡检复核要求整改";
        }
        if (StringUtils.hasText(problemItem.getDescription())) {
            return problemItem.getItemName() + "：" + problemItem.getDescription();
        }
        return problemItem.getItemName() + "异常";
    }

    private void closeRecordIfAllRectificationsClosed(Long recordId) {
        if (recordId == null) {
            return;
        }
        long openCount = inspectionRectificationMapper.selectCount(new LambdaQueryWrapper<InspectionRectification>()
                .eq(InspectionRectification::getInspectionRecordId, recordId)
                .ne(InspectionRectification::getStatus, RECTIFICATION_CLOSED));
        if (openCount == 0) {
            InspectionRecord record = inspectionRecordMapper.selectById(recordId);
            if (record != null) {
                record.setStatus(STATUS_CLOSED);
                inspectionRecordMapper.updateById(record);
            }
        }
    }

    private InspectionRecordVO toRecordVO(InspectionRecord record, ElectricBox knownBox, boolean publicView) {
        return toRecordVO(record, knownBox, null, publicView);
    }

    private InspectionRecordVO toRecordVO(InspectionRecord record, ElectricBox knownBox,
                                          List<InspectionRecordItem> knownItems, boolean publicView) {
        ElectricBox box = knownBox != null ? knownBox : electricBoxMapper.selectById(record.getElectricBoxId());
        List<InspectionRecordItem> items = knownItems != null
                ? knownItems
                : inspectionRecordItemMapper.selectList(new LambdaQueryWrapper<InspectionRecordItem>()
                .eq(InspectionRecordItem::getRecordId, record.getId())
                .orderByAsc(InspectionRecordItem::getId));
        InspectionRecordVO vo = new InspectionRecordVO();
        BeanUtils.copyProperties(record, vo);
        vo.setStatus(STATUS_COMPLETED);
        vo.setReviewStatus("NOT_REQUIRED");
        vo.setReviewerId(null);
        vo.setReviewerName(null);
        vo.setAssignedReviewerId(null);
        vo.setAssignedReviewerName(null);
        vo.setReviewComment(null);
        vo.setReviewDueTime(null);
        vo.setReviewOverdue(0);
        if (box != null) {
            vo.setBoxCode(box.getBoxCode());
            vo.setBoxName(box.getBoxName());
            vo.setInstallLocation(box.getInstallLocation());
        }
        vo.setOuterPhotoCount(countIds(record.getOuterPhotoFileIds()));
        vo.setInnerPhotoCount(countIds(record.getInnerPhotoFileIds()));
        vo.setOuterPhotoFileIds(splitIds(record.getOuterPhotoFileIds()));
        vo.setInnerPhotoFileIds(splitIds(record.getInnerPhotoFileIds()));
        vo.setProblemPhotoFileIds(SOURCE_SAFETY_SPOT_CHECK.equals(record.getSource())
                ? splitIds(record.getOuterPhotoFileIds())
                : Collections.emptyList());
        vo.setInspectedAt(record.getCreateTime());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        vo.setReviewLogs(Collections.emptyList());
        if (publicView) {
            vo.setId(null);
            vo.setProjectId(null);
            vo.setElectricBoxId(null);
            vo.setInspectorId(null);
            vo.setReviewerId(null);
            vo.setReviewerName(null);
            vo.setAssignedReviewerId(null);
            vo.setAssignedReviewerName(null);
            vo.setReviewComment(null);
            vo.setReviewLogs(Collections.emptyList());
        }
        return vo;
    }

    private PublicInspectionRecordVO toPublicRecordVO(InspectionRecord record) {
        PublicInspectionRecordVO vo = new PublicInspectionRecordVO();
        vo.setCheckDate(record.getCheckDate());
        vo.setInspectedAt(record.getCreateTime());
        vo.setSource(record.getSource());
        vo.setStatus(STATUS_COMPLETED);
        vo.setAbnormalCount(record.getAbnormalCount() == null ? 0 : record.getAbnormalCount());
        return vo;
    }

    private ElectricBox requirePublicBox(String rawPublicCode) {
        String publicCode = trimToNull(rawPublicCode);
        if (!StringUtils.hasText(publicCode)) {
            throw new BusinessException("公开码不能为空");
        }
        if (publicCode.startsWith("B:")) {
            publicCode = publicCode.substring(2);
        }
        ElectricBox box = electricBoxMapper.selectOne(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getPublicCode, publicCode)
                .last("LIMIT 1"));
        if (box == null) {
            throw BusinessException.notFound("未找到电箱公开信息");
        }
        if (!Integer.valueOf(1).equals(box.getPublicAccessEnabled())) {
            throw BusinessException.forbidden("该电箱公开扫码访问已停用");
        }
        return box;
    }

    private PublicInspectionMonthRowVO buildPublicMonthRow(LocalDate date, boolean required,
                                                            InspectionRecord record,
                                                            List<InspectionRecordItem> items,
                                                            List<InspectionRectification> rectifications) {
        PublicInspectionMonthRowVO row = new PublicInspectionMonthRowVO();
        row.setDate(date);
        row.setRequired(required);
        if (date.isAfter(LocalDate.now())) {
            row.setStatus("FUTURE");
            fillPublicResult(row, "—");
            row.setRemark("尚未到巡检日期");
            return row;
        }
        if (!required) {
            row.setStatus("NON_SCOPE");
            fillPublicResult(row, "非巡检范围");
            row.setRemark("非巡检范围");
            return row;
        }
        if (record == null) {
            row.setStatus("MISSED");
            fillPublicResult(row, "未检");
            row.setRemark("未检");
            return row;
        }
        Map<String, InspectionRecordItem> itemMap = items.stream()
                .collect(Collectors.toMap(InspectionRecordItem::getItemCode, item -> item, (left, right) -> right));
        row.setAppearance(publicResult(itemMap.get("APPEARANCE")));
        row.setLeakageProtector(publicResult(itemMap.get("LEAKAGE_PROTECTOR")));
        row.setFuse(publicResult(itemMap.get("FUSE")));
        row.setProtectiveZero(publicResult(itemMap.get("PROTECTIVE_ZERO")));
        row.setSocket220v(publicResult(itemMap.get("SOCKET_220V")));
        row.setSocket380v(publicResult(itemMap.get("SOCKET_380V")));
        row.setInspectorName(record.getInspectorName());
        row.setStatus(resolvePublicMonthStatus(record, rectifications));
        row.setRemark(buildPublicMonthRemark(record, items, rectifications));
        return row;
    }

    private void fillPublicResult(PublicInspectionMonthRowVO row, String value) {
        row.setAppearance(value);
        row.setLeakageProtector(value);
        row.setFuse(value);
        row.setProtectiveZero(value);
        row.setSocket220v(value);
        row.setSocket380v(value);
    }

    private String publicResult(InspectionRecordItem item) {
        if (item == null || !StringUtils.hasText(item.getResult())) return "未填写";
        return switch (item.getResult()) {
            case "NORMAL" -> "正常";
            case "ABNORMAL" -> "异常";
            case "NA" -> "不适用";
            default -> item.getResult();
        };
    }

    private String resolvePublicMonthStatus(InspectionRecord record, List<InspectionRectification> rectifications) {
        return STATUS_COMPLETED;
    }

    private String buildPublicMonthRemark(InspectionRecord record, List<InspectionRecordItem> items,
                                          List<InspectionRectification> rectifications) {
        List<String> remarks = new ArrayList<>();
        if (StringUtils.hasText(record.getRemark())) remarks.add(record.getRemark().trim());
        items.stream()
                .filter(this::isAbnormal)
                .filter(item -> StringUtils.hasText(item.getDescription()))
                .map(item -> (StringUtils.hasText(item.getItemName()) ? item.getItemName() + "：" : "")
                        + item.getDescription().trim())
                .forEach(remarks::add);
        return remarks.isEmpty() ? "—" : String.join("；", remarks);
    }

    private InspectionRectificationVO toRectificationVO(InspectionRectification rectification) {
        ElectricBox box = electricBoxMapper.selectById(rectification.getElectricBoxId());
        InspectionRecord record = rectification.getInspectionRecordId() == null
                ? null
                : inspectionRecordMapper.selectById(rectification.getInspectionRecordId());
        InspectionRectificationVO vo = new InspectionRectificationVO();
        BeanUtils.copyProperties(rectification, vo);
        vo.setOrderNo("ZG-" + rectification.getId());
        vo.setCreatedAt(rectification.getCreateTime());
        vo.setCompletedAt(rectification.getCompletedTime());
        vo.setReviewComment(rectification.getReviewComment());
        vo.setRejectCount(rectification.getRejectCount() == null ? 0 : rectification.getRejectCount());
        vo.setRectificationPhotoFileIds(splitIds(rectification.getRectificationPhotoFileIds()));
        vo.setReviewLogs(queryRectificationReviewLogs(rectification.getId()));
        if (box != null) {
            vo.setBoxName(box.getBoxName());
            vo.setInstallLocation(box.getInstallLocation());
        }
        if (record != null) {
            vo.setInspectorName(record.getInspectorName());
            List<Long> beforePhotoIds = new ArrayList<>();
            beforePhotoIds.addAll(splitIds(record.getOuterPhotoFileIds()));
            beforePhotoIds.addAll(splitIds(record.getInnerPhotoFileIds()));
            vo.setBeforePhotoFileIds(beforePhotoIds);
        } else {
            vo.setBeforePhotoFileIds(Collections.emptyList());
        }
        return vo;
    }

    private InspectionRecordItemVO toItemVO(InspectionRecordItem item) {
        InspectionRecordItemVO vo = new InspectionRecordItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private void validateRecordRequest(InspectionRecordRequest request) {
        if (request == null) {
            throw new BusinessException("巡检记录不能为空");
        }
        if (request.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (request.getElectricBoxId() == null) {
            throw new BusinessException("电箱ID不能为空");
        }
        request.setTemplateCode(TEMPLATE_ELECTRIC_BOX_DAILY);
        request.setSource(SOURCE_ELECTRICIAN_DAILY);
        request.setProblemCategory(null);
        if (request.getItems() == null || request.getItems().size() != REQUIRED_ITEM_CODES.size()) {
            throw new BusinessException("六项检查结果必须完整填写");
        }
        validatePhotoFileIds(request.getOuterPhotoFileIds(), "外观照片");
        validatePhotoFileIds(request.getInnerPhotoFileIds(), "内部照片");
        String remark = trimToNull(request.getRemark());
        validateLength(remark, REMARK_MAX_LENGTH, "巡检备注");
        request.setRemark(remark);
        Set<String> itemCodes = new HashSet<>();
        for (InspectionItemRequest item : request.getItems()) {
            if (item == null) {
                throw new BusinessException("检查项不能为空");
            }
            String itemCode = StringUtils.hasText(item.getItemCode()) ? item.getItemCode().trim().toUpperCase() : "";
            if (!REQUIRED_ITEM_CODES.contains(itemCode)) {
                throw new BusinessException("检查项编码不支持：" + item.getItemCode());
            }
            item.setItemCode(itemCode);
            if (!itemCodes.add(itemCode)) {
                throw new BusinessException("检查项编码不能重复：" + itemCode);
            }
        }
        if (!itemCodes.containsAll(REQUIRED_ITEM_CODES)) {
            throw new BusinessException("六项检查项编码不完整");
        }
        for (InspectionItemRequest item : request.getItems()) {
            if (!StringUtils.hasText(item.getResult())) {
                throw new BusinessException(item.getItemName() + "结果不能为空");
            }
            String result = item.getResult().trim().toUpperCase();
            if (!ALLOWED_ITEM_RESULTS.contains(result)) {
                throw new BusinessException(item.getItemName() + "结果值不支持");
            }
            item.setResult(result);
            item.setItemName(EXPORT_ITEM_NAMES.get(item.getItemCode()));
            String description = trimToNull(item.getDescription());
            validateLength(description, ITEM_DESCRIPTION_MAX_LENGTH, item.getItemName() + "异常说明");
            if ("ABNORMAL".equals(result) && description == null) {
                throw new BusinessException(item.getItemName() + "异常时必须填写说明");
            }
            item.setDescription(description);
        }
    }

    private void validatePhotoFileIds(List<Long> fileIds, String fieldName) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        if (fileIds.size() > MAX_PHOTO_COUNT_PER_GROUP) {
            throw new BusinessException(fieldName + "不能超过" + MAX_PHOTO_COUNT_PER_GROUP + "张");
        }
        Set<Long> distinct = new HashSet<>();
        for (Long fileId : fileIds) {
            if (fileId == null || fileId <= 0 || !distinct.add(fileId)) {
                throw new BusinessException(fieldName + "包含重复或无效文件ID");
            }
        }
    }

    private void validateLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }

    private void ensureCheckDateAllowed(LocalDate checkDate) {
        LocalDate today = LocalDate.now();
        if (checkDate.isAfter(today)) {
            throw new BusinessException("检查日期不能晚于今天");
        }
        if (checkDate.isBefore(today.minusDays(7))) {
            throw new BusinessException("仅允许补录近7天检查记录");
        }
    }

    private void ensureDailyUnique(Long projectId, Long electricBoxId, String templateCode, String source,
                                   LocalDate checkDate) {
        long count = inspectionRecordMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getElectricBoxId, electricBoxId)
                .eq(InspectionRecord::getTemplateCode, templateCode)
                .eq(InspectionRecord::getSource, source)
                .eq(InspectionRecord::getCheckDate, checkDate)
                .ne(InspectionRecord::getStatus, STATUS_REVIEW_REJECTED));
        if (count > 0) {
            throw BusinessException.of(409, "同一电箱同一天只能提交一条有效日检");
        }
    }

    private void markCompleted(InspectionRecord record) {
        record.setStatus(STATUS_COMPLETED);
        record.setReviewStatus("NOT_REQUIRED");
        record.setReviewerId(null);
        record.setReviewerName(null);
        record.setAssignedReviewerId(null);
        record.setAssignedReviewerName(null);
        record.setReviewTime(null);
        record.setReviewComment(null);
        record.setReviewDueTime(null);
        record.setReviewOverdue(0);
    }

    private void requireInspectionManager(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_MANAGE);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.INSPECTION_REVIEW)) {
            throw BusinessException.forbidden("无检查复核权限");
        }
    }

    private void requireDailySubmitPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_SUBMIT);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)) {
            throw BusinessException.forbidden("无日检提交权限");
        }
    }

    private void requireRectificationReviewPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_MANAGE);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.RECTIFICATION_REVIEW)) {
            throw BusinessException.forbidden("无整改复查权限");
        }
    }

    private void requireSummaryPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_VIEW);
        if (!projectPermissionService.hasAnyInspectionPermission(currentUser.getId(), projectId,
                InspectionPermissionCodes.SUMMARY_VIEW,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)) {
            throw BusinessException.forbidden("无巡检汇总查看权限");
        }
    }

    private void requireSummaryExportPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_EXPORT);
        if (!projectPermissionService.hasInspectionPermission(currentUser.getId(), projectId, InspectionPermissionCodes.SUMMARY_EXPORT)) {
            throw BusinessException.forbidden("无巡检汇总导出权限");
        }
    }

    private void requireSingleBoxExportPermission(SysUser currentUser, Long projectId) {
        projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                SystemPermissionCodes.INSPECTION_EXPORT);
        if (!projectPermissionService.hasAnyInspectionPermission(currentUser.getId(), projectId,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT,
                InspectionPermissionCodes.SUMMARY_EXPORT)) {
            throw BusinessException.forbidden("无本箱月度检查表导出权限");
        }
    }

    private boolean canViewRecord(InspectionRecord record, SysUser currentUser) {
        if (!projectPermissionService.hasSystemPermission(currentUser.getId(), record.getProjectId(),
                SystemPermissionCodes.INSPECTION_VIEW)) {
            return false;
        }
        if (projectPermissionService.hasAnyInspectionPermission(currentUser.getId(), record.getProjectId(),
                InspectionPermissionCodes.INSPECTION_RECORD_VIEW,
                InspectionPermissionCodes.INSPECTION_DAILY_SUBMIT)) {
            return true;
        }
        return Objects.equals(record.getInspectorId(), currentUser.getId());
    }

    private boolean canViewRectification(InspectionRectification rectification, SysUser currentUser) {
        if (!projectPermissionService.hasSystemPermission(currentUser.getId(), rectification.getProjectId(),
                SystemPermissionCodes.INSPECTION_VIEW)) {
            return false;
        }
        if (projectPermissionService.hasInspectionPermission(currentUser.getId(), rectification.getProjectId(), InspectionPermissionCodes.RECTIFICATION_VIEW)) {
            return true;
        }
        return Objects.equals(rectification.getAssigneeId(), currentUser.getId());
    }

    private void applyProjectScope(LambdaQueryWrapper<InspectionRecord> wrapper, Long projectId, SysUser currentUser) {
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                    SystemPermissionCodes.INSPECTION_VIEW);
            wrapper.eq(InspectionRecord::getProjectId, projectId);
            return;
        }
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            return;
        }
        List<ProjectInfo> projects = projectPermissionService.getUserProjects(currentUser.getId());
        if (projects.isEmpty()) {
            wrapper.eq(InspectionRecord::getProjectId, -1L);
            return;
        }
        wrapper.in(InspectionRecord::getProjectId, projects.stream().map(ProjectInfo::getId).toList());
    }

    private void applyRectificationProjectScope(LambdaQueryWrapper<InspectionRectification> wrapper, Long projectId,
                                                SysUser currentUser) {
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            projectPermissionService.requireSystemPermission(currentUser.getId(), projectId,
                    SystemPermissionCodes.INSPECTION_VIEW);
            wrapper.eq(InspectionRectification::getProjectId, projectId);
            return;
        }
        if (projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            return;
        }
        List<ProjectInfo> projects = projectPermissionService.getUserProjects(currentUser.getId());
        if (projects.isEmpty()) {
            wrapper.eq(InspectionRectification::getProjectId, -1L);
            return;
        }
        wrapper.in(InspectionRectification::getProjectId, projects.stream().map(ProjectInfo::getId).toList());
    }

    private List<ElectricBox> queryBoxes(Long projectId, Long boxId) {
        LambdaQueryWrapper<ElectricBox> wrapper = new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId)
                .orderByAsc(ElectricBox::getBoxCode);
        if (boxId != null) {
            wrapper.eq(ElectricBox::getId, boxId);
        }
        return electricBoxMapper.selectList(wrapper);
    }

    private List<InspectionRecord> querySummaryRecords(Long projectId, Long boxId, YearMonth month,
                                                       LocalDate checkDate) {
        LambdaQueryWrapper<InspectionRecord> wrapper = new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getSource, SOURCE_ELECTRICIAN_DAILY)
                .ne(InspectionRecord::getStatus, "DRAFT");
        if (boxId != null) {
            wrapper.eq(InspectionRecord::getElectricBoxId, boxId);
        }
        if (checkDate != null) {
            wrapper.eq(InspectionRecord::getCheckDate, checkDate);
        } else {
            wrapper.between(InspectionRecord::getCheckDate, month.atDay(1), month.atEndOfMonth());
        }
        return inspectionRecordMapper.selectList(wrapper);
    }

    private ElectricBox requireBox(Long id) {
        ElectricBox box = electricBoxMapper.selectById(id);
        if (box == null) {
            throw BusinessException.notFound("电箱不存在");
        }
        return box;
    }

    private InspectionRecord requireRecord(Long id) {
        InspectionRecord record = inspectionRecordMapper.selectById(id);
        if (record == null) {
            throw BusinessException.notFound("检查记录不存在");
        }
        return record;
    }

    private InspectionRectification requireRectification(Long id) {
        InspectionRectification rectification = inspectionRectificationMapper.selectById(id);
        if (rectification == null) {
            throw BusinessException.notFound("整改任务不存在");
        }
        return rectification;
    }

    private int countAbnormal(List<InspectionItemRequest> items) {
        return (int) items.stream().filter(item -> isAbnormal(item.getResult())).count();
    }

    private boolean isAbnormal(InspectionRecordItem item) {
        return item != null && isAbnormal(item.getResult());
    }

    private boolean isAbnormal(String result) {
        return "ABNORMAL".equalsIgnoreCase(result) || "异常".equals(result);
    }

    private String normalizeAction(String action) {
        return StringUtils.hasText(action) ? action.trim().toUpperCase() : "";
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    private int countIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return 0;
        }
        return (int) Arrays.stream(ids.split(",")).filter(StringUtils::hasText).count();
    }

    private List<Long> splitIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return Collections.emptyList();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException("月份格式应为 yyyy-MM");
        }
    }

    private void ensureSinglePeriodFilter(String month, String checkDate) {
        if (StringUtils.hasText(month) && StringUtils.hasText(checkDate)) {
            throw new BusinessException("月份和检查日期不能同时传入");
        }
    }

    private LocalDate parseCheckDate(String checkDate) {
        try {
            LocalDate parsed = LocalDate.parse(checkDate.trim());
            if (parsed.isAfter(LocalDate.now())) {
                throw new BusinessException("检查日期不能晚于今天");
            }
            return parsed;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("检查日期格式应为 yyyy-MM-dd");
        }
    }

    private int countOpenRectifications(Long projectId, Long boxId, YearMonth month) {
        LambdaQueryWrapper<InspectionRectification> wrapper = new LambdaQueryWrapper<InspectionRectification>()
                .eq(InspectionRectification::getProjectId, projectId)
                .ne(InspectionRectification::getStatus, RECTIFICATION_CLOSED);
        if (boxId != null) {
            wrapper.eq(InspectionRectification::getElectricBoxId, boxId);
        }
        if (month != null) {
            wrapper.between(InspectionRectification::getDeadline, month.atDay(1), month.atEndOfMonth());
        }
        return inspectionRectificationMapper.selectCount(wrapper).intValue();
    }

    private String safeSheetName(String sheetName) {
        String normalized = StringUtils.hasText(sheetName) ? sheetName : "电箱";
        normalized = normalized.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return normalized.length() > 31 ? normalized.substring(0, 31) : normalized;
    }

    private record ReviewAssignment(Long reviewerId, String reviewerName) {
    }

    public record ExportFile(String fileName, byte[] content) {
    }
}
