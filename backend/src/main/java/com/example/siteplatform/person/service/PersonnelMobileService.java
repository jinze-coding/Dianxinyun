package com.example.siteplatform.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.dto.PersonnelMobileSummaryVO;
import com.example.siteplatform.person.entity.PersonCertificate;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.PersonCertificateMapper;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.safety.entity.SafetyEducationBatch;
import com.example.siteplatform.safety.entity.SafetyEducationPerson;
import com.example.siteplatform.safety.mapper.SafetyEducationBatchMapper;
import com.example.siteplatform.safety.mapper.SafetyEducationPersonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PersonnelMobileService {

    @Autowired
    private TemporaryPersonMapper personMapper;

    @Autowired
    private SafetyEducationBatchMapper educationBatchMapper;

    @Autowired
    private SafetyEducationPersonMapper educationPersonMapper;

    @Autowired
    private PersonCertificateMapper certificateMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    public PersonnelMobileSummaryVO getSummary(Long projectId, SysUser currentUser) {
        projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
        List<TemporaryPerson> people = personMapper.selectList(new LambdaQueryWrapper<TemporaryPerson>()
                .eq(TemporaryPerson::getProjectId, projectId)
                .orderByAsc(TemporaryPerson::getStatus)
                .orderByDesc(TemporaryPerson::getEntryTime)
                .orderByDesc(TemporaryPerson::getCreateTime));
        List<SafetyEducationBatch> batches = educationBatchMapper.selectList(new LambdaQueryWrapper<SafetyEducationBatch>()
                .eq(SafetyEducationBatch::getProjectId, projectId)
                .orderByDesc(SafetyEducationBatch::getTrainingTime)
                .orderByDesc(SafetyEducationBatch::getCreateTime)
                .last("LIMIT 20"));
        List<PersonCertificate> certificates = certificateMapper.selectList(new LambdaQueryWrapper<PersonCertificate>()
                .eq(PersonCertificate::getProjectId, projectId));
        Map<Long, List<PersonCertificate>> certificatesByPerson = certificates.stream()
                .collect(Collectors.groupingBy(PersonCertificate::getPersonId));
        boolean canManage = projectPermissionService.canManagePersonnel(currentUser.getId(), projectId);

        PersonnelMobileSummaryVO summary = new PersonnelMobileSummaryVO();
        summary.setOnsiteCount((int) people.stream().filter(person -> !PersonnelStatus.LEFT.equals(PersonnelStatus.normalize(person.getStatus()))).count());
        summary.setTodayEntryCount((int) people.stream().filter(person -> isToday(person.getEntryTime())).count());
        summary.setPendingEducationCount((int) people.stream().filter(person -> PersonnelStatus.WAIT_EDUCATION.equals(PersonnelStatus.normalize(person.getStatus()))).count());
        summary.setCertificateWarningCount((int) certificates.stream().filter(this::isCertificateWarning).count());
        summary.setCanManage(canManage);
        summary.setPeople(people.stream().map(person -> toPersonItem(person, canManage,
                certificatesByPerson.getOrDefault(person.getId(), List.of()))).toList());
        summary.setTrainings(batches.stream().map(this::toTrainingItem).toList());
        return summary;
    }

    private PersonnelMobileSummaryVO.PersonItem toPersonItem(TemporaryPerson person, boolean includeSensitive,
                                                              List<PersonCertificate> certificates) {
        PersonnelMobileSummaryVO.PersonItem item = new PersonnelMobileSummaryVO.PersonItem();
        item.setId(person.getId());
        item.setName(person.getName());
        item.setGender(person.getGender());
        item.setMaskedIdcard(maskIdcard(person.getIdcard()));
        item.setMaskedPhone(maskPhone(person.getPhone()));
        if (includeSensitive) {
            item.setIdcard(person.getIdcard());
            item.setPhone(person.getPhone());
        }
        item.setTeam(person.getUnit());
        item.setTrade(person.getRole());
        item.setEntryTime(person.getEntryTime());
        item.setStatus(PersonnelStatus.normalize(person.getStatus()));
        item.setStatusLabel(PersonnelStatus.label(person.getStatus()));
        item.setRemark(person.getRemark());
        item.setCertificateCount(certificates.size());
        item.setCertificateWarningCount((int) certificates.stream().filter(this::isCertificateWarning).count());
        return item;
    }

    private PersonnelMobileSummaryVO.TrainingItem toTrainingItem(SafetyEducationBatch batch) {
        PersonnelMobileSummaryVO.TrainingItem item = new PersonnelMobileSummaryVO.TrainingItem();
        item.setId(batch.getId());
        item.setTitle(batch.getBatchName());
        item.setType(batch.getEduType());
        item.setTrainingTime(batch.getTrainingTime());
        item.setPlace(batch.getTrainingPlace());
        item.setTrainer(batch.getTrainer());
        item.setStatus(normalizeTrainingStatus(batch.getStatus()));
        item.setStatusLabel(trainingStatusLabel(batch.getStatus()));
        item.setPersonCount(Math.toIntExact(educationPersonMapper.selectCount(new LambdaQueryWrapper<SafetyEducationPerson>()
                .eq(SafetyEducationPerson::getBatchId, batch.getId()))));
        return item;
    }

    private boolean isToday(LocalDateTime value) {
        return value != null && LocalDate.now().equals(value.toLocalDate());
    }

    private String normalizeTrainingStatus(String status) {
        if (status == null) return "NOT_STARTED";
        return switch (status.trim().toUpperCase()) {
            case "COMPLETED", "已完成" -> "COMPLETED";
            case "IN_PROGRESS", "进行中" -> "IN_PROGRESS";
            default -> "NOT_STARTED";
        };
    }

    private String trainingStatusLabel(String status) {
        return switch (normalizeTrainingStatus(status)) {
            case "COMPLETED" -> "已完成";
            case "IN_PROGRESS" -> "进行中";
            default -> "未开始";
        };
    }

    private boolean isCertificateWarning(PersonCertificate certificate) {
        return certificate.getExpiryDate() != null
                && !certificate.getExpiryDate().isAfter(LocalDate.now().plusDays(30));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskIdcard(String idcard) {
        if (idcard == null || idcard.length() < 8) return idcard;
        return idcard.substring(0, 4) + "**********" + idcard.substring(idcard.length() - 4);
    }
}
