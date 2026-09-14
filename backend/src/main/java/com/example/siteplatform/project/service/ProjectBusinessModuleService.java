package com.example.siteplatform.project.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.system.constant.BusinessModuleCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Project configuration is independent of role grants and applies to administrators too. */
@Service
@RequiredArgsConstructor
public class ProjectBusinessModuleService {
    private final JdbcTemplate jdbc;
    private final ProjectInfoMapper projects;
    private final OperationLogMapper logs;
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public record State(Long projectId, List<String> enabledBusinessModules, long moduleConfigVersion) {}
    public record Row(Long projectId, String projectName, String shortName,
                      List<String> enabledBusinessModules, long moduleConfigVersion) {}

    public static boolean isModulePause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException e && e.getData() instanceof Map<?,?> data
                    && "PROJECT_MODULE_DISABLED".equals(data.get("reason"))) return true;
        }
        return false;
    }

    public State state(Long projectId) {
        var rows = jdbc.queryForList("SELECT module_code, enabled, version FROM project_business_module WHERE project_id=?", projectId);
        if (rows.isEmpty() && projects.selectById(projectId) == null) throw BusinessException.notFound("项目不存在");
        if (rows.size() != BusinessModuleCodes.ALL.size()) {
            throw BusinessException.of(503, "项目模块配置不完整，请联系管理员");
        }
        var enabled = new HashSet<String>();
        long version = ((Number) rows.get(0).get("version")).longValue();
        for (var row : rows) {
            if (((Number) row.get("version")).longValue() != version) throw BusinessException.of(503, "项目模块配置版本不一致");
            if (((Number) row.get("enabled")).intValue() == 1) enabled.add((String) row.get("module_code"));
        }
        return new State(projectId, BusinessModuleCodes.ALL.stream().filter(enabled::contains).toList(), version);
    }

    public boolean isDisabled(Long projectId, String moduleCode) {
        if (moduleCode == null) return false;
        if (projectId == null || !BusinessModuleCodes.ALL.contains(moduleCode)) return true;
        return !Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM project_business_module m "
                + "JOIN project_info p ON p.id=m.project_id AND p.deleted=0 "
                + "WHERE m.project_id=? AND m.module_code=? AND m.enabled=1", Integer.class, projectId, moduleCode));
    }

    public void requireEnabled(Long projectId, String moduleCode) {
        if (isDisabled(projectId, moduleCode)) throw BusinessException.of(403, "该项目未启用此业务模块",
                Map.of("reason", "PROJECT_MODULE_DISABLED", "projectId", projectId == null ? 0 : projectId, "moduleCode", moduleCode));
    }

    /** SQL is built solely from the fixed server-side catalog, never from request text. */
    public static String enabledProjectSql(String moduleCode) {
        if (!BusinessModuleCodes.ALL.contains(moduleCode)) throw new IllegalArgumentException("Unknown business module");
        return "SELECT project_id FROM project_business_module WHERE enabled=1 AND module_code='" + moduleCode + "'";
    }

    public List<Long> filterEnabled(Collection<Long> projectIds, String moduleCode) {
        var enabled = new HashSet<>(jdbc.queryForList(enabledProjectSql(moduleCode), Long.class));
        return projectIds.stream().filter(enabled::contains).toList();
    }

    /** Initial enablement has no lower bound; only a deliberate re-enable starts a new window. */
    public LocalDateTime activatedAt(Long projectId, String moduleCode) {
        var values = jdbc.query("SELECT activated_at FROM project_business_module WHERE project_id=? AND module_code=? AND enabled=1",
                (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toLocalDateTime(), projectId, moduleCode);
        return values.isEmpty() ? null : values.get(0);
    }

    public boolean occurrenceEnabled(Long projectId, String moduleCode, LocalDateTime occurrence) {
        if (isDisabled(projectId, moduleCode)) return false;
        LocalDateTime start = activatedAt(projectId, moduleCode);
        return start == null || (occurrence != null && !occurrence.isBefore(start));
    }

    @Transactional
    public void initialize(Long projectId, Long actorId) {
        for (String module : BusinessModuleCodes.ALL) {
            if (jdbc.update("INSERT INTO project_business_module(project_id,module_code,enabled,version,updated_by,update_time) VALUES(?,?,1,1,?,?)",
                    projectId, module, actorId, LocalDateTime.now(ZONE)) != 1) throw BusinessException.of(409, "项目模块初始化失败");
        }
    }

    public PageResult<Row> list(String keyword, int pageNo, int pageSize) {
        int page = Math.max(1, pageNo), size = Math.max(1, Math.min(100, pageSize));
        String search = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM project_info WHERE deleted=0 AND (project_name LIKE ? OR short_name LIKE ?)", Long.class, search, search);
        var rows = jdbc.query("SELECT id,project_name,short_name FROM project_info WHERE deleted=0 AND (project_name LIKE ? OR short_name LIKE ?) ORDER BY id LIMIT ? OFFSET ?",
                (rs, n) -> { var state = state(rs.getLong("id")); return new Row(state.projectId(), rs.getString("project_name"), rs.getString("short_name"), state.enabledBusinessModules(), state.moduleConfigVersion()); },
                search, search, size, ((long) page - 1) * size);
        return PageResult.of(page, size, total == null ? 0 : total, rows);
    }

    @Transactional
    public State update(Long projectId, List<String> moduleCodes, long expectedVersion, SysUser actor) {
        if (moduleCodes == null || moduleCodes.size() > 5 || new HashSet<>(moduleCodes).size() != moduleCodes.size()
                || !BusinessModuleCodes.ALL.containsAll(moduleCodes)) throw new BusinessException("请选择有效且不重复的业务模块");
        if (projects.selectByIdForUpdate(projectId) == null) throw BusinessException.notFound("项目不存在");
        State before = state(projectId);
        if (expectedVersion != before.moduleConfigVersion()) throw BusinessException.of(409, "配置已被其他管理员修改，请刷新后核对再保存");
        if (new HashSet<>(moduleCodes).equals(new HashSet<>(before.enabledBusinessModules()))) return before;
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (String module : BusinessModuleCodes.ALL) {
            boolean enabled = moduleCodes.contains(module);
            boolean reenabled = enabled && !before.enabledBusinessModules().contains(module);
            if (jdbc.update("UPDATE project_business_module SET enabled=?,version=version+1,activated_at=CASE WHEN ? THEN ? ELSE activated_at END,updated_by=?,update_time=? WHERE project_id=? AND module_code=? AND version=?",
                    enabled ? 1 : 0, reenabled, now, actor.getId(), now, projectId, module, expectedVersion) != 1) throw BusinessException.of(409, "项目模块保存冲突，请刷新后重试");
        }
        OperationLog log = new OperationLog();
        log.setUserId(actor.getId()); log.setUsername(actor.getUsername());
        log.setOperationType("UPDATE_PROJECT_MODULES"); log.setBusinessType("PROJECT_MODULES"); log.setBusinessId(projectId);
        log.setOperationDesc("启用模块 " + before.enabledBusinessModules() + " → " + BusinessModuleCodes.ALL.stream().filter(moduleCodes::contains).toList()
                + "；版本 " + expectedVersion + " → " + (expectedVersion + 1));
        log.setCreateTime(now);
        if (logs.insert(log) != 1) throw BusinessException.of(409, "项目模块审计写入失败");
        return state(projectId);
    }
}
