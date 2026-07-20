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
        if (!StringUtils.hasText(person.getName())) {
            throw new BusinessException("人员姓名不能为空");
        }
        LocalDateTime occurredAt = person.getEntryTime() == null ? LocalDateTime.now() : person.getEntryTime();
        person.setName(person.getName().trim());
        person.setEntryTime(occurredAt);
        person.setStatus(PersonnelStatus.WAIT_EDUCATION);
        person.setCreateTime(LocalDateTime.now());
        person.setUpdateTime(LocalDateTime.now());
        personMapper.insert(person);
        writeMovement(person, "ENTRY", occurredAt, currentUser, "首次登记进场");
        return person;
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
        request.setId(id);
        request.setProjectId(existing.getProjectId());
        request.setStatus(request.getStatus() == null ? existing.getStatus() : PersonnelStatus.normalize(request.getStatus()));
        request.setEntryTime(request.getEntryTime() == null ? existing.getEntryTime() : request.getEntryTime());
        request.setUpdateTime(LocalDateTime.now());
        personMapper.updateById(request);
        return personMapper.selectById(id);
    }

    @Transactional
    public void delete(Long id, SysUser currentUser) {
        TemporaryPerson existing = requirePerson(id);
        requireManage(currentUser, existing.getProjectId());
        personMapper.deleteById(id);
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
        personMapper.updateById(person);
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
        validateFile(request.getFileId(), person.getProjectId());
        certificateMapper.insert(certificate);
        bindFile(request.getFileId(), person.getProjectId(), certificate.getId());
        return toCertificateVO(certificateMapper.selectById(certificate.getId()));
    }

    @Transactional
    public PersonCertificateVO updateCertificate(Long certificateId, PersonCertificateRequest request, SysUser currentUser) {
        PersonCertificate existing = requireCertificate(certificateId);
        requireManage(currentUser, existing.getProjectId());
        validateCertificate(request);
        validateFile(request.getFileId(), existing.getProjectId());
        BeanUtils.copyProperties(request, existing);
        existing.setUpdateTime(LocalDateTime.now());
        certificateMapper.updateById(existing);
        bindFile(request.getFileId(), existing.getProjectId(), existing.getId());
        return toCertificateVO(existing);
    }

    @Transactional
    public void deleteCertificate(Long certificateId, SysUser currentUser) {
        PersonCertificate existing = requireCertificate(certificateId);
        requireManage(currentUser, existing.getProjectId());
        certificateMapper.deleteById(certificateId);
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
        log.setOperatorName(displayName(operator));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        movementMapper.insert(log);
        return log;
    }

    private void validateCertificate(PersonCertificateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCertificateType())) {
            throw new BusinessException("证件类型不能为空");
        }
        if (!StringUtils.hasText(request.getCertificateNo())) {
            throw new BusinessException("证件编号不能为空");
        }
        if (request.getIssueDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssueDate())) {
            throw new BusinessException("证件到期日期不能早于发证日期");
        }
    }

    private void validateFile(Long fileId, Long projectId) {
        if (fileId == null) return;
        FileResource file = fileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getProjectId(), projectId)) {
            throw new BusinessException("证件附件不属于当前项目");
        }
    }

    private void bindFile(Long fileId, Long projectId, Long certificateId) {
        if (fileId == null) return;
        FileResource file = fileMapper.selectById(fileId);
        file.setProjectId(projectId);
        file.setBusinessType("PERSON_CERTIFICATE");
        file.setBusinessId(certificateId);
        file.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(file);
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
}
