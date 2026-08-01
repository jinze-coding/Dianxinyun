package com.example.siteplatform.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.dto.PersonCertificateRequest;
import com.example.siteplatform.person.dto.PersonMovementRequest;
import com.example.siteplatform.person.entity.PersonCertificate;
import com.example.siteplatform.person.entity.PersonEntryExitLog;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.PersonCertificateMapper;
import com.example.siteplatform.person.mapper.PersonEntryExitLogMapper;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.person.vo.PersonCertificateVO;
import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class PersonnelWorkflowService {
    private static final int PERSON_NAME_MAX_LENGTH = 50;
    private static final int GENDER_MAX_LENGTH = 10;
    private static final int IDCARD_MAX_LENGTH = 20;
    private static final int PHONE_MAX_LENGTH = 20;
    private static final int UNIT_MAX_LENGTH = 100;
    private static final int ROLE_MAX_LENGTH = 50;
    private static final int REMARK_MAX_LENGTH = 500;
    private static final int CERTIFICATE_TYPE_MAX_LENGTH = 80;
    private static final int CERTIFICATE_NO_MAX_LENGTH = 100;
    private static final String PERSON_CERTIFICATE_BUSINESS_TYPE = "PERSON_CERTIFICATE";

    private final TemporaryPersonMapper personMapper;
    private final PersonEntryExitLogMapper movementMapper;
    private final PersonCertificateMapper certificateMapper;
    private final FileResourceMapper fileMapper;
    private final ProjectPermissionService permissionService;

    public PersonnelWorkflowService(TemporaryPersonMapper personMapper,
                                    PersonEntryExitLogMapper movementMapper,
                                    PersonCertificateMapper certificateMapper,
                                    FileResourceMapper fileMapper,
                                    ProjectPermissionService permissionService) {
        this.personMapper = personMapper;
        this.movementMapper = movementMapper;
        this.certificateMapper = certificateMapper;
        this.fileMapper = fileMapper;
        this.permissionService = permissionService;
    }

    @Transactional
    public TemporaryPerson create(TemporaryPerson person, SysUser currentUser) {
        if (person == null || person.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        requireManage(currentUser, person.getProjectId());
        LocalDateTime occurredAt = person.getEntryTime() == null ? LocalDateTime.now() : person.getEntryTime();
        TemporaryPerson created = new TemporaryPerson();
        created.setProjectId(person.getProjectId());
        copyEditablePersonFields(person, created, true);
        created.setEntryTime(occurredAt);
        created.setStatus(PersonnelStatus.WAIT_EDUCATION);
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(personMapper.insert(created), "人员新增");
        writeMovement(created, "ENTRY", occurredAt, currentUser, "首次登记进场");
        return created;
    }

    @Transactional
    public TemporaryPerson update(Long id, TemporaryPerson request, SysUser currentUser) {
        TemporaryPerson existing = requirePerson(id);
        requireManage(currentUser, existing.getProjectId());
        if (request == null) {
            throw new BusinessException("人员信息不能为空");
        }
        if (request.getStatus() != null
                && !PersonnelStatus.normalize(request.getStatus()).equals(PersonnelStatus.normalize(existing.getStatus()))) {
            throw new BusinessException("人员状态请通过进退场或安全教育流程更新");
        }
        copyEditablePersonFields(request, existing, false);
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(personMapper.updateById(existing), "人员更新");
        return existing;
    }

    @Transactional
    public void delete(Long id, SysUser currentUser) {
        TemporaryPerson existing = requirePerson(id);
        requireManage(currentUser, existing.getProjectId());
        requireSingleWrite(personMapper.deleteById(id), "人员删除");
    }

    @Transactional
    public PersonEntryExitLog move(Long id, String actionType, PersonMovementRequest request, SysUser currentUser) {
        TemporaryPerson person = requirePerson(id);
        requireManage(currentUser, person.getProjectId());
        String action = actionType == null ? "" : actionType.trim().toUpperCase();
        LocalDateTime occurredAt = request != null && request.getOccurredAt() != null
                ? request.getOccurredAt() : LocalDateTime.now();
        if ("ENTRY".equals(action)) {
            if (!PersonnelStatus.LEFT.equals(PersonnelStatus.normalize(person.getStatus()))) {
                throw new BusinessException("当前人员已在场");
            }
            person.setStatus(PersonnelStatus.WAIT_EDUCATION);
            person.setEntryTime(occurredAt);
        } else if ("EXIT".equals(action)) {
            if (PersonnelStatus.LEFT.equals(PersonnelStatus.normalize(person.getStatus()))) {
                throw new BusinessException("当前人员已离场");
            }
            person.setStatus(PersonnelStatus.LEFT);
        } else {
            throw new BusinessException("进退场动作不正确");
        }
        person.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(personMapper.updateById(person), "人员进退场状态更新");
        return writeMovement(person, action, occurredAt, currentUser, request == null ? null : request.getRemark());
    }

    public List<PersonEntryExitLog> listMovements(Long personId, SysUser currentUser) {
        TemporaryPerson person = requirePerson(personId);
        permissionService.checkProjectPermission(currentUser.getId(), person.getProjectId());
        return movementMapper.selectList(new LambdaQueryWrapper<PersonEntryExitLog>()
                .eq(PersonEntryExitLog::getPersonId, personId)
                .orderByDesc(PersonEntryExitLog::getOccurredAt));
    }

    public List<PersonCertificateVO> listCertificates(Long projectId, Long personId, SysUser currentUser) {
        permissionService.checkProjectPermission(currentUser.getId(), projectId);
        LambdaQueryWrapper<PersonCertificate> wrapper = new LambdaQueryWrapper<PersonCertificate>()
                .eq(PersonCertificate::getProjectId, projectId)
                .orderByAsc(PersonCertificate::getExpiryDate)
                .orderByDesc(PersonCertificate::getCreateTime);
        if (personId != null) {
            TemporaryPerson person = requirePerson(personId);
            if (!Objects.equals(person.getProjectId(), projectId)) {
                throw new BusinessException("人员不属于当前项目");
            }
            wrapper.eq(PersonCertificate::getPersonId, personId);
        }
        return certificateMapper.selectList(wrapper).stream().map(this::toCertificateVO).toList();
    }

    @Transactional
    public PersonCertificateVO createCertificate(Long personId, PersonCertificateRequest request, SysUser currentUser) {
        TemporaryPerson person = requirePerson(personId);
        requireManage(currentUser, person.getProjectId());
        validateCertificate(request);
        PersonCertificate certificate = new PersonCertificate();
        BeanUtils.copyProperties(request, certificate);
        certificate.setProjectId(person.getProjectId());
        certificate.setPersonId(personId);
        certificate.setCreateTime(LocalDateTime.now());
        certificate.setUpdateTime(LocalDateTime.now());
        FileResource file = validateFile(request.getFileId(), person.getProjectId(), currentUser.getId(), null);
        requireSingleWrite(certificateMapper.insert(certificate), "人员证件新增");
        bindFile(file, person.getProjectId(), certificate.getId(), currentUser.getId());
        return toCertificateVO(certificate);
    }

    @Transactional
    public PersonCertificateVO updateCertificate(Long certificateId, PersonCertificateRequest request, SysUser currentUser) {
        PersonCertificate existing = requireCertificate(certificateId);
        requireManage(currentUser, existing.getProjectId());
        validateCertificate(request);
        FileResource file = validateFile(request.getFileId(), existing.getProjectId(), currentUser.getId(), existing.getId());
        BeanUtils.copyProperties(request, existing);
        existing.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(certificateMapper.updateById(existing), "人员证件更新");
        bindFile(file, existing.getProjectId(), existing.getId(), currentUser.getId());
        return toCertificateVO(existing);
    }

    @Transactional
    public void deleteCertificate(Long certificateId, SysUser currentUser) {
        PersonCertificate existing = requireCertificate(certificateId);
        requireManage(currentUser, existing.getProjectId());
        requireSingleWrite(certificateMapper.deleteById(certificateId), "人员证件删除");
    }

    public TemporaryPerson requirePerson(Long id) {
        TemporaryPerson person = personMapper.selectById(id);
        if (person == null) {
            throw BusinessException.notFound("人员不存在");
        }
        return person;
    }

    private PersonEntryExitLog writeMovement(TemporaryPerson person, String action, LocalDateTime occurredAt,
                                             SysUser operator, String remark) {
        PersonEntryExitLog log = new PersonEntryExitLog();
        log.setProjectId(person.getProjectId());
        log.setPersonId(person.getId());
        log.setActionType(action);
        log.setOccurredAt(occurredAt);
        log.setOperatorId(operator.getId());
        log.setOperatorName(optionalText(displayName(operator), PERSON_NAME_MAX_LENGTH, "操作人姓名"));
        log.setRemark(optionalText(remark, REMARK_MAX_LENGTH, "进退场备注"));
        log.setCreateTime(LocalDateTime.now());
        requireSingleWrite(movementMapper.insert(log), "进退场流水新增");
        return log;
    }

    private void validateCertificate(PersonCertificateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCertificateType())) {
            throw new BusinessException("证件类型不能为空");
        }
        if (!StringUtils.hasText(request.getCertificateNo())) {
            throw new BusinessException("证件编号不能为空");
        }
        request.setCertificateType(requireText(request.getCertificateType(), CERTIFICATE_TYPE_MAX_LENGTH, "证件类型"));
        request.setCertificateNo(requireText(request.getCertificateNo(), CERTIFICATE_NO_MAX_LENGTH, "证件编号"));
        request.setRemark(optionalText(request.getRemark(), REMARK_MAX_LENGTH, "证件备注"));
        if (request.getFileId() != null && request.getFileId() <= 0) {
            throw new BusinessException("证件附件ID不正确");
        }
        if (request.getIssueDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssueDate())) {
            throw new BusinessException("证件到期日期不能早于发证日期");
        }
    }

    private FileResource validateFile(Long fileId, Long projectId, Long operatorId, Long certificateId) {
        if (fileId == null) return null;
        FileResource file = fileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getProjectId(), projectId)) {
            throw new BusinessException("证件附件不属于当前项目");
        }
        if (!PERSON_CERTIFICATE_BUSINESS_TYPE.equalsIgnoreCase(file.getBusinessType())) {
            throw new BusinessException("证件附件类型不正确");
        }
        if (file.getBusinessId() == null) {
            if (!Objects.equals(file.getUploaderId(), operatorId)) {
                throw BusinessException.forbidden("只能关联本人刚上传的证件附件");
            }
        } else if (!Objects.equals(file.getBusinessId(), certificateId)) {
            throw new BusinessException("证件附件已关联其他业务记录");
        }
        return file;
    }

    private void bindFile(FileResource file, Long projectId, Long certificateId, Long operatorId) {
        if (file == null) return;
        LambdaUpdateWrapper<FileResource> wrapper = new LambdaUpdateWrapper<FileResource>()
                .eq(FileResource::getId, file.getId())
                .eq(FileResource::getProjectId, projectId)
                .eq(FileResource::getBusinessType, file.getBusinessType())
                .eq(FileResource::getDeleted, 0);
        if (file.getBusinessId() == null) {
            wrapper.isNull(FileResource::getBusinessId)
                    .eq(FileResource::getUploaderId, operatorId);
        } else {
            wrapper.eq(FileResource::getBusinessId, certificateId);
        }
        wrapper.set(FileResource::getBusinessType, PERSON_CERTIFICATE_BUSINESS_TYPE)
                .set(FileResource::getBusinessId, certificateId)
                .set(FileResource::getUpdateTime, LocalDateTime.now());
        requireSingleWrite(fileMapper.update(null, wrapper), "证件附件绑定");
    }

    private PersonCertificate requireCertificate(Long id) {
        PersonCertificate certificate = certificateMapper.selectById(id);
        if (certificate == null) {
            throw BusinessException.notFound("人员证件不存在");
        }
        return certificate;
    }

    private PersonCertificateVO toCertificateVO(PersonCertificate certificate) {
        PersonCertificateVO vo = new PersonCertificateVO();
        BeanUtils.copyProperties(certificate, vo);
        if (certificate.getFileId() != null) {
            FileResource file = fileMapper.selectById(certificate.getFileId());
            vo.setFileName(file == null ? null : file.getFileName());
        }
        LocalDate expiry = certificate.getExpiryDate();
        if (expiry == null) {
            vo.setWarningLevel("NONE");
            vo.setWarningLabel("未设置到期日期");
        } else {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), expiry);
            if (days < 0) {
                vo.setWarningLevel("EXPIRED");
                vo.setWarningLabel("已过期");
            } else if (days <= 30) {
                vo.setWarningLevel("WARNING");
                vo.setWarningLabel(days + "天后到期");
            } else {
                vo.setWarningLevel("NORMAL");
                vo.setWarningLabel("有效");
            }
        }
        return vo;
    }

    private void requireManage(SysUser currentUser, Long projectId) {
        if (!permissionService.canManagePersonnel(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无人员管理权限");
        }
    }

    private String displayName(SysUser user) {
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private void copyEditablePersonFields(TemporaryPerson source, TemporaryPerson target, boolean requireName) {
        if (requireName || source.getName() != null) {
            target.setName(requireText(source.getName(), PERSON_NAME_MAX_LENGTH, "人员姓名"));
        }
        if (source.getGender() != null) {
            target.setGender(optionalText(source.getGender(), GENDER_MAX_LENGTH, "性别"));
        }
        if (source.getIdcard() != null) {
            target.setIdcard(optionalText(source.getIdcard(), IDCARD_MAX_LENGTH, "身份证号"));
        }
        if (source.getPhone() != null) {
            target.setPhone(optionalText(source.getPhone(), PHONE_MAX_LENGTH, "手机号"));
        }
        if (source.getUnit() != null) {
            target.setUnit(optionalText(source.getUnit(), UNIT_MAX_LENGTH, "所属单位"));
        }
        if (source.getRole() != null) {
            target.setRole(optionalText(source.getRole(), ROLE_MAX_LENGTH, "工种"));
        }
        if (source.getEntryTime() != null) {
            target.setEntryTime(source.getEntryTime());
        }
        if (source.getRemark() != null) {
            target.setRemark(optionalText(source.getRemark(), REMARK_MAX_LENGTH, "人员备注"));
        }
    }

    private String requireText(String value, int maxLength, String fieldName) {
        String normalized = optionalText(value, maxLength, fieldName);
        if (normalized == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String fieldName) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }
}
