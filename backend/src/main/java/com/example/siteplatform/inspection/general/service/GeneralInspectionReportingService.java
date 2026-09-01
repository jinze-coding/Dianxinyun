package com.example.siteplatform.inspection.general.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.electricbox.mapper.ElectricBoxMapper;
import com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import com.example.siteplatform.inspection.entity.InspectionRectification;
import com.example.siteplatform.inspection.mapper.InspectionRecordMapper;
import com.example.siteplatform.inspection.mapper.InspectionRectificationMapper;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.mapper.*;
import com.example.siteplatform.inspection.general.vo.GeneralInspectionDashboardVO;
import com.example.siteplatform.inspection.general.vo.PublicGeneralInspectionMonthlyVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GeneralInspectionReportingService {

    private final GeneralInspectionPermissionService permissionService;
    private final GeneralInspectionTaskMapper taskMapper;
    private final GeneralInspectionTaskItemMapper taskItemMapper;
    private final GeneralInspectionRectificationMapper rectificationMapper;
    private final GeneralInspectionPointMapper pointMapper;
    private final GeneralInspectionTemplateVersionMapper templateVersionMapper;
    private final ProjectInfoMapper projectMapper;
    private final ElectricBoxMapper electricBoxMapper;
    private final ElectricBoxInspectionScopeService electricBoxScopeService;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final InspectionRectificationMapper inspectionRectificationMapper;

    public GeneralInspectionDashboardVO dashboard(Long projectId, LocalDate startDate, LocalDate endDate,
                                                   SysUser currentUser) {
        permissionService.requireSummaryView(projectId, currentUser);
        validatePeriod(startDate, endDate, 366);
        LocalDate effectiveEnd = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;
        List<GeneralInspectionTask> tasks = effectiveEnd.isBefore(startDate) ? List.of()
                : taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getProjectId, projectId)
                .between(GeneralInspectionTask::getOccurrenceDate, startDate, effectiveEnd)
                .ne(GeneralInspectionTask::getStatus, "CANCELLED"));
        Set<Long> taskIds = tasks.stream().map(GeneralInspectionTask::getId).collect(Collectors.toSet());
        List<GeneralInspectionRectification> rectifications = taskIds.isEmpty() ? List.of()
                : rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                .in(GeneralInspectionRectification::getTaskId, taskIds));
        LocalDateTime now = LocalDateTime.now();
        long generalCompleted = tasks.stream().filter(task -> task.getSubmittedTime() != null).count();
        long generalOnTime = tasks.stream().filter(task -> task.getSubmittedTime() != null && Integer.valueOf(1).equals(task.getOnTime())).count();
        long late = tasks.stream().filter(task -> task.getSubmittedTime() != null && Integer.valueOf(0).equals(task.getOnTime())).count();
        long generalMissed = tasks.stream().filter(task -> "PENDING".equals(task.getStatus()) && now.isAfter(task.getDueTime())).count();
        long generalAbnormal = tasks.stream().filter(task -> value(task.getAbnormalCount()) > 0).count();

        ElectricKpi electric = electricKpi(projectId, startDate, effectiveEnd);
        long openRectifications = rectifications.stream().filter(rect -> !Set.of("CLOSED", "VOIDED").contains(rect.getStatus())).count()
                + electric.openRectifications();
        long closedRectifications = rectifications.stream().filter(rect -> "CLOSED".equals(rect.getStatus())).count()
                + electric.closedRectifications();
        long validRectifications = rectifications.stream().filter(rect -> !"VOIDED".equals(rect.getStatus())).count()
                + electric.validRectifications();
        long due = tasks.size() + electric.due();
        long completed = generalCompleted + electric.completed();
        long onTime = generalOnTime + electric.completed();
        long missed = generalMissed + electric.missed();
        long abnormal = generalAbnormal + electric.abnormal();

        GeneralInspectionDashboardVO vo = new GeneralInspectionDashboardVO();
        vo.setProjectId(projectId);
        vo.setElectricBoxDueCount(electric.due());
        vo.setElectricBoxCompletedCount(electric.completed());
        vo.setElectricBoxMissedCount(electric.missed());
        vo.setElectricBoxAbnormalCount(electric.abnormal());
        vo.setGeneralDueCount((long) tasks.size());
        vo.setGeneralCompletedCount(generalCompleted);
        vo.setGeneralMissedCount(generalMissed);
        vo.setGeneralAbnormalCount(generalAbnormal);
        vo.setDueCount(due);
        vo.setCompletedCount(completed);
        vo.setOnTimeCount(onTime);
        vo.setLateCompletedCount(late);
        vo.setMissedCount(missed);
        vo.setAbnormalTaskCount(abnormal);
        vo.setOpenRectificationCount(openRectifications);
        vo.setClosedRectificationCount(closedRectifications);
        vo.setCompletionRate(rate(completed, due));
        vo.setOnTimeRate(rate(onTime, due));
        vo.setRectificationClosureRate(rate(closedRectifications, validRectifications));
        vo.setBreakdowns(buildBreakdowns(tasks, rectifications, electric, now));
        return vo;
    }

    /** Returns clinical edge-inspection KPIs only; electric-box records are deliberately excluded. */
    public GeneralInspectionDashboardVO edgeStatistics(Long projectId, LocalDate startDate, LocalDate endDate,
                                                        SysUser currentUser) {
        permissionService.requireSummaryView(projectId, currentUser);
        validatePeriod(startDate, endDate, 366);
        LocalDate effectiveEnd = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;
        List<GeneralInspectionTask> tasks = effectiveEnd.isBefore(startDate) ? List.of()
                : taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getProjectId, projectId)
                .isNotNull(GeneralInspectionTask::getPointTypeCode)
                .between(GeneralInspectionTask::getOccurrenceDate, startDate, effectiveEnd)
                .ne(GeneralInspectionTask::getStatus, "CANCELLED"));
        Set<Long> taskIds = tasks.stream().map(GeneralInspectionTask::getId).collect(Collectors.toSet());
        List<GeneralInspectionRectification> rectifications = taskIds.isEmpty() ? List.of()
                : rectificationMapper.selectList(new LambdaQueryWrapper<GeneralInspectionRectification>()
                .in(GeneralInspectionRectification::getTaskId, taskIds)
                .ne(GeneralInspectionRectification::getStatus, "VOIDED"));
        LocalDateTime now = LocalDateTime.now();
        long completed = tasks.stream().filter(task -> task.getSubmittedTime() != null).count();
        long onTime = tasks.stream().filter(task -> task.getSubmittedTime() != null
                && Integer.valueOf(1).equals(task.getOnTime())).count();
        long late = tasks.stream().filter(task -> task.getSubmittedTime() != null
                && Integer.valueOf(0).equals(task.getOnTime())).count();
        long missed = tasks.stream().filter(task -> "PENDING".equals(task.getStatus())
                && now.isAfter(task.getDueTime())).count();
        long abnormal = tasks.stream().filter(task -> value(task.getAbnormalCount()) > 0).count();
        Map<Long, List<GeneralInspectionRectification>> rectificationSheets = rectifications.stream()
                .collect(Collectors.groupingBy(GeneralInspectionRectification::getTaskId));
        long closedRectifications = rectificationSheets.values().stream()
                .filter(items -> !items.isEmpty() && items.stream()
                        .allMatch(rectification -> "CLOSED".equals(rectification.getStatus())))
                .count();
        long openRectifications = rectificationSheets.size() - closedRectifications;

        GeneralInspectionDashboardVO vo = new GeneralInspectionDashboardVO();
        vo.setProjectId(projectId);
        vo.setElectricBoxDueCount(0L);
        vo.setElectricBoxCompletedCount(0L);
        vo.setElectricBoxMissedCount(0L);
        vo.setElectricBoxAbnormalCount(0L);
        vo.setGeneralDueCount((long) tasks.size());
        vo.setGeneralCompletedCount(completed);
        vo.setGeneralMissedCount(missed);
        vo.setGeneralAbnormalCount(abnormal);
        vo.setDueCount((long) tasks.size());
        vo.setCompletedCount(completed);
        vo.setOnTimeCount(onTime);
        vo.setLateCompletedCount(late);
        vo.setMissedCount(missed);
        vo.setAbnormalTaskCount(abnormal);
        vo.setOpenRectificationCount(openRectifications);
        vo.setClosedRectificationCount(closedRectifications);
        vo.setCompletionRate(rate(completed, tasks.size()));
        vo.setOnTimeRate(rate(onTime, tasks.size()));
        vo.setRectificationClosureRate(rate(closedRectifications, rectificationSheets.size()));
        vo.setBreakdowns(buildBreakdowns(tasks, rectifications, ElectricKpi.EMPTY, now));
        return vo;
    }

    private ElectricKpi electricKpi(Long projectId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) return ElectricKpi.EMPTY;
        List<ElectricBox> boxes = electricBoxMapper.selectList(new LambdaQueryWrapper<ElectricBox>()
                .eq(ElectricBox::getProjectId, projectId));
        Map<Long, Set<LocalDate>> requiredByBox = new LinkedHashMap<>();
        for (ElectricBox box : boxes) {
            requiredByBox.put(box.getId(), electricBoxScopeService.requiredDates(box, startDate, endDate));
        }
        long due = requiredByBox.values().stream().mapToLong(Set::size).sum();
        List<InspectionRecord> records = inspectionRecordMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getProjectId, projectId)
                .eq(InspectionRecord::getSource, "ELECTRICIAN_DAILY")
                .ne(InspectionRecord::getStatus, "DRAFT")
                .between(InspectionRecord::getCheckDate, startDate, endDate));
        Map<String, InspectionRecord> validRecords = new LinkedHashMap<>();
        for (InspectionRecord record : records) {
            if (record.getElectricBoxId() == null || record.getCheckDate() == null
                    || !requiredByBox.getOrDefault(record.getElectricBoxId(), Set.of()).contains(record.getCheckDate())) continue;
            validRecords.putIfAbsent(record.getElectricBoxId() + ":" + record.getCheckDate(), record);
        }
        long completed = validRecords.size();
        long abnormal = validRecords.values().stream().filter(record -> value(record.getAbnormalCount()) > 0).count();
        Set<Long> recordIds = validRecords.values().stream().map(InspectionRecord::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<InspectionRectification> rectifications = recordIds.isEmpty() ? List.of()
                : inspectionRectificationMapper.selectList(new LambdaQueryWrapper<InspectionRectification>()
                .in(InspectionRectification::getInspectionRecordId, recordIds));
        long open = rectifications.stream().filter(rect -> !"CLOSED".equals(rect.getStatus())).count();
        long closed = rectifications.stream().filter(rect -> "CLOSED".equals(rect.getStatus())).count();
        return new ElectricKpi(due, completed, Math.max(due - completed, 0), abnormal,
                open, closed, rectifications.size(), boxes, requiredByBox, validRecords, rectifications);
    }

    private record ElectricKpi(long due, long completed, long missed, long abnormal,
                               long openRectifications, long closedRectifications,
                               long validRectifications, List<ElectricBox> boxes,
                               Map<Long, Set<LocalDate>> requiredByBox,
                               Map<String, InspectionRecord> records,
                               List<InspectionRectification> rectifications) {
        private static final ElectricKpi EMPTY = new ElectricKpi(0, 0, 0, 0, 0, 0, 0,
                List.of(), Map.of(), Map.of(), List.of());
    }

    private List<GeneralInspectionDashboardVO.DimensionStat> buildBreakdowns(
            List<GeneralInspectionTask> tasks,
            List<GeneralInspectionRectification> generalRectifications,
            ElectricKpi electric,
            LocalDateTime now) {
        Map<String, DimensionAccumulator> dimensions = new LinkedHashMap<>();
        Map<Long, GeneralInspectionTask> taskById = tasks.stream().collect(Collectors.toMap(
                GeneralInspectionTask::getId, task -> task, (a, b) -> a));
        for (GeneralInspectionTask task : tasks) {
            boolean completed = task.getSubmittedTime() != null;
            boolean onTime = completed && Integer.valueOf(1).equals(task.getOnTime());
            boolean late = completed && Integer.valueOf(0).equals(task.getOnTime());
            boolean missed = !completed && now.isAfter(task.getDueTime());
            boolean abnormal = value(task.getAbnormalCount()) > 0;
            for (DimensionRef ref : generalDimensions(task)) {
                accumulator(dimensions, ref).occurrence(completed, onTime, late, missed, abnormal);
            }
        }
        for (GeneralInspectionRectification rectification : generalRectifications) {
            GeneralInspectionTask task = taskById.get(rectification.getTaskId());
            if (task == null || "VOIDED".equals(rectification.getStatus())) continue;
            for (DimensionRef ref : generalDimensions(task)) {
                accumulator(dimensions, ref).rectification("CLOSED".equals(rectification.getStatus()),
                        rectification.getCreateTime(), rectification.getCloseTime());
            }
        }

        Map<Long, ElectricBox> boxById = electric.boxes().stream().collect(Collectors.toMap(
                ElectricBox::getId, box -> box, (a, b) -> a));
        Map<Long, InspectionRecord> electricRecordById = electric.records().values().stream()
                .filter(record -> record.getId() != null)
                .collect(Collectors.toMap(InspectionRecord::getId, record -> record, (a, b) -> a));
        for (Map.Entry<Long, Set<LocalDate>> entry : electric.requiredByBox().entrySet()) {
            ElectricBox box = boxById.get(entry.getKey());
            if (box == null) continue;
            for (LocalDate date : entry.getValue()) {
                InspectionRecord record = electric.records().get(box.getId() + ":" + date);
                boolean completed = record != null;
                boolean abnormal = completed && value(record.getAbnormalCount()) > 0;
                boolean missed = !completed && date.isBefore(LocalDate.now());
                for (DimensionRef ref : electricDimensions(box, record, date)) {
                    accumulator(dimensions, ref).occurrence(completed, completed, false, missed, abnormal);
                }
            }
        }
        for (InspectionRectification rectification : electric.rectifications()) {
            InspectionRecord record = electricRecordById.get(rectification.getInspectionRecordId());
            ElectricBox box = record == null ? boxById.get(rectification.getElectricBoxId())
                    : boxById.get(record.getElectricBoxId());
            if (box == null) continue;
            LocalDate date = record == null || record.getCheckDate() == null
                    ? rectification.getCreateTime() == null ? LocalDate.now() : rectification.getCreateTime().toLocalDate()
                    : record.getCheckDate();
            for (DimensionRef ref : electricDimensions(box, record, date)) {
                accumulator(dimensions, ref).rectification("CLOSED".equals(rectification.getStatus()),
                        rectification.getCreateTime(), rectification.getCloseTime());
            }
        }
        return dimensions.values().stream().map(DimensionAccumulator::toVO).toList();
    }

    private List<DimensionRef> generalDimensions(GeneralInspectionTask task) {
        String personKey = task.getAssigneeId() == null ? "UNASSIGNED" : String.valueOf(task.getAssigneeId());
        String personName = StringUtils.hasText(task.getAssigneeName()) ? task.getAssigneeName() : "待改派";
        return List.of(
                new DimensionRef("TYPE", "EDGE_INSPECTION", "临边巡检"),
                new DimensionRef("POINT_TYPE", StringUtils.hasText(task.getPointTypeCode())
                        ? task.getPointTypeCode() : "UNKNOWN",
                        StringUtils.hasText(task.getPointTypeName()) ? task.getPointTypeName() : "未识别类型"),
                new DimensionRef("POINT", "G:" + task.getPointId(), task.getPointCode() + " · " + task.getPointName()),
                new DimensionRef("PERSON", personKey, personName),
                new DimensionRef("DATE", task.getOccurrenceDate().toString(), task.getOccurrenceDate().toString()));
    }

    private List<DimensionRef> electricDimensions(ElectricBox box, InspectionRecord record, LocalDate date) {
        Long personId = record == null ? box.getResponsibleElectricianId() : record.getInspectorId();
        String personName = record == null ? box.getResponsibleElectricianName() : record.getInspectorName();
        return List.of(
                new DimensionRef("TYPE", "ELECTRIC_BOX", "电箱巡检"),
                new DimensionRef("POINT", "E:" + box.getId(), box.getBoxCode() + " · " + box.getBoxName()),
                new DimensionRef("PERSON", personId == null ? "UNASSIGNED" : String.valueOf(personId),
                        StringUtils.hasText(personName) ? personName : "待分配"),
                new DimensionRef("DATE", date.toString(), date.toString()));
    }

    private DimensionAccumulator accumulator(Map<String, DimensionAccumulator> dimensions, DimensionRef ref) {
        return dimensions.computeIfAbsent(ref.dimension() + ":" + ref.key(), ignored ->
                new DimensionAccumulator(ref));
    }

    private record DimensionRef(String dimension, String key, String name) { }

    private class DimensionAccumulator {
        private final DimensionRef ref;
        private long due;
        private long completed;
        private long onTime;
        private long late;
        private long missed;
        private long abnormal;
        private long openRectifications;
        private long closedRectifications;
        private double closedHours;

        private DimensionAccumulator(DimensionRef ref) { this.ref = ref; }

        private void occurrence(boolean checked, boolean timely, boolean lateChecked,
                                boolean overdueMissed, boolean hasAbnormal) {
            due++;
            if (checked) completed++;
            if (timely) onTime++;
            if (lateChecked) late++;
            if (overdueMissed) missed++;
            if (hasAbnormal) abnormal++;
        }

        private void rectification(boolean closed, LocalDateTime created, LocalDateTime closedTime) {
            if (closed) {
                closedRectifications++;
                if (created != null && closedTime != null && !closedTime.isBefore(created)) {
                    closedHours += java.time.Duration.between(created, closedTime).toMinutes() / 60D;
                }
            } else {
                openRectifications++;
            }
        }

        private GeneralInspectionDashboardVO.DimensionStat toVO() {
            GeneralInspectionDashboardVO.DimensionStat vo = new GeneralInspectionDashboardVO.DimensionStat();
            vo.setDimension(ref.dimension());
            vo.setDimensionKey(ref.key());
            vo.setDimensionName(ref.name());
            vo.setDueCount(due);
            vo.setCompletedCount(completed);
            vo.setOnTimeCount(onTime);
            vo.setLateCompletedCount(late);
            vo.setMissedCount(missed);
            vo.setAbnormalCount(abnormal);
            vo.setOpenRectificationCount(openRectifications);
            vo.setClosedRectificationCount(closedRectifications);
            vo.setRectificationClosureRate(rate(closedRectifications, openRectifications + closedRectifications));
            vo.setAverageCloseHours(closedRectifications == 0 ? 0D
                    : Math.round(closedHours * 100D / closedRectifications) / 100D);
            return vo;
        }
    }

    public PublicGeneralInspectionMonthlyVO publicMonthly(String rawCode, YearMonth month) {
        String code = normalizePublicCode(rawCode);
        YearMonth target = month == null ? YearMonth.now() : month;
        YearMonth earliest = YearMonth.now().minusMonths(11);
        if (target.isBefore(earliest) || target.isAfter(YearMonth.now())) {
            throw new BusinessException("匿名月表仅提供最近12个月记录");
        }
        GeneralInspectionPoint point = pointMapper.selectOne(new LambdaQueryWrapper<GeneralInspectionPoint>()
                .eq(GeneralInspectionPoint::getPublicCode, code)
                .eq(GeneralInspectionPoint::getDeleted, 0).last("LIMIT 1"));
        if (point == null) throw BusinessException.notFound("巡检点位码不存在或已换码");
        if (!"ACTIVE".equals(point.getStatus())
                || !Integer.valueOf(1).equals(point.getPublicAccessEnabled())) {
            throw BusinessException.forbidden("该点位未开放匿名巡检月表");
        }
        List<GeneralInspectionTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTask>()
                .eq(GeneralInspectionTask::getPointId, point.getId())
                .between(GeneralInspectionTask::getOccurrenceDate, target.atDay(1), target.atEndOfMonth())
                .ne(GeneralInspectionTask::getStatus, "CANCELLED")
                .orderByAsc(GeneralInspectionTask::getOccurrenceDate)
                .orderByAsc(GeneralInspectionTask::getStartTime));
        ProjectInfo project = projectMapper.selectById(point.getProjectId());
        PublicGeneralInspectionMonthlyVO vo = new PublicGeneralInspectionMonthlyVO();
        vo.setProjectShortName(project == null ? "项目" : StringUtils.hasText(project.getShortName())
                ? project.getShortName() : project.getProjectName());
        vo.setPointCode(point.getPointCode());
        vo.setPointName(point.getPointName());
        vo.setLocationDesc(point.getLocationDesc());
        vo.setMonth(target.toString());
        vo.setShouldCheckCount(tasks.size());
        vo.setCheckedCount((int) tasks.stream().filter(task -> task.getSubmittedTime() != null).count());
        vo.setMissedCount((int) tasks.stream().filter(task -> "PENDING".equals(task.getStatus())
                && LocalDateTime.now().isAfter(task.getDueTime())).count());
        vo.setAbnormalCount((int) tasks.stream().filter(task -> value(task.getAbnormalCount()) > 0).count());
        Map<Long, List<GeneralInspectionTask>> grouped = tasks.stream().collect(Collectors.groupingBy(
                GeneralInspectionTask::getTemplateVersionId, LinkedHashMap::new, Collectors.toList()));
        List<PublicGeneralInspectionMonthlyVO.VersionSection> sections = new ArrayList<>();
        int sectionIndex = 1;
        for (Map.Entry<Long, List<GeneralInspectionTask>> entry : grouped.entrySet()) {
            PublicGeneralInspectionMonthlyVO.VersionSection section = new PublicGeneralInspectionMonthlyVO.VersionSection();
            GeneralInspectionTemplateVersion version = templateVersionMapper.selectById(entry.getKey());
            section.setVersionLabel("第" + sectionIndex++ + "段 · " + (version == null ? "历史模板" : version.getTemplateName()));
            section.setTemplateName(version == null ? entry.getValue().get(0).getTemplateName() : version.getTemplateName());
            List<GeneralInspectionTaskItem> firstItems = listItems(entry.getValue().get(0).getId());
            section.setItemNames(firstItems.stream().map(GeneralInspectionTaskItem::getItemName).toList());
            section.setRows(entry.getValue().stream().map(task -> publicRow(task, firstItems)).toList());
            sections.add(section);
        }
        vo.setSections(sections);
        return vo;
    }

    private PublicGeneralInspectionMonthlyVO.Row publicRow(GeneralInspectionTask task,
                                                            List<GeneralInspectionTaskItem> itemOrder) {
        PublicGeneralInspectionMonthlyVO.Row row = new PublicGeneralInspectionMonthlyVO.Row();
        row.setDate(task.getOccurrenceDate().toString());
        row.setSlotName(task.getSlotName());
        row.setStatus(publicStatus(task));
        row.setInspectorName(task.getSubmittedTime() == null ? null : task.getSubmittedByName());
        row.setPublicRemark(task.getSubmittedTime() == null ? null : task.getPublicRemark());
        Map<String, String> results = listItems(task.getId()).stream().collect(Collectors.toMap(
                GeneralInspectionTaskItem::getItemKey,
                item -> item.getResult() == null ? "未检" : resultLabel(item.getResult()), (a, b) -> b));
        row.setResults(itemOrder.stream().map(item -> results.getOrDefault(item.getItemKey(), "未检")).toList());
        return row;
    }

    private List<GeneralInspectionTaskItem> listItems(Long taskId) {
        return taskItemMapper.selectList(new LambdaQueryWrapper<GeneralInspectionTaskItem>()
                .eq(GeneralInspectionTaskItem::getTaskId, taskId)
                .orderByAsc(GeneralInspectionTaskItem::getSortOrder));
    }

    private String publicStatus(GeneralInspectionTask task) {
        if (task.getSubmittedTime() != null && Integer.valueOf(0).equals(task.getOnTime())) return "逾期补检";
        if (task.getSubmittedTime() != null && value(task.getAbnormalCount()) > 0) return "已检有异常";
        if (task.getSubmittedTime() != null) return "已检正常";
        if (LocalDateTime.now().isAfter(task.getDueTime())) return "未检";
        return "待检";
    }

    private String resultLabel(String result) {
        return switch (result) {
            case "NORMAL" -> "正常";
            case "ABNORMAL" -> "异常";
            case "NA" -> "不适用";
            default -> "未检";
        };
    }

    private String normalizePublicCode(String raw) {
        if (!StringUtils.hasText(raw)) throw new BusinessException("点位码不能为空");
        String value = raw.trim();
        if (value.startsWith("P:")) value = value.substring(2);
        if (!value.matches("[A-Za-z0-9_-]{20,80}")) throw new BusinessException("点位码格式无效");
        return value;
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate, int maxDays) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) throw new BusinessException("统计日期范围无效");
        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1 > maxDays) {
            throw new BusinessException("统计日期范围不能超过" + maxDays + "天");
        }
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) return 0D;
        return Math.round(numerator * 10000D / denominator) / 100D;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
