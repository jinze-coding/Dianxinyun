package com.example.siteplatform.seal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.entity.SysUserWechatBinding;
import com.example.siteplatform.auth.mapper.SysUserWechatBindingMapper;
import com.example.siteplatform.auth.service.WechatPlatformClient;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.mapper.ProjectInfoMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.seal.dto.SealDefinitionRequest;
import com.example.siteplatform.seal.entity.SealDefinition;
import com.example.siteplatform.seal.mapper.SealDefinitionMapper;
import com.example.siteplatform.seal.vo.SealDefinitionVO;
import com.example.siteplatform.seal.vo.SealEntryVO;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfig;
import com.example.siteplatform.workflow.entity.WorkflowApprovalConfigUser;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigMapper;
import com.example.siteplatform.workflow.mapper.WorkflowApprovalConfigUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SealDefinitionService {
    public static final String BUSINESS_CODE = "SEAL_APPLICATION";
    public static final String DEFAULT_COMPANY_NAME = "上海建工智慧营造有限公司";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> SEAL_TYPES = Set.of(
            "PROJECT_SEAL", "COMPANY_SEAL", "CONTRACT_SEAL", "FINANCE_SEAL", "OTHER");

    private final SealDefinitionMapper sealMapper;
    private final ProjectInfoMapper projectMapper;
    private final ProjectPermissionService permissionService;
    private final SealSceneCryptoService cryptoService;
    private final WechatPlatformClient wechatPlatformClient;
    private final WorkflowApprovalConfigMapper configMapper;
    private final WorkflowApprovalConfigUserMapper configUserMapper;
    private final SysUserWechatBindingMapper wechatBindingMapper;
    private final String miniProgramPage;
    private final String miniProgramEnvVersion;

    public SealDefinitionService(SealDefinitionMapper sealMapper,
                                 ProjectInfoMapper projectMapper,
                                 ProjectPermissionService permissionService,
                                 SealSceneCryptoService cryptoService,
                                 WechatPlatformClient wechatPlatformClient,
                                 WorkflowApprovalConfigMapper configMapper,
                                 WorkflowApprovalConfigUserMapper configUserMapper,
                                 SysUserWechatBindingMapper wechatBindingMapper,
                                 @Value("${seal.mini-program.page:pages/seal/entry}") String miniProgramPage,
                                 @Value("${wechat.mini-program.env-version:release}") String miniProgramEnvVersion) {
        this.sealMapper = sealMapper;
        this.projectMapper = projectMapper;
        this.permissionService = permissionService;
        this.cryptoService = cryptoService;
        this.wechatPlatformClient = wechatPlatformClient;
        this.configMapper = configMapper;
        this.configUserMapper = configUserMapper;
        this.wechatBindingMapper = wechatBindingMapper;
        this.miniProgramPage = miniProgramPage;
        this.miniProgramEnvVersion = miniProgramEnvVersion;
    }

    public List<SealDefinitionVO> systemList(Long projectId, SysUser user) {
        requireView(user, projectId);
        return sealMapper.selectList(new LambdaQueryWrapper<SealDefinition>()
                        .eq(projectId != null, SealDefinition::getProjectId, projectId)
                        .orderByAsc(SealDefinition::getSortOrder).orderByAsc(SealDefinition::getId))
                .stream().map(this::toVO).toList();
    }

    public List<SealDefinitionVO> applicationOptions(Long projectId, SysUser user) {
        requireActiveMember(user, projectId);
        return sealMapper.selectList(new LambdaQueryWrapper<SealDefinition>()
                        .eq(SealDefinition::getProjectId, projectId)
                        .eq(SealDefinition::getStatus, "ACTIVE")
                        .orderByAsc(SealDefinition::getSortOrder).orderByAsc(SealDefinition::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public SealDefinitionVO create(SealDefinitionRequest request, SysUser user) {
        if (request == null || request.getProjectId() == null) throw new BusinessException("项目不能为空");
        requireManage(user, request.getProjectId());
        requireProject(request.getProjectId());
        String sealName = required(request.getSealName(), 100, "印章名称");
        String sealCode = normalizeCode(request.getSealCode());
        ensureUnique(request.getProjectId(), sealCode, sealName, null);
        String scene = newScene();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        SealDefinition seal = new SealDefinition();
        seal.setProjectId(request.getProjectId());
        seal.setSealCode(sealCode);
        seal.setSealName(sealName);
        seal.setSealType(normalizeSealType(request.getSealType()));
        seal.setCompanyName(StringUtils.hasText(request.getCompanyName())
                ? required(request.getCompanyName(), 200, "公司名称") : DEFAULT_COMPANY_NAME);
        seal.setStatus(Boolean.FALSE.equals(request.getEnabled()) ? "DISABLED" : "ACTIVE");
        seal.setSceneTokenHash(cryptoService.digest(scene));
        seal.setSceneTokenEncrypted(cryptoService.encrypt(scene));
        seal.setQrStatus("ENABLED");
        seal.setQrVersion(1);
        seal.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        seal.setCreatedBy(user.getId());
        seal.setUpdatedBy(user.getId());
        seal.setVersion(0);
        seal.setDeleted(0);
        seal.setCreateTime(now);
        seal.setUpdateTime(now);
        requireSingleWrite(sealMapper.insert(seal), "印章新增");
        return toVO(seal);
    }

    @Transactional
    public SealDefinitionVO update(Long id, SealDefinitionRequest request, SysUser user) {
        SealDefinition current = requireSeal(id);
        requireManage(user, current.getProjectId());
        if (request != null && request.getProjectId() != null
                && !request.getProjectId().equals(current.getProjectId())) {
            throw new BusinessException("印章所属项目不可修改");
        }
        String code = request != null && StringUtils.hasText(request.getSealCode())
                ? normalizeCode(request.getSealCode()) : current.getSealCode();
        String name = request != null && StringUtils.hasText(request.getSealName())
                ? required(request.getSealName(), 100, "印章名称") : current.getSealName();
        ensureUnique(current.getProjectId(), code, name, id);
        String type = request == null || request.getSealType() == null
                ? current.getSealType() : normalizeSealType(request.getSealType());
        String company = request == null || request.getCompanyName() == null
                ? current.getCompanyName() : required(request.getCompanyName(), 200, "公司名称");
        String status = request != null && request.getEnabled() != null
                ? (request.getEnabled() ? "ACTIVE" : "DISABLED") : current.getStatus();
        int sortOrder = request == null || request.getSortOrder() == null
                ? current.getSortOrder() : request.getSortOrder();
        Integer expectedVersion = request != null && request.getVersion() != null
                ? request.getVersion() : current.getVersion();
        requireSingleWrite(sealMapper.updateDefinition(id, expectedVersion, code, name, type, company, status,
                sortOrder, user.getId(), LocalDateTime.now(BUSINESS_ZONE)), "印章更新");
        return toVO(requireSeal(id));
    }

    public SealEntryVO entryCode(Long projectId, Long sealId, SysUser user) {
        SealDefinition seal = requireSeal(sealId);
        if (projectId != null && !projectId.equals(seal.getProjectId())) throw BusinessException.notFound("印章不存在");
        requireView(user, seal.getProjectId());
        return toEntry(seal, true);
    }

    public SealEntryVO resolve(String scene, SysUser user) {
        String normalized = normalizeScene(scene);
        requireActiveWechatBinding(user);
        SealDefinition seal = sealMapper.selectOne(new LambdaQueryWrapper<SealDefinition>()
                .eq(SealDefinition::getSceneTokenHash, cryptoService.digest(normalized)).last("LIMIT 1"));
        if (seal == null) throw BusinessException.notFound("用印二维码不存在或已轮换");
        requireActiveMember(user, seal.getProjectId());
        if (!"ACTIVE".equals(seal.getStatus()) || !"ENABLED".equals(seal.getQrStatus())) {
            throw BusinessException.of(410, "当前用印扫码入口已停用");
        }
        return toEntry(seal, false);
    }

    @Transactional
    public SealEntryVO rotate(Long sealId, SysUser user) {
        SealDefinition seal = requireSeal(sealId);
        requireManage(user, seal.getProjectId());
        String scene = newScene();
        requireSingleWrite(sealMapper.rotateScene(sealId, seal.getVersion(), cryptoService.digest(scene),
                cryptoService.encrypt(scene), user.getId(), LocalDateTime.now(BUSINESS_ZONE)), "用印二维码轮换");
        return toEntry(requireSeal(sealId), true);
    }

    @Transactional
    public SealEntryVO updateQrStatus(Long sealId, boolean enabled, SysUser user) {
        SealDefinition seal = requireSeal(sealId);
        requireManage(user, seal.getProjectId());
        requireSingleWrite(sealMapper.updateQrStatus(sealId, seal.getVersion(), enabled ? "ENABLED" : "DISABLED",
                user.getId(), LocalDateTime.now(BUSINESS_ZONE)), "用印二维码状态更新");
        return toEntry(requireSeal(sealId), true);
    }

    public String miniCode(Long sealId, SysUser user) {
        SealDefinition seal = requireSeal(sealId);
        requireView(user, seal.getProjectId());
        if (!"ACTIVE".equals(seal.getStatus()) || !"ENABLED".equals(seal.getQrStatus())) {
            throw new BusinessException("请先启用印章和扫码入口");
        }
        String scene = cryptoService.decrypt(seal.getSceneTokenEncrypted());
        return wechatPlatformClient.generateUnlimitedCode(scene, miniProgramPage, miniProgramEnvVersion);
    }

    public SealDefinition requireActiveSeal(Long sealId, Long projectId) {
        SealDefinition seal = requireSeal(sealId);
        if (!seal.getProjectId().equals(projectId) || !"ACTIVE".equals(seal.getStatus())) {
            throw new BusinessException("印章不可用");
        }
        return seal;
    }

    public SealDefinition requireSceneSeal(String scene) {
        String normalized = normalizeScene(scene);
        SealDefinition seal = sealMapper.selectOne(new LambdaQueryWrapper<SealDefinition>()
                .eq(SealDefinition::getSceneTokenHash, cryptoService.digest(normalized)).last("LIMIT 1"));
        if (seal == null || !"ACTIVE".equals(seal.getStatus()) || !"ENABLED".equals(seal.getQrStatus())) {
            throw new BusinessException("用印二维码不存在、已轮换或已停用");
        }
        return seal;
    }

    public void requireActiveWechatBinding(SysUser user) {
        if (user == null) throw BusinessException.unauthorized("请先登录");
        long count = wechatBindingMapper.selectCount(new LambdaQueryWrapper<SysUserWechatBinding>()
                .eq(SysUserWechatBinding::getUserId, user.getId())
                .eq(SysUserWechatBinding::getStatus, "ACTIVE")
                .eq(SysUserWechatBinding::getDeleted, 0));
        if (count == 0) throw BusinessException.forbidden("扫码发起用印申请前请先绑定微信");
    }

    private SealEntryVO toEntry(SealDefinition seal, boolean includeScene) {
        ProjectInfo project = requireProject(seal.getProjectId());
        WorkflowApprovalConfig config = configMapper.selectOne(new LambdaQueryWrapper<WorkflowApprovalConfig>()
                .eq(WorkflowApprovalConfig::getBusinessCode, BUSINESS_CODE)
                .eq(WorkflowApprovalConfig::getProjectId, seal.getProjectId())
                .eq(WorkflowApprovalConfig::getSealId, seal.getId()).last("LIMIT 1"));
        boolean configured = config != null && Integer.valueOf(1).equals(config.getEnabled())
                && configUserMapper.selectCount(new LambdaQueryWrapper<WorkflowApprovalConfigUser>()
                .eq(WorkflowApprovalConfigUser::getConfigId, config.getId())
                .eq(WorkflowApprovalConfigUser::getAssignmentType, "APPROVER")) > 0;
        SealEntryVO vo = new SealEntryVO();
        if (includeScene) vo.setScene(cryptoService.decrypt(seal.getSceneTokenEncrypted()));
        vo.setProjectId(project.getId());
        vo.setProjectName(project.getProjectName());
        vo.setProjectShortName(project.getShortName());
        vo.setDepartmentName(project.getProjectName());
        vo.setCompanyName(seal.getCompanyName());
        vo.setSealId(seal.getId());
        vo.setSealName(seal.getSealName());
        vo.setActive("ACTIVE".equals(seal.getStatus()) && "ENABLED".equals(seal.getQrStatus()));
        vo.setConfigured(configured);
        vo.setQrStatus(seal.getQrStatus());
        vo.setQrVersion(seal.getQrVersion());
        vo.setMessage(configured ? "可发起用印申请" : "该印章尚未配置审批人");
        return vo;
    }

    private SealDefinitionVO toVO(SealDefinition seal) {
        ProjectInfo project = projectMapper.selectById(seal.getProjectId());
        SealDefinitionVO vo = new SealDefinitionVO();
        vo.setId(seal.getId());
        vo.setProjectId(seal.getProjectId());
        vo.setProjectName(project == null ? null : project.getProjectName());
        vo.setSealCode(seal.getSealCode());
        vo.setSealName(seal.getSealName());
        vo.setSealType(seal.getSealType());
        vo.setCompanyName(seal.getCompanyName());
        vo.setStatus(seal.getStatus());
        vo.setEnabled("ACTIVE".equals(seal.getStatus()));
        vo.setQrStatus(seal.getQrStatus());
        vo.setQrEnabled("ENABLED".equals(seal.getQrStatus()));
        vo.setQrVersion(seal.getQrVersion());
        vo.setSortOrder(seal.getSortOrder());
        vo.setVersion(seal.getVersion());
        vo.setCreateTime(seal.getCreateTime());
        vo.setUpdateTime(seal.getUpdateTime());
        return vo;
    }

    private void requireManage(SysUser user, Long projectId) {
        if (user == null || projectId == null) throw BusinessException.forbidden("无审批配置管理权限");
        permissionService.checkProjectPermission(user.getId(), projectId);
        permissionService.requireSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_MANAGE);
    }

    private void requireView(SysUser user, Long projectId) {
        if (user == null || projectId == null) throw BusinessException.forbidden("无审批配置查看权限");
        permissionService.checkProjectPermission(user.getId(), projectId);
        if (!permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_VIEW)
                && !permissionService.hasSystemPermission(user.getId(), projectId, SystemPermissionCodes.APPROVAL_MANAGE)) {
            throw BusinessException.forbidden("无审批配置查看权限");
        }
    }

    private void requireActiveMember(SysUser user, Long projectId) {
        if (user == null) throw BusinessException.unauthorized("请先登录");
        permissionService.checkProjectPermission(user.getId(), projectId);
        if (!"ACTIVE".equals(permissionService.getProjectAccessStatus(user.getId(), projectId))) {
            throw BusinessException.forbidden("仅当前项目有效成员可使用用印功能");
        }
    }

    private SealDefinition requireSeal(Long id) {
        SealDefinition seal = id == null ? null : sealMapper.selectById(id);
        if (seal == null) throw BusinessException.notFound("印章不存在");
        return seal;
    }

    private ProjectInfo requireProject(Long id) {
        ProjectInfo project = projectMapper.selectById(id);
        if (project == null) throw BusinessException.notFound("项目不存在");
        return project;
    }

    private void ensureUnique(Long projectId, String sealCode, String sealName, Long excludeId) {
        long codeCount = sealMapper.selectCount(new LambdaQueryWrapper<SealDefinition>()
                .eq(SealDefinition::getSealCode, sealCode).ne(excludeId != null, SealDefinition::getId, excludeId));
        if (codeCount > 0) throw new BusinessException("印章编码已存在");
        long nameCount = sealMapper.selectCount(new LambdaQueryWrapper<SealDefinition>()
                .eq(SealDefinition::getProjectId, projectId).eq(SealDefinition::getSealName, sealName)
                .ne(excludeId != null, SealDefinition::getId, excludeId));
        if (nameCount > 0) throw new BusinessException("当前项目已存在同名印章");
    }

    private String generateSealCode(Long projectId) {
        for (int i = 0; i < 5; i++) {
            String code = "SEAL-" + projectId + "-" + randomUrlToken(6).toUpperCase(Locale.ROOT);
            if (sealMapper.selectCount(new LambdaQueryWrapper<SealDefinition>()
                    .eq(SealDefinition::getSealCode, code)) == 0) return code;
        }
        throw new BusinessException("印章编码生成失败，请重试");
    }

    private String newScene() {
        return "S:" + randomUrlToken(21);
    }

    private String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String normalizeScene(String value) {
        String scene = required(value, 64, "scene");
        if (!scene.startsWith("S:") || !scene.matches("^S:[A-Za-z0-9_-]{20,40}$")) {
            throw new BusinessException("用印二维码格式不正确");
        }
        return scene;
    }

    private String normalizeCode(String value) {
        String code = required(value, 40, "印章编码").toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9][A-Z0-9_-]{1,39}$")) throw new BusinessException("印章编码格式不正确");
        return code;
    }

    private String required(String value, int max, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) throw new BusinessException(label + "不能为空");
        if (text.length() > max) throw new BusinessException(label + "不能超过" + max + "个字符");
        return text;
    }

    private String optional(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        if (text.length() > max) throw new BusinessException("字段内容过长");
        return text;
    }

    private String normalizeSealType(String value) {
        String type = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "PROJECT_SEAL";
        if (!SEAL_TYPES.contains(type)) throw new BusinessException("印章类型不正确");
        return type;
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) throw BusinessException.of(409, operation + "未生效，请刷新后重试");
    }
}
