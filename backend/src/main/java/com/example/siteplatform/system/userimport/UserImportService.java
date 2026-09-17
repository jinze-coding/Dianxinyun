package com.example.siteplatform.system.userimport;

import com.example.siteplatform.system.userimport.mapper.UserImportBatchMapper;
import com.example.siteplatform.system.userimport.mapper.UserImportItemMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.mapper.SysUserMapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.PasswordCredentialService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.log.entity.OperationLog;
import com.example.siteplatform.log.mapper.OperationLogMapper;
import com.example.siteplatform.project.entity.*;
import com.example.siteplatform.project.mapper.*;
import com.example.siteplatform.registration.entity.RegistrationApplication;
import com.example.siteplatform.registration.mapper.RegistrationApplicationMapper;
import com.example.siteplatform.system.entity.SystemRole;
import com.example.siteplatform.system.mapper.SystemRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserImportService {
    private final UserImportBatchMapper batches;
    private final UserImportItemMapper items;
    private final SysUserMapper users;
    private final ProjectInfoMapper projects;
    private final SystemRoleMapper roles;
    private final SysUserProjectMapper memberships;
    private final SysUserProjectRoleMapper assignments;
    private final RegistrationApplicationMapper registrations;
    private final OperationLogMapper logs;
    private final UserImportWorkbook workbook;
    private final UserImportCredentialCipher cipher;
    private final PasswordCredentialService passwords;
    private final AuthService auth;
    private final PlatformTransactionManager transactionManager;

    public void requireAdmin(Long id) {
        SysUser user = users.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || auth.requiresInitialPasswordSetup(user)
                || !users.selectRoleCodesByUserId(id).contains("PLATFORM_ADMIN")) throw BusinessException.forbidden("仅平台管理员可以管理用户导入");
    }
    public byte[] template(SysUser operator) {
        requireAdmin(operator.getId());
        return workbook.template(availableProjects(), availableRoles());
    }
    private List<ProjectInfo> availableProjects() {
        return projects.selectList(new LambdaQueryWrapper<ProjectInfo>().and(q -> q.isNull(ProjectInfo::getProjectStatus).or().ne(ProjectInfo::getProjectStatus, "stopped")).orderByAsc(ProjectInfo::getId));
    }
    private List<SystemRole> availableRoles() {
        return roles.selectList(new LambdaQueryWrapper<SystemRole>().eq(SystemRole::getScopeType, "PROJECT").eq(SystemRole::getEnabled, 1).orderByAsc(SystemRole::getId));
    }

    @Transactional
    public Map<String, Object> preview(MultipartFile file, SysUser operator) {
        requireAdmin(operator.getId()); cipher.requireConfigured();
        List<UserImportItem> rows = workbook.parse(file);
        Map<Long, ProjectInfo> projectMap = availableProjects().stream().collect(Collectors.toMap(ProjectInfo::getId, p -> p));
        Map<Long, SystemRole> roleMap = availableRoles().stream().collect(Collectors.toMap(SystemRole::getId, p -> p));
        Map<String, List<UserImportItem>> people = rows.stream().collect(Collectors.groupingBy(UserImportItem::getPhone, LinkedHashMap::new, Collectors.toList()));
        for (var person : people.entrySet()) {
            String phone = person.getKey(); List<UserImportItem> personRows = person.getValue();
            boolean validPhone = phone.matches("^1\\d{10}$");
            SysUser existing = validPhone ? users.selectOccupiedIdentity(phone) : null;
            if (existing != null) {
                String message = Integer.valueOf(1).equals(existing.getDeleted()) ? "历史账号占用，不能通过导入恢复"
                        : Integer.valueOf(0).equals(existing.getStatus()) ? "账号已停用，跳过且不修改" : "已有账号，跳过且不修改";
                personRows.forEach(row -> result(row, "SKIPPED", message)); continue;
            }
            boolean pending = validPhone && pending(phone);
            boolean namesConflict = personRows.stream().map(UserImportItem::getRealName).distinct().count() > 1;
            Set<String> seen = new HashSet<>();
            for (UserImportItem row : personRows) {
                List<String> errors = new ArrayList<>();
                if (!validPhone) errors.add("手机号须为11位有效号码");
                if (row.getRealName().isBlank()) errors.add("姓名必填");
                if (namesConflict) errors.add("同一手机号的姓名不一致");
                if (pending) errors.add("手机号存在待审核注册申请，请先处理原申请");
                Long projectId = labelId(row.getProjectLabel(), "P"), roleId = labelId(row.getRoleLabel(), "R");
                if (!projectMap.containsKey(projectId)) errors.add("请选择现有有效项目的完整选项");
                if (!roleMap.containsKey(roleId)) errors.add("请选择有效项目角色，不能导入平台角色");
                row.setProjectId(projectId); row.setRoleId(roleId);
                if (!errors.isEmpty()) result(row, "ERROR", String.join("；", errors));
                else {
                    row.setProjectLabel(UserImportWorkbook.projectLabel(projectMap.get(projectId)));
                    row.setRoleLabel(UserImportWorkbook.roleLabel(roleMap.get(roleId)));
                    boolean uniqueAssignment = seen.add(projectId + ":" + roleId);
                    result(row, uniqueAssignment ? "NEW" : "DUPLICATE", uniqueAssignment ? "同一手机号合并建号" : "重复授权自动合并");
                }
            }
            if (personRows.stream().anyMatch(row -> "ERROR".equals(row.getStatus()))) {
                personRows.stream().filter(row -> !"ERROR".equals(row.getStatus())).forEach(row -> result(row, "ERROR", "该人员其他行有错误，请一起修正"));
            }
        }
        UserImportBatch batch = new UserImportBatch(); batch.setCreatedBy(operator.getId());
        batch.setCreatedByName(Objects.toString(operator.getRealName(), operator.getUsername()));
        batch.setPersonCount(people.size()); batch.setNewCount((int) people.values().stream().filter(p -> p.stream().anyMatch(r -> "NEW".equals(r.getStatus()))).count());
        batch.setSkippedCount((int) people.values().stream().filter(p -> "SKIPPED".equals(p.get(0).getStatus())).count());
        batch.setErrorCount((int) rows.stream().filter(r -> "ERROR".equals(r.getStatus())).count());
        batch.setStatus(batch.getErrorCount() == 0 ? "PREVIEW" : "INVALID"); batch.setPreparedCount(0);
        batch.setMessage(batch.getErrorCount() == 0 ? "校验完成，请核对新增账号和项目角色" : "存在错误，请修正名单后重新上传");
        batch.setCreatedAt(LocalDateTime.now()); batch.setUpdatedAt(batch.getCreatedAt()); write(batches.insert(batch));
        for (UserImportItem row : rows) { row.setBatchId(batch.getId()); write(items.insert(row)); }
        audit(operator, "PREVIEW_USER_IMPORT", batch.getId(), "校验名单：" + batch.getPersonCount() + "人，错误" + batch.getErrorCount() + "行");
        return view(batch, rows, operator.getId());
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String, Object> confirm(Long id, String requestKey, String temporaryPassword, SysUser operator) {
        requireAdmin(operator.getId()); cipher.requireConfigured();
        validateTemporaryPassword(temporaryPassword);
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{16,80}")) throw new BusinessException("请提供有效的幂等请求键");
        roles.selectPlatformAdministratorForUpdate();
        UserImportBatch batch = requireBatch(id, true); requireOwner(batch, operator.getId());
        if (batch.getRequestKey() != null) {
            if (!requestKey.equals(batch.getRequestKey())) throw BusinessException.of(409, "该批次已确认，请刷新查看原任务");
            if (batch.getConfirmationPasswordHash() != null && !passwords.matches(temporaryPassword, batch.getConfirmationPasswordHash()))
                throw BusinessException.of(409, "该批次已使用其他临时密码确认，请查看原任务；如需改密请在用户管理中重新设置");
            return view(batch, items.forBatch(id), operator.getId());
        }
        if (!"PREVIEW".equals(batch.getStatus()) || batch.getErrorCount() != 0) throw BusinessException.of(409, "请修正名单并重新校验");
        if (batch.getCreatedAt().plusHours(24).isBefore(LocalDateTime.now())) throw BusinessException.of(409, "校验结果已过期，请重新上传校验");
        if (batch.getNewCount() == 0) throw new BusinessException("没有需要新增的账号");
        if (batches.selectCount(new LambdaQueryWrapper<UserImportBatch>().eq(UserImportBatch::getCreatedBy, operator.getId()).in(UserImportBatch::getStatus, "QUEUED", "PROCESSING")) >= 2) throw new BusinessException("已有两个导入任务，请等待完成");
        validateNewRows(items.forBatch(id), true);
        batch.setRequestKey(requestKey);
        batch.setConfirmationPasswordHash(passwords.encode(temporaryPassword));
        batch.setTemporaryPasswordCipher(cipher.encrypt(temporaryPassword, batchContext(batch)));
        batch.setStatus("QUEUED"); batch.setMessage("已确认，等待生成账号"); batch.setUpdatedAt(LocalDateTime.now()); write(batches.updateById(batch));
        audit(operator, "CONFIRM_USER_IMPORT", id, "确认导入" + batch.getNewCount() + "个账号");
        return view(batch, List.of(), operator.getId());
    }

    public PageResult<Map<String, Object>> list(SysUser operator, int pageNo, int pageSize) {
        requireAdmin(operator.getId()); int page = Math.max(1, pageNo), size = Math.max(1, Math.min(50, pageSize));
        long total = batches.selectCount(null);
        var list = batches.selectList(new LambdaQueryWrapper<UserImportBatch>().orderByDesc(UserImportBatch::getId).last("LIMIT " + size + " OFFSET " + ((long)(page - 1) * size)));
        return PageResult.of(page, size, total, list.stream().map(b -> view(b, List.of(), operator.getId())).toList());
    }
    public Map<String, Object> detail(Long id, boolean includeRows, SysUser operator) {
        requireAdmin(operator.getId()); return view(requireBatch(id, false), includeRows ? items.forBatch(id) : List.of(), operator.getId());
    }

    /** Hash work runs outside the business transaction; only the leased worker may publish accounts. */
    public void processNext() {
        Long id = batches.nextJob(); if (id == null) return;
        String lease = UUID.randomUUID().toString(); if (batches.claim(id, lease) != 1) return;
        try {
            UserImportBatch batch = requireBatch(id, false); requireAdmin(batch.getCreatedBy()); cipher.requireConfigured();
            List<UserImportItem> rows = items.forBatch(id);
            var people = newPeople(rows); Map<String, Prepared> prepared = new LinkedHashMap<>();
            if (batch.getTemporaryPasswordCipher() == null) throw BusinessException.of(409, "该任务缺少管理员设置的临时密码，请重新上传校验并设置密码");
            String password = cipher.decrypt(batch.getTemporaryPasswordCipher(), batchContext(batch));
            validateTemporaryPassword(password);
            for (String phone : people.keySet()) {
                if (Thread.currentThread().isInterrupted()) return;
                prepared.put(phone, new Prepared(password, passwords.encode(password)));
                if (batches.heartbeat(id, lease, prepared.size()) != 1) return;
            }
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> publish(id, lease, prepared));
        } catch (BusinessException e) {
            batches.fail(id, lease, e.getCode() == 409 ? "CONFLICT" : "FAILED", e.getMessage());
        } catch (Exception e) {
            // SQL/crypto exceptions may contain sensitive values; never publish or log their contents.
            batches.fail(id, lease, "FAILED", "导入未完成，新增账号已整体回滚；请重新校验后重试");
        }
    }

    private void publish(Long id, String lease, Map<String, Prepared> prepared) {
        roles.selectPlatformAdministratorForUpdate();
        UserImportBatch batch = requireBatch(id, true);
        if (!"PROCESSING".equals(batch.getStatus()) || !lease.equals(batch.getLeaseToken()) || batch.getLeaseUntil().isBefore(LocalDateTime.now())) throw BusinessException.of(409, "任务已由其他进程接管");
        SysUser operator = users.selectByIdForUpdate(batch.getCreatedBy()); requireAdmin(batch.getCreatedBy());
        List<UserImportItem> rows = items.forBatch(id); validateNewRows(rows, true);
        LocalDateTime now = LocalDateTime.now();
        for (var person : newPeople(rows).entrySet()) {
            List<UserImportItem> personRows = person.getValue(); UserImportItem first = personRows.get(0); Prepared credential = prepared.get(person.getKey());
            if (credential == null) throw BusinessException.of(409, "导入内容已变化");
            SysUser user = new SysUser(); user.setUsername(first.getPhone()); user.setPhone(first.getPhone()); user.setRealName(first.getRealName());
            user.setPassword(credential.hash()); user.setPasswordLoginEnabled(1); user.setPasswordResetRequired(0); user.setMustChangePassword(1);
            user.setTemporaryPasswordExpiresAt(now.plusDays(30)); user.setCredentialVersion(1); user.setStatus(1); user.setDeleted(0); user.setCreateTime(now); user.setUpdateTime(now); write(users.insert(user));
            var byProject = personRows.stream().collect(Collectors.groupingBy(UserImportItem::getProjectId, TreeMap::new, Collectors.toList()));
            for (var project : byProject.entrySet()) {
                List<SystemRole> projectRoles = project.getValue().stream().map(UserImportItem::getRoleId).distinct().map(roles::selectById).sorted(Comparator.comparing((SystemRole r) -> Integer.valueOf(1).equals(r.getProjectManagerRole())).reversed().thenComparing(SystemRole::getRoleCode)).toList();
                SysUserProject member = new SysUserProject(); member.setUserId(user.getId()); member.setProjectId(project.getKey()); member.setProjectRoleCode(projectRoles.get(0).getRoleCode()); member.setStatus("ACTIVE"); member.setCreateTime(now); member.setUpdateTime(now); write(memberships.insert(member));
                for (SystemRole role : projectRoles) {
                    SysUserProjectRole assignment = new SysUserProjectRole(); assignment.setUserId(user.getId()); assignment.setProjectId(project.getKey()); assignment.setRoleId(role.getId()); assignment.setCreateTime(now); write(assignments.insert(assignment));
                }
            }
            for (UserImportItem row : personRows) { row.setUserId(user.getId()); row.setStatus("CREATED"); row.setMessage("账号及项目角色已创建"); write(items.updateById(row)); }
            storeCredential(first, user, credential.password(), batch.getCreatedBy(), now);
        }
        batch.setTemporaryPasswordCipher(null);
        batch.setStatus("SUCCEEDED"); batch.setMessage("导入完成，请在24小时内下载账号发放表"); batch.setCompletedAt(now); batch.setUpdatedAt(now); write(batches.updateById(batch));
        audit(operator, "COMPLETE_USER_IMPORT", id, "已创建" + batch.getNewCount() + "个账号，跳过" + batch.getSkippedCount() + "人");
    }

    private void validateNewRows(List<UserImportItem> rows, boolean lock) {
        var people = newPeople(rows);
        for (Long projectId : people.values().stream().flatMap(List::stream).map(UserImportItem::getProjectId).distinct().sorted().toList()) {
            ProjectInfo project = lock ? projects.selectByIdForUpdate(projectId) : projects.selectById(projectId);
            if (project == null || Integer.valueOf(1).equals(project.getDeleted()) || "stopped".equalsIgnoreCase(project.getProjectStatus())) throw BusinessException.of(409, "项目状态已变化，请重新校验名单");
        }
        for (Long roleId : people.values().stream().flatMap(List::stream).map(UserImportItem::getRoleId).distinct().sorted().toList()) {
            SystemRole role = lock ? roles.selectByIdForUpdate(roleId) : roles.selectById(roleId);
            if (role == null || !"PROJECT".equals(role.getScopeType()) || !Integer.valueOf(1).equals(role.getEnabled()) || Integer.valueOf(1).equals(role.getDeleted())) throw BusinessException.of(409, "角色状态已变化，请重新校验名单");
        }
        for (String phone : people.keySet()) if (users.selectOccupiedIdentity(phone) != null || pending(phone)) throw BusinessException.of(409, "名单中的账号或注册申请已变化，请重新校验");
    }
    private LinkedHashMap<String, List<UserImportItem>> newPeople(List<UserImportItem> rows) {
        return rows.stream().filter(r -> "NEW".equals(r.getStatus()) || "DUPLICATE".equals(r.getStatus())).collect(Collectors.groupingBy(UserImportItem::getPhone, LinkedHashMap::new, Collectors.toList()));
    }
    private boolean pending(String phone) {
        return registrations.selectCount(new LambdaQueryWrapper<RegistrationApplication>().eq(RegistrationApplication::getStatus, "PENDING").and(q -> q.eq(RegistrationApplication::getPhone, phone).or().eq(RegistrationApplication::getUsername, phone))) > 0;
    }

    @Transactional
    public byte[] credentials(Long id, SysUser operator) {
        requireAdmin(operator.getId()); UserImportBatch batch = requireBatch(id, false); requireOwner(batch, operator.getId());
        if (!"SUCCEEDED".equals(batch.getStatus())) throw BusinessException.of(409, "导入尚未成功");
        List<List<String>> lines = new ArrayList<>();
        for (UserImportItem item : items.forBatch(id)) if (downloadable(item, operator.getId())) {
            SysUser user = users.selectByIdForUpdate(item.getUserId());
            if (validCredentialUser(item, user)) lines.add(credentialLine(item));
        }
        if (lines.isEmpty()) throw BusinessException.of(410, "没有可下载的临时密码：可能已改密或超过24小时，请按需重新设置");
        byte[] bytes = workbook.credentials(lines); audit(operator, "DOWNLOAD_USER_CREDENTIALS", id, "下载账号发放表，共" + lines.size() + "人"); return bytes;
    }
    @Transactional
    public void regenerate(Long userId, String password, SysUser operator) {
        requireAdmin(operator.getId()); cipher.requireConfigured();
        validateTemporaryPassword(password);
        SysUser user = users.selectByIdForUpdate(userId); UserImportItem item = items.forUser(userId);
        if (item == null || user == null || !Integer.valueOf(0).equals(user.getDeleted()) || !Integer.valueOf(1).equals(user.getStatus()) || !Integer.valueOf(1).equals(user.getMustChangePassword())) throw BusinessException.of(409, "仅启用且未完成首次改密的导入账号可以重新设置临时密码");
        if (passwords.matches(password, user.getPassword())) throw new BusinessException("新临时密码不能与原临时密码相同");
        user.setPassword(passwords.encode(password)); user.setPasswordLoginEnabled(1); user.setPasswordResetRequired(0);
        user.setCredentialVersion(Objects.requireNonNullElse(user.getCredentialVersion(), 1) + 1); user.setTemporaryPasswordExpiresAt(LocalDateTime.now().plusDays(30)); user.setUpdateTime(LocalDateTime.now()); write(users.updateById(user));
        items.clearCredentials(userId); storeCredential(item, user, password, operator.getId(), LocalDateTime.now());
        auth.logout(userId); auth.repeatLogoutAfterCommit(userId);
        audit(operator, "REGENERATE_TEMPORARY_PASSWORD", item.getBatchId(), "重新设置用户#" + userId + "的临时密码");
    }
    @Transactional
    public byte[] userCredential(Long userId, SysUser operator) {
        requireAdmin(operator.getId()); SysUser user = users.selectByIdForUpdate(userId); UserImportItem item = items.forUser(userId);
        if (item == null || !downloadable(item, operator.getId()) || !validCredentialUser(item, user)) throw BusinessException.of(403, "仅设置该临时密码的管理员可在24小时内下载，且账号必须尚未改密");
        byte[] result = workbook.credentials(List.of(credentialLine(item)));
        audit(operator, "DOWNLOAD_USER_CREDENTIALS", item.getBatchId(), "下载用户#" + userId + "的账号发放表"); return result;
    }
    private void storeCredential(UserImportItem item, SysUser user, String password, Long owner, LocalDateTime now) {
        item.setUserId(user.getId()); item.setCredentialVersion(user.getCredentialVersion()); item.setCredentialOwnerId(owner);
        item.setPasswordExpiresAt(user.getTemporaryPasswordExpiresAt()); item.setDownloadUntil(now.plusHours(24));
        item.setCredentialCipher(cipher.encrypt(password, context(item))); write(items.updateById(item));
    }
    private String context(UserImportItem item) { return item.getId() + ":" + item.getUserId() + ":" + item.getCredentialVersion(); }
    private boolean downloadable(UserImportItem item, Long operator) {
        return item.getCredentialCipher() != null && Objects.equals(operator, item.getCredentialOwnerId()) && item.getDownloadUntil() != null && item.getDownloadUntil().isAfter(LocalDateTime.now());
    }
    private boolean validCredentialUser(UserImportItem item, SysUser user) {
        return user != null && Integer.valueOf(0).equals(user.getDeleted()) && Integer.valueOf(1).equals(user.getStatus()) && Integer.valueOf(1).equals(user.getMustChangePassword()) && Objects.equals(user.getCredentialVersion(), item.getCredentialVersion()) && user.getTemporaryPasswordExpiresAt() != null && user.getTemporaryPasswordExpiresAt().isAfter(LocalDateTime.now());
    }
    private List<String> credentialLine(UserImportItem item) {
        return List.of(item.getRealName(), item.getPhone(), cipher.decrypt(item.getCredentialCipher(), context(item)), item.getPasswordExpiresAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "使用手机号和临时密码登录，先设置个人密码；随后在小程序“我的”绑定微信。请仅将本行发给本人。");
    }
    public void purgeCredentials() { items.purgeCredentials(); batches.purgeTerminalCredentials(); }
    private UserImportBatch requireBatch(Long id, boolean lock) {
        UserImportBatch batch = lock ? batches.lock(id) : batches.selectById(id);
        if (batch == null) throw BusinessException.notFound("导入批次不存在"); return batch;
    }
    private void requireOwner(UserImportBatch batch, Long id) { if (!Objects.equals(batch.getCreatedBy(), id)) throw BusinessException.forbidden("仅原导入管理员可以确认批次或下载账号发放表"); }
    private Map<String, Object> view(UserImportBatch b, List<UserImportItem> rows, Long operator) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", b.getId()); result.put("createdByName", b.getCreatedByName()); result.put("status", b.getStatus());
        result.put("personCount", b.getPersonCount()); result.put("newCount", b.getNewCount()); result.put("skippedCount", b.getSkippedCount()); result.put("errorCount", b.getErrorCount()); result.put("preparedCount", b.getPreparedCount());
        result.put("message", b.getMessage()); result.put("createdAt", b.getCreatedAt()); result.put("completedAt", b.getCompletedAt()); result.put("owned", Objects.equals(b.getCreatedBy(), operator));
        result.put("rows", rows.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("rowNumber", r.getRowNumber()); row.put("realName", r.getRealName()); row.put("phone", r.getPhone()); row.put("projectLabel", r.getProjectLabel()); row.put("roleLabel", r.getRoleLabel()); row.put("status", r.getStatus()); row.put("message", r.getMessage()); return row;
        }).toList()); return result;
    }
    private void audit(SysUser operator, String action, Long batchId, String description) {
        OperationLog log = new OperationLog(); log.setUserId(operator.getId()); log.setUsername(operator.getUsername()); log.setOperationType(action); log.setBusinessType("USER_IMPORT"); log.setBusinessId(batchId); log.setOperationDesc(description); log.setCreateTime(LocalDateTime.now()); write(logs.insert(log));
    }
    private Long labelId(String label, String kind) {
        var match = Pattern.compile(".*\\[" + kind + ":(\\d+)\\]$").matcher(label);
        try { return match.matches() ? Long.valueOf(match.group(1)) : null; } catch (NumberFormatException e) { return null; }
    }
    private void result(UserImportItem row, String status, String message) { row.setStatus(status); row.setMessage(message); }
    private void write(int count) { if (count != 1) throw BusinessException.of(409, "数据状态已变化，本次操作已回滚"); }
    private String batchContext(UserImportBatch batch) { return "batch:" + batch.getId() + ":" + batch.getCreatedBy() + ":" + batch.getRequestKey(); }
    private void validateTemporaryPassword(String password) {
        passwords.validateStrength(password);
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new BusinessException("临时密码编码后不能超过72字节，请减少字符");
    }
    private record Prepared(String password, String hash) { @Override public String toString() { return "[protected credential]"; } }
}
