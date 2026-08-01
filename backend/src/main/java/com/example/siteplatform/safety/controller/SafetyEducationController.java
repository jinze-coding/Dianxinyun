package com.example.siteplatform.safety.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.file.constant.FileStatus;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.constant.PersonnelStatus;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.safety.entity.SafetyEducationBatch;
import com.example.siteplatform.safety.entity.SafetyEducationPerson;
import com.example.siteplatform.safety.mapper.SafetyEducationBatchMapper;
import com.example.siteplatform.safety.mapper.SafetyEducationPersonMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "安全教育管理", description = "安全三级教育培训批次管理接口")
@RestController
@RequestMapping("/api/v1/safety-education")
public class SafetyEducationController {

    private static final String FILE_BUSINESS_TYPE = "safety_education";
    private static final int BATCH_NAME_MAX_LENGTH = 200;
    private static final int EDU_TYPE_MAX_LENGTH = 50;
    private static final int PLACE_MAX_LENGTH = 100;
    private static final int TRAINER_MAX_LENGTH = 50;
    private static final int REMARK_MAX_LENGTH = 500;
    private static final int EXAM_TYPE_MAX_LENGTH = 100;
    private static final int MATERIAL_MAX_LENGTH = 200;
    private static final int MAX_FILE_COUNT = 20;
    private static final int MAX_PERSON_COUNT = 500;

    @Autowired
    private SafetyEducationBatchMapper batchMapper;

    @Autowired
    private SafetyEducationPersonMapper personRelationMapper;

    @Autowired
    private TemporaryPersonMapper personnelMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private FileResourceMapper fileMapper;

    @Autowired
    private ProjectPermissionService projectPermissionService;

    @Operation(summary = "获取培训批次列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getBatchList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        LambdaQueryWrapper<SafetyEducationBatch> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectPermissionService.checkProjectPermission(currentUser.getId(), projectId);
            wrapper.eq(SafetyEducationBatch::getProjectId, projectId);
        } else if (!projectPermissionService.isPlatformAdmin(currentUser.getId())) {
            List<Long> projectIds = projectPermissionService.getUserProjects(currentUser.getId()).stream()
                    .map(ProjectInfo::getId)
                    .toList();
            if (projectIds.isEmpty()) {
                return Result.success(List.of());
            }
            wrapper.in(SafetyEducationBatch::getProjectId, projectIds);
        }
        wrapper.orderByDesc(SafetyEducationBatch::getCreateTime);

        List<SafetyEducationBatch> batches = batchMapper.selectList(wrapper);

        // 转换为带关联人员信息的map
        List<Map<String, Object>> result = new ArrayList<>();
        for (SafetyEducationBatch batch : batches) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", batch.getId());
            item.put("projectId", batch.getProjectId());
            item.put("batchName", batch.getBatchName());
            item.put("eduType", batch.getEduType());
            item.put("time", batch.getTrainingTime());
            item.put("place", batch.getTrainingPlace());
            item.put("trainer", batch.getTrainer());
            item.put("status", batch.getStatus());
            item.put("remark", batch.getRemark());
            item.put("courseHours", batch.getCourseHours());
            item.put("examType", batch.getExamType());
            item.put("trainingMaterial", batch.getTrainingMaterial());
            item.put("createTime", batch.getCreateTime());

            // 查询关联人员
            LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
            relationWrapper.eq(SafetyEducationPerson::getBatchId, batch.getId());
            List<SafetyEducationPerson> relations = personRelationMapper.selectList(relationWrapper);

            List<Long> personIds = new ArrayList<>();
            List<String> personNames = new ArrayList<>();
            for (SafetyEducationPerson relation : relations) {
                personIds.add(relation.getPersonId());
                TemporaryPerson person = personnelMapper.selectById(relation.getPersonId());
                if (person != null) {
                    personNames.add(person.getName());
                }
            }
            item.put("personIds", personIds);
            item.put("personNames", personNames);
            item.put("personCount", personIds.size());

            // 查询关联文件
            LambdaQueryWrapper<FileResource> fileWrapper = new LambdaQueryWrapper<>();
            fileWrapper.eq(FileResource::getBusinessId, batch.getId())
                       .eq(FileResource::getBusinessType, "safety_education")
                       .eq(FileResource::getDeleted, 0);
            List<FileResource> files = fileMapper.selectList(fileWrapper);
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (FileResource file : files) {
                Map<String, Object> fileMap = new HashMap<>();
                fileMap.put("id", file.getId());
                fileMap.put("name", file.getFileName());
                fileMap.put("fileName", file.getFileName());
                fileMap.put("fileType", file.getFileType());
                fileMap.put("fileSize", file.getFileSize());
                fileList.add(fileMap);
            }
            item.put("files", fileList);

            result.add(item);
        }

        return Result.success(result);
    }

    @Operation(summary = "获取培训批次详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getBatchById(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("培训批次不存在");
        }
        projectPermissionService.checkProjectPermission(currentUser.getId(), batch.getProjectId());

        Map<String, Object> item = new HashMap<>();
        item.put("id", batch.getId());
        item.put("projectId", batch.getProjectId());
        item.put("batchName", batch.getBatchName());
        item.put("eduType", batch.getEduType());
        item.put("time", batch.getTrainingTime());
        item.put("place", batch.getTrainingPlace());
        item.put("trainer", batch.getTrainer());
        item.put("status", batch.getStatus());
        item.put("remark", batch.getRemark());
        item.put("courseHours", batch.getCourseHours());
        item.put("examType", batch.getExamType());
        item.put("trainingMaterial", batch.getTrainingMaterial());
        item.put("createTime", batch.getCreateTime());

        // 查询关联人员
        LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SafetyEducationPerson::getBatchId, id);
        List<SafetyEducationPerson> relations = personRelationMapper.selectList(relationWrapper);

        List<Long> personIds = new ArrayList<>();
        List<String> personNames = new ArrayList<>();
        for (SafetyEducationPerson relation : relations) {
            personIds.add(relation.getPersonId());
            TemporaryPerson person = personnelMapper.selectById(relation.getPersonId());
            if (person != null) {
                personNames.add(person.getName());
            }
        }
        item.put("personIds", personIds);
        item.put("personNames", personNames);
        item.put("personCount", personIds.size());

        // 查询关联文件
        LambdaQueryWrapper<FileResource> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(FileResource::getBusinessId, id)
                   .eq(FileResource::getBusinessType, "safety_education")
                   .eq(FileResource::getDeleted, 0);
        List<FileResource> files = fileMapper.selectList(fileWrapper);
        List<Map<String, Object>> fileList = new ArrayList<>();
        for (FileResource file : files) {
            Map<String, Object> fileMap = new HashMap<>();
            fileMap.put("id", file.getId());
            fileMap.put("name", file.getFileName());
            fileMap.put("fileName", file.getFileName());
            fileMap.put("fileType", file.getFileType());
            fileMap.put("fileSize", file.getFileSize());
            fileList.add(fileMap);
        }
        item.put("files", fileList);

        return Result.success(item);
    }

    @Operation(summary = "创建培训批次")
    @PostMapping
    @Transactional
    public Result<Map<String, Object>> createBatch(
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        requireParams(params);

        SafetyEducationBatch batch = new SafetyEducationBatch();
        batch.setProjectId(requirePositiveLong(params.get("projectId"), "项目ID"));
        requireManagePermission(currentUser, batch.getProjectId());
        List<Long> personIds = parseIdList(params.get("personIds"), "培训人员", MAX_PERSON_COUNT, true);
        List<TemporaryPerson> people = validatePeople(personIds, batch.getProjectId());
        List<Long> fileIds = parseIdList(params.get("fileIds"), "培训资料", MAX_FILE_COUNT, false);
        List<FileResource> files = validateFiles(fileIds, batch.getProjectId(), currentUser.getId());

        batch.setBatchName(requireText(params.get("batchName"), BATCH_NAME_MAX_LENGTH, "批次名称"));
        batch.setEduType(optionalText(params.get("eduType"), EDU_TYPE_MAX_LENGTH, "教育类型"));
        Object timeValue = firstNonNull(params.get("time"), params.get("trainingTime"));
        if (timeValue != null) {
            batch.setTrainingTime(parseTrainingTime(timeValue));
        }
        batch.setTrainingPlace(optionalText(firstNonNull(params.get("place"), params.get("trainingPlace")),
                PLACE_MAX_LENGTH, "培训地点"));
        batch.setTrainer(optionalText(params.get("trainer"), TRAINER_MAX_LENGTH, "培训讲师"));
        batch.setStatus("IN_PROGRESS");
        batch.setRemark(optionalText(params.get("remark"), REMARK_MAX_LENGTH, "批次备注"));
        if (params.get("courseHours") != null) batch.setCourseHours(parseCourseHours(params.get("courseHours")));
        batch.setExamType(optionalText(params.get("examType"), EXAM_TYPE_MAX_LENGTH, "考核方式"));
        batch.setTrainingMaterial(optionalText(params.get("trainingMaterial"), MATERIAL_MAX_LENGTH, "培训课件"));
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());

        requireSingleWrite(batchMapper.insert(batch), "培训批次新增");

        for (FileResource file : files) {
            bindFile(file, batch.getProjectId(), batch.getId(), currentUser.getId());
        }

        for (TemporaryPerson person : people) {
            SafetyEducationPerson relation = new SafetyEducationPerson();
            relation.setBatchId(batch.getId());
            relation.setPersonId(person.getId());
            relation.setStatus("IN_PROGRESS");
            relation.setCreateTime(LocalDateTime.now());
            requireSingleWrite(personRelationMapper.insert(relation), "培训人员关联新增");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", batch.getId());
        result.put("personIds", personIds);
        return Result.success(result);
    }

    @Operation(summary = "更新培训批次")
    @PutMapping("/{id}")
    @Transactional
    public Result<Void> updateBatch(
            @PathVariable Long id,
            @RequestBody Map<String, Object> params,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        requireParams(params);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("培训批次不存在");
        }
        requireManagePermission(currentUser, batch.getProjectId());

        if (params.get("batchName") != null) batch.setBatchName(requireText(params.get("batchName"), BATCH_NAME_MAX_LENGTH, "批次名称"));
        if (params.get("eduType") != null) batch.setEduType(optionalText(params.get("eduType"), EDU_TYPE_MAX_LENGTH, "教育类型"));
        Object timeValue = firstNonNull(params.get("time"), params.get("trainingTime"));
        if (timeValue != null) {
            batch.setTrainingTime(parseTrainingTime(timeValue));
        }
        Object placeValue = firstNonNull(params.get("place"), params.get("trainingPlace"));
        if (placeValue != null) batch.setTrainingPlace(optionalText(placeValue, PLACE_MAX_LENGTH, "培训地点"));
        if (params.get("trainer") != null) batch.setTrainer(optionalText(params.get("trainer"), TRAINER_MAX_LENGTH, "培训讲师"));
        if (params.get("remark") != null) batch.setRemark(optionalText(params.get("remark"), REMARK_MAX_LENGTH, "批次备注"));
        if (params.get("courseHours") != null) batch.setCourseHours(parseCourseHours(params.get("courseHours")));
        if (params.get("examType") != null) batch.setExamType(optionalText(params.get("examType"), EXAM_TYPE_MAX_LENGTH, "考核方式"));
        if (params.get("trainingMaterial") != null) batch.setTrainingMaterial(optionalText(params.get("trainingMaterial"), MATERIAL_MAX_LENGTH, "培训课件"));
        batch.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(batchMapper.updateById(batch), "培训批次更新");

        return Result.success();
    }

    @Operation(summary = "标记培训完成")
    @PutMapping("/{id}/complete")
    @Transactional
    public Result<Void> markComplete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("培训批次不存在");
        }
        requireManagePermission(currentUser, batch.getProjectId());

        if ("COMPLETED".equalsIgnoreCase(batch.getStatus()) || "已完成".equals(batch.getStatus())) {
            return Result.success();
        }

        // 更新批次状态
        batch.setStatus("COMPLETED");
        batch.setUpdateTime(LocalDateTime.now());
        requireSingleWrite(batchMapper.updateById(batch), "培训批次完成");

        // 更新关联人员状态为"已教育"
        LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SafetyEducationPerson::getBatchId, id);
        List<SafetyEducationPerson> relations = personRelationMapper.selectList(relationWrapper);

        for (SafetyEducationPerson relation : relations) {
            TemporaryPerson person = personnelMapper.selectById(relation.getPersonId());
            if (person != null && !PersonnelStatus.LEFT.equals(PersonnelStatus.normalize(person.getStatus()))) {
                LambdaUpdateWrapper<TemporaryPerson> personWrapper = new LambdaUpdateWrapper<>();
                personWrapper.eq(TemporaryPerson::getId, relation.getPersonId())
                        .set(TemporaryPerson::getStatus, PersonnelStatus.EDUCATED)
                        .set(TemporaryPerson::getUpdateTime, LocalDateTime.now());
                requireSingleWrite(personnelMapper.update(null, personWrapper), "培训人员教育状态更新");
            }

            // 更新关联记录状态
            relation.setStatus("COMPLETED");
            relation.setFinishTime(LocalDateTime.now());
            requireSingleWrite(personRelationMapper.updateById(relation), "培训人员关联完成");
        }

        return Result.success();
    }

    @Operation(summary = "删除培训批次")
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> deleteBatch(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw BusinessException.notFound("培训批次不存在");
        }
        requireManagePermission(currentUser, batch.getProjectId());

        // 删除关联记录
        LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SafetyEducationPerson::getBatchId, id);
        long relationCount = personRelationMapper.selectCount(relationWrapper);
        int deletedRelations = personRelationMapper.delete(relationWrapper);
        if (deletedRelations != relationCount) {
            throw BusinessException.of(409, "培训人员关联删除未完整生效，请刷新后重试");
        }

        // 删除批次
        requireSingleWrite(batchMapper.deleteById(id), "培训批次删除");
        return Result.success();
    }

    private Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private void requireManagePermission(SysUser currentUser, Long projectId) {
        if (!projectPermissionService.canManagePersonnel(currentUser.getId(), projectId)) {
            throw BusinessException.forbidden("无安全教育管理权限");
        }
    }

    private LocalDateTime parseTrainingTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        text = text.replace(' ', 'T');
        if (text.length() == 10) {
            text = text + "T00:00:00";
        } else if (text.length() == 16) {
            text = text + ":00";
        } else if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("培训时间格式不正确");
        }
    }

    private void requireParams(Map<String, Object> params) {
        if (params == null) {
            throw new BusinessException("培训批次信息不能为空");
        }
    }

    private Long requirePositiveLong(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
        try {
            long parsed = Long.parseLong(value.toString().trim());
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(fieldName + "不正确");
        }
    }

    private List<Long> parseIdList(Object value, String fieldName, int maxCount, boolean required) {
        if (value == null) {
            if (required) throw new BusinessException("请至少选择一名培训人员");
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new BusinessException(fieldName + "格式不正确");
        }
        if (required && values.isEmpty()) {
            throw new BusinessException("请至少选择一名培训人员");
        }
        if (values.size() > maxCount) {
            throw new BusinessException(fieldName + "数量不能超过" + maxCount);
        }
        List<Long> result = new ArrayList<>();
        Set<Long> distinct = new HashSet<>();
        for (Object item : values) {
            Long id = requirePositiveLong(item, fieldName + "ID");
            if (!distinct.add(id)) {
                throw new BusinessException(fieldName + "不能重复");
            }
            result.add(id);
        }
        return result;
    }

    private List<TemporaryPerson> validatePeople(List<Long> personIds, Long projectId) {
        List<TemporaryPerson> people = new ArrayList<>();
        for (Long personId : personIds) {
            TemporaryPerson person = personnelMapper.selectById(personId);
            if (person == null || !projectId.equals(person.getProjectId())) {
                throw new BusinessException("培训人员不属于当前项目");
            }
            people.add(person);
        }
        return people;
    }

    private List<FileResource> validateFiles(List<Long> fileIds, Long projectId, Long uploaderId) {
        List<FileResource> files = new ArrayList<>();
        for (Long fileId : fileIds) {
            FileResource file = fileMapper.selectById(fileId);
            if (file == null || !projectId.equals(file.getProjectId())) {
                throw new BusinessException("培训资料不属于当前项目");
            }
            if (!FILE_BUSINESS_TYPE.equalsIgnoreCase(file.getBusinessType())) {
                throw new BusinessException("培训资料类型不正确");
            }
            if (!FileStatus.UPLOADED.equals(FileStatus.normalize(file.getStatus()))) {
                throw new BusinessException("培训资料状态不允许绑定");
            }
            if (!uploaderId.equals(file.getUploaderId())) {
                throw BusinessException.forbidden("只能关联本人刚上传的培训资料");
            }
            if (file.getBusinessId() != null) {
                throw new BusinessException("培训资料已关联其他业务记录");
            }
            files.add(file);
        }
        return files;
    }

    private void bindFile(FileResource file, Long projectId, Long batchId, Long uploaderId) {
        LambdaUpdateWrapper<FileResource> wrapper = new LambdaUpdateWrapper<FileResource>()
                .eq(FileResource::getId, file.getId())
                .eq(FileResource::getProjectId, projectId)
                .eq(FileResource::getBusinessType, file.getBusinessType())
                .eq(FileResource::getStatus, file.getStatus())
                .eq(FileResource::getUploaderId, uploaderId)
                .eq(FileResource::getDeleted, 0)
                .isNull(FileResource::getBusinessId)
                .set(FileResource::getBusinessId, batchId)
                .set(FileResource::getUpdateTime, LocalDateTime.now());
        requireSingleWrite(fileMapper.update(null, wrapper), "培训资料绑定");
    }

    private String requireText(Object value, int maxLength, String fieldName) {
        String normalized = optionalText(value, maxLength, fieldName);
        if (normalized == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(Object value, int maxLength, String fieldName) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            throw new BusinessException(fieldName + "格式不正确");
        }
        String normalized = value.toString().trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw new BusinessException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private Integer parseCourseHours(Object value) {
        try {
            int hours = Integer.parseInt(value.toString().trim());
            if (hours < 0 || hours > 10000) throw new NumberFormatException();
            return hours;
        } catch (NumberFormatException exception) {
            throw new BusinessException("培训课时必须是0到10000之间的整数");
        }
    }

    private void requireSingleWrite(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw BusinessException.of(409, operation + "未生效，请刷新后重试");
        }
    }
}
