package com.example.siteplatform.safety.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.person.entity.TemporaryPerson;
import com.example.siteplatform.person.mapper.TemporaryPersonMapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "安全教育管理", description = "安全三级教育培训批次管理接口")
@RestController
@RequestMapping("/api/v1/safety-education")
public class SafetyEducationController {

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

    @Operation(summary = "获取培训批次列表")
    @GetMapping
    public Result<List<Map<String, Object>>> getBatchList(
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        LambdaQueryWrapper<SafetyEducationBatch> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(SafetyEducationBatch::getProjectId, projectId);
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
        authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            return Result.error("培训批次不存在");
        }

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
        authService.getCurrentUser(token);

        SafetyEducationBatch batch = new SafetyEducationBatch();
        batch.setProjectId(params.get("projectId") != null ? Long.valueOf(params.get("projectId").toString()) : null);
        batch.setBatchName((String) params.get("batchName"));
        batch.setEduType((String) params.get("eduType"));
        Object timeValue = firstNonNull(params.get("time"), params.get("trainingTime"));
        if (timeValue != null) {
            batch.setTrainingTime(parseTrainingTime(timeValue));
        }
        batch.setTrainingPlace((String) firstNonNull(params.get("place"), params.get("trainingPlace")));
        batch.setTrainer((String) params.get("trainer"));
        batch.setStatus("进行中");
        batch.setRemark((String) params.get("remark"));
        if (params.get("courseHours") != null) batch.setCourseHours(Integer.valueOf(params.get("courseHours").toString()));
        if (params.get("examType") != null) batch.setExamType((String) params.get("examType"));
        if (params.get("trainingMaterial") != null) batch.setTrainingMaterial((String) params.get("trainingMaterial"));
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());

        batchMapper.insert(batch);

        // 更新文件关联的businessId
        List<Integer> fileIds = (List<Integer>) params.get("fileIds");
        if (fileIds != null) {
            for (Integer fileId : fileIds) {
                FileResource file = fileMapper.selectById(Long.valueOf(fileId));
                if (file != null) {
                    file.setBusinessId(batch.getId());
                    fileMapper.updateById(file);
                }
            }
        }

        // 插入关联人员
        List<Integer> personIds = (List<Integer>) params.get("personIds");
        if (personIds != null) {
            for (Integer personId : personIds) {
                SafetyEducationPerson relation = new SafetyEducationPerson();
                relation.setBatchId(batch.getId());
                relation.setPersonId(Long.valueOf(personId));
                relation.setStatus("进行中");
                relation.setCreateTime(LocalDateTime.now());
                personRelationMapper.insert(relation);
            }
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
        authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            return Result.error("培训批次不存在");
        }

        if (params.get("batchName") != null) batch.setBatchName((String) params.get("batchName"));
        if (params.get("eduType") != null) batch.setEduType((String) params.get("eduType"));
        Object timeValue = firstNonNull(params.get("time"), params.get("trainingTime"));
        if (timeValue != null) {
            batch.setTrainingTime(parseTrainingTime(timeValue));
        }
        Object placeValue = firstNonNull(params.get("place"), params.get("trainingPlace"));
        if (placeValue != null) batch.setTrainingPlace((String) placeValue);
        if (params.get("trainer") != null) batch.setTrainer((String) params.get("trainer"));
        if (params.get("remark") != null) batch.setRemark((String) params.get("remark"));
        if (params.get("courseHours") != null) batch.setCourseHours(Integer.valueOf(params.get("courseHours").toString()));
        if (params.get("examType") != null) batch.setExamType((String) params.get("examType"));
        if (params.get("trainingMaterial") != null) batch.setTrainingMaterial((String) params.get("trainingMaterial"));
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);

        return Result.success();
    }

    @Operation(summary = "标记培训完成")
    @PutMapping("/{id}/complete")
    @Transactional
    public Result<Void> markComplete(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        SafetyEducationBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            return Result.error("培训批次不存在");
        }

        // 更新批次状态
        batch.setStatus("已完成");
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);

        // 更新关联人员状态为"已教育"
        LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SafetyEducationPerson::getBatchId, id);
        List<SafetyEducationPerson> relations = personRelationMapper.selectList(relationWrapper);

        for (SafetyEducationPerson relation : relations) {
            LambdaUpdateWrapper<TemporaryPerson> personWrapper = new LambdaUpdateWrapper<>();
            personWrapper.eq(TemporaryPerson::getId, relation.getPersonId())
                    .set(TemporaryPerson::getStatus, "已教育")
                    .set(TemporaryPerson::getUpdateTime, LocalDateTime.now());
            personnelMapper.update(null, personWrapper);

            // 更新关联记录状态
            relation.setStatus("已完成");
            relation.setFinishTime(LocalDateTime.now());
            personRelationMapper.updateById(relation);
        }

        return Result.success();
    }

    @Operation(summary = "删除培训批次")
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> deleteBatch(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);

        // 删除关联记录
        LambdaQueryWrapper<SafetyEducationPerson> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SafetyEducationPerson::getBatchId, id);
        personRelationMapper.delete(relationWrapper);

        // 删除批次
        batchMapper.deleteById(id);
        return Result.success();
    }

    private Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private LocalDateTime parseTrainingTime(Object value) {
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
        return LocalDateTime.parse(text);
    }
}
