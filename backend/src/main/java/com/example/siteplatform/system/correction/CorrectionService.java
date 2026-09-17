package com.example.siteplatform.system.correction;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.*;
import com.example.siteplatform.siteaccess.service.VisitorDataCryptoService;
import com.example.siteplatform.safetycommittee.CommitteeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static com.example.siteplatform.system.correction.CorrectionRepository.*;

@Service
@RequiredArgsConstructor
public class CorrectionService {
    private static final String PREFIX="data-correction:preview:";
    private final com.example.siteplatform.electricbox.service.ElectricBoxInspectionScopeService inspectionScope;
    private final com.example.siteplatform.electricbox.mapper.ElectricBoxMapper boxes;
    private final com.example.siteplatform.project.service.ProjectProfileService profiles;
    private final CorrectionCatalog catalog;
    private final CorrectionRepository repo;
    private final CorrectionAccess access;
    private final CorrectionAttachments files;
    private final VisitorDataCryptoService crypto;
    private final StringRedisTemplate redis;
    public record Token(long operatorId,String type,long id,CorrectionRequests.Preview request) {}
    public record State(Map<String,Object> row,Map<String,List<Map<String,Object>>> children,Map<String,List<Map<String,Object>>> attachments) {}

    public List<Map<String,Object>> catalog(SysUser user) {
        access.operator(user);
        return catalog.all().stream().map(t-> {
            Map<String,Object> result=new LinkedHashMap<>(); result.put("code",t.code());result.put("label",t.label());result.put("module",t.module());result.put("group",t.group());
            result.put("fields",t.fields());result.put("readOnly",t.readOnly());
            result.put("children",t.children().stream().map(c->Map.of("key",c.key(),"label",c.label(),"fields",c.fields(),"readOnly",c.readOnly())).toList());
            result.put("slots",t.slots().stream().map(s->Map.of("key",s.key(),"label",s.label(),"min",s.min(),"max",s.max(),"policy",s.policy())).toList());return result;
        }).toList();
    }
    public PageResult<Map<String,Object>> page(String code,long projectId,String keyword,String status,LocalDate start,LocalDate end,int page,int size,SysUser user) {
        var type=catalog.require(code); access.project(projectId,type,user);
        if(page<1 || !Set.of(10,20,50,100).contains(size)) throw new BusinessException("分页参数不正确");
        if((start==null)!=(end==null) || start!=null && start.isAfter(end)) throw new BusinessException("开始和结束日期必须成对且顺序正确");
        String scope=type.table().equals("project_info")?"id":"project_id";
        String where=" WHERE "+scope+"=? AND ("+type.predicate()+")";
        var args=new ArrayList<Object>();args.add(projectId);
        if(keyword!=null && !keyword.isBlank()) {
            if(keyword.length()>100)throw new BusinessException("关键词不能超过100字");
            var columns=new LinkedHashSet<String>();columns.add(type.titleColumn());
            for(var f:type.fields()) {if(!f.encrypted()&&Set.of("TEXT","TEXTAREA").contains(f.type()))columns.add(f.key());if(f.nameColumn()!=null)columns.add(f.nameColumn());}
            where+=" AND ("+String.join(" OR ",columns.stream().map(c->"CAST("+c+" AS CHAR) LIKE ?").toList())+")";
            for(String ignored:columns)args.add("%"+keyword.trim()+"%");
        }
        if(status!=null && !status.isBlank()) {
            if(!type.readOnly().contains("status")) throw new BusinessException("当前类型不支持状态筛选");
            where+=" AND status=?";args.add(status);
        }
        if(start!=null) {where+=" AND "+type.dateColumn()+">=? AND "+type.dateColumn()+"<?";args.add(start);args.add(end.plusDays(1));}
        long total=repo.count("SELECT COUNT(*) FROM "+type.table()+where,args.toArray());
        args.add(size);args.add(((long)page-1)*size);
        var rows=repo.rows("SELECT * FROM "+type.table()+where+" ORDER BY "+type.dateColumn()+" DESC,id DESC LIMIT ? OFFSET ?",args.toArray());
        return PageResult.of(page,size,total,rows.stream().map(row->summary(type,row)).toList());
    }
    private Map<String,Object> summary(CorrectionCatalog.Type type,Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();result.put("id",row.get("id"));result.put("title",row.get(type.titleColumn()));result.put("date",row.get(type.dateColumn()));
        result.put("status",row.get("status"));result.put("lastCorrection",lastCorrection(type.code(),id(row.get("id")))); return result;
    }
    public Map<String,Object> detail(String code,long id,SysUser user) { var type=catalog.require(code);return view(type,state(type,id,user,false)); }
    public List<Map<String,Object>> candidates(String code,long id,String key,SysUser user) {
        var type=catalog.require(code);var row=access.target(type,id,user,false);
        var field=java.util.stream.Stream.concat(type.fields().stream(),type.children().stream().flatMap(c->c.fields().stream())).filter(f->f.key().equals(key)&&f.type().equals("USER")).findFirst().orElseThrow(()->new BusinessException("不支持的人员字段"));
        return access.candidates(access.projectId(type,row),type,field,user);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(String code,long id,CorrectionRequests.Preview request,SysUser user) {
        var type=catalog.require(code);State before=state(type,id,user,false);requireRevision(before,request);
        Map<String,Object> after=evaluate(type,before,request,user,false);
        Map<String,Object> old=view(type,before);
        if(repo.encode(old.get("values")).equals(repo.encode(after.get("values"))) && repo.encode(old.get("children")).equals(repo.encode(after.get("children")))
                && repo.encode(old.get("attachments")).equals(repo.encode(after.get("attachments")))) throw new BusinessException("尚未修改任何内容");
        String token=UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX+token,crypto.encrypt(repo.encode(new Token(user.getId(),code,id,request))),Duration.ofMinutes(5));
        return Map.of("confirmationToken",token,"expiresAt",Instant.now().plusSeconds(300).toString(),"before",old,"after",after,"reason",request.reason().trim());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> confirm(CorrectionRequests.Confirm request,SysUser user,String ip) {
        access.operator(user);
        var previous=repo.rows("SELECT id,target_type,target_id,preview_token,after_snapshot_encrypted FROM sys_data_correction_log WHERE operator_id=? AND request_key=?",user.getId(),request.requestKey());
        if(!previous.isEmpty()) {
            var prior=previous.get(0);if(!request.confirmationToken().equals(prior.get("preview_token"))) throw conflict();
            access.target(catalog.require(string(prior.get("target_type"))),id(prior.get("target_id")),user,false);
            return Map.of("correctionId",prior.get("id"),"replayed",true,"record",repo.object(crypto.decrypt(string(prior.get("after_snapshot_encrypted")))));
        }
        String payload=redis.opsForValue().get(PREFIX+request.confirmationToken());if(payload==null) throw conflict();
        Token token=repo.decode(crypto.decrypt(payload),Token.class);
        if(token.operatorId()!=user.getId()) throw BusinessException.forbidden("预览不属于当前管理员");
        var type=catalog.require(token.type());State before=state(type,token.id(),user,true);
        previous=repo.rows("SELECT id,request_key,operator_id,after_snapshot_encrypted FROM sys_data_correction_log WHERE preview_token=?",request.confirmationToken());
        if(!previous.isEmpty()) {
            var prior=previous.get(0);if(!request.requestKey().equals(prior.get("request_key")) || id(prior.get("operator_id"))!=user.getId()) throw conflict();
            return Map.of("correctionId",prior.get("id"),"replayed",true,"record",repo.object(crypto.decrypt(string(prior.get("after_snapshot_encrypted")))));
        }
        requireRevision(before,token.request()); evaluate(type,before,token.request(),user,true);
        long projectId=access.projectId(type,before.row());
        apply(type,before,token.request(),user);
        State after=state(type,token.id(),user,false);
        String who=user.getRealName()==null?user.getUsername():user.getRealName();
        long logId=repo.insert("INSERT INTO sys_data_correction_log(project_id,target_type,target_id,target_label,operator_id,operator_name,reason,request_key,preview_token,before_snapshot_encrypted,after_snapshot_encrypted,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                projectId,type.code(),token.id(),type.label(),user.getId(),who,token.request().reason().trim(),request.requestKey(),request.confirmationToken(),
                crypto.encrypt(repo.encode(view(type,before))),crypto.encrypt(repo.encode(view(type,after))),now());
        files.journal(logId,type,before,after,user);
        repo.changed(repo.jdbc().update("INSERT INTO sys_operation_log(user_id,username,operation_type,operation_desc,business_type,business_id,ip_address,create_time) VALUES(?,?,?,?,?,?,?,?)",
                user.getId(),user.getUsername(),"DATA_CORRECTION","管理员纠正"+type.label()+" #"+token.id()+"；详细修改前后内容见纠错日志 #"+logId,"DATA_CORRECTION",logId,ip,now()));
        return Map.of("correctionId",logId,"replayed",false,"record",view(type,after));
    }
    private State state(CorrectionCatalog.Type type,long id,SysUser user,boolean lock) {
        var row=access.target(type,id,user,lock);
        var children=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(var child:type.children()) children.put(child.key(),repo.rows("SELECT * FROM "+child.table()+" WHERE "+child.parentColumn()+"=? AND ("+child.predicate()+") ORDER BY id"+(lock?" FOR UPDATE":""),id));
        if(type.code().equals("QUALITY_WEEKLY"))children.put("_relatedIssues",repo.rows("SELECT * FROM quality_issue WHERE weekly_inspection_id=? AND deleted=0 ORDER BY id"+(lock?" FOR UPDATE":""),id));
        if(type.code().equals("EDGE_RECTIFICATION")) {
            children.put("_relatedRectifications",repo.rows("SELECT * FROM general_inspection_rectification WHERE task_id=? AND status<>'VOIDED' ORDER BY id"+(lock?" FOR UPDATE":""),row.get("task_id")));
            children.put("_task",repo.rows("SELECT * FROM general_inspection_task WHERE id=?"+(lock?" FOR UPDATE":""),row.get("task_id")));
        }
        return new State(row,children,files.current(type,row,lock));
    }
    private String revision(State state) {return CommitteeService.hash(repo.encode(state));}
    private void requireRevision(State state,CorrectionRequests.Preview request) {
        if(request==null || request.reason()==null || request.reason().trim().isEmpty() || request.reason().length()>500 || request.changes()==null) throw new BusinessException("请填写1至500字纠错原因");
        if(!revision(state).equals(request.expectedRevision())) throw conflict();
    }
    private Map<String,Object> view(CorrectionCatalog.Type type,State state) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",state.row().get("id"));result.put("targetType",type.code());result.put("label",type.label());result.put("projectId",access.projectId(type,state.row()));
        result.put("title",state.row().get(type.titleColumn()));result.put("expectedRevision",revision(state));result.put("values",values(type.fields(),state.row()));
        var readOnly=pick(state.row(),type.readOnly());
        for(String key:List.of("create_time","created_by","created_by_name","uploader_id"))if(state.row().containsKey(key))readOnly.put(key,state.row().get(key));
        result.put("readOnly",readOnly);
        var names=new LinkedHashMap<String,Object>(); for(var field:type.fields()) if(field.nameColumn()!=null)names.put(field.key(),state.row().get(field.nameColumn()));result.put("names",names);
        var children=new LinkedHashMap<String,Object>();
        for(var child:type.children()) children.put(child.key(),state.children().get(child.key()).stream().map(row->{
            var value=new LinkedHashMap<String,Object>();value.put("id",row.get("id"));value.put("values",values(child.fields(),row));value.put("readOnly",pick(row,child.readOnly()));return value;
        }).toList());
        var related=new ArrayList<Map<String,Object>>();
        for(var row:state.children().getOrDefault("_relatedIssues",List.of()))related.add(Map.of("id",row.get("id"),"label","关联质量问题", "values",pick(row,List.of("record_date"))));
        for(var row:state.children().getOrDefault("_relatedRectifications",List.of()))related.add(Map.of("id",row.get("id"),"label","同一临边整改单", "values",pick(row,List.of("assignee_id","assignee_name"))));
        result.put("relatedRecords",related);
        result.put("children",children);result.put("slots",files.slots(type,state.row()));result.put("attachments",state.attachments());return result;
    }
    private Map<String,Object> pick(Map<String,Object> row,List<String> keys) {var out=new LinkedHashMap<String,Object>();for(String key:keys)out.put(key,row.get(key));return out;}
    private Map<String,Object> values(List<CorrectionCatalog.Field> fields,Map<String,Object> row) {
        var values=new LinkedHashMap<String,Object>();
        for(var field:fields) {Object v=row.get(field.key());if(field.encrypted()&&v!=null)v=crypto.decrypt(v.toString());values.put(field.key(),v);}
        return values;
    }
    private Map<String,Object> evaluate(CorrectionCatalog.Type type,State state,CorrectionRequests.Preview request,SysUser user,boolean lock) {
        long projectId=access.projectId(type,state.row());
        var patched=new LinkedHashMap<>(state.row());var normalized=normalize(type.fields(),request.changes(),state.row(),projectId,lock);repo.validateColumns(type.table(),normalized);patched.putAll(normalized);
        validateDomain(type,state.row(),patched,request,lock);
        var children=new LinkedHashMap<>(state.children());
        if(type.code().equals("QUALITY_WEEKLY") && normalized.containsKey("inspection_date"))children.put("_relatedIssues",state.children().get("_relatedIssues").stream().map(row->{var next=new LinkedHashMap<>(row);next.put("record_date",patched.get("inspection_date"));return (Map<String,Object>)next;}).toList());
        if(type.code().equals("EDGE_RECTIFICATION") && normalized.containsKey("assignee_id") && !"VOIDED".equals(state.row().get("status")))children.put("_relatedRectifications",state.children().get("_relatedRectifications").stream().map(row->{var next=new LinkedHashMap<>(row);next.put("assignee_id",patched.get("assignee_id"));next.put("assignee_name",patched.get("assignee_name"));return (Map<String,Object>)next;}).toList());
        if(request.children()!=null) for(var entry:request.children().entrySet()) {
            var child=type.children().stream().filter(c->c.key().equals(entry.getKey())).findFirst().orElseThrow(()->new BusinessException("不支持的关联明细"));
            var originals=state.children().get(child.key());
            if(entry.getValue()==null || entry.getValue().size()!=originals.size()) throw new BusinessException("纠错不能增删业务明细");
            var seen=new HashSet<Long>();var output=new ArrayList<Map<String,Object>>();
            for(var change:entry.getValue()) {
                long rowId=id(change.get("id"));if(!seen.add(rowId))throw new BusinessException("业务明细不能重复");
                var original=originals.stream().filter(r->id(r.get("id"))==rowId).findFirst().orElseThrow(()->new BusinessException("明细不属于当前记录"));
                var input=new LinkedHashMap<>(change);input.remove("id");var row=new LinkedHashMap<>(original);var normalizedChild=normalize(child.fields(),input,original,projectId,lock);repo.validateColumns(child.table(),normalizedChild);row.putAll(normalizedChild);output.add(row);
            }
            output.sort(Comparator.comparingLong(r->id(r.get("id"))));children.put(child.key(),output);
        }
        var attachments=files.validate(type,state,request.attachments(),user,lock);
        if(type.code().equals("QUALITY_DOCUMENT") && request.attachments()!=null && request.attachments().containsKey("file") && !request.changes().containsKey("file_name"))patched.put("file_name",attachments.get("file").get(0).get("name"));
        return view(type,new State(patched,children,attachments));
    }
    private Map<String,Object> normalize(List<CorrectionCatalog.Field> fields,Map<String,Object> changes,Map<String,Object> old,long projectId,boolean lock) {
        var result=new LinkedHashMap<String,Object>();
        if(changes.size()>100)throw new BusinessException("修改字段过多");
        for(var entry:changes.entrySet()) {
            var field=fields.stream().filter(f->f.key().equals(entry.getKey())).findFirst().orElseThrow(()->new BusinessException("包含不允许修改的字段"));
            Object value=normalizeValue(field,entry.getValue());
            Object previous=old.get(field.key());if(field.encrypted()&&previous!=null)previous=crypto.decrypt(previous.toString());
            if(Objects.equals(string(previous),string(value)))continue;
            if(field.type().equals("USER") && value!=null) {
                var person=access.person(projectId,id(value),field.permission(),lock);
                result.put(field.nameColumn(),person.get("real_name")==null?person.get("username"):person.get("real_name"));
                if(field.key().equals("applicant_id"))result.put("applicant_phone",person.get("phone"));
                if(field.key().equals("host_user_id"))result.put("host_phone_encrypted",person.get("phone")==null?null:crypto.encrypt(string(person.get("phone"))));
            }
            result.put(field.key(),field.encrypted()&&value!=null?crypto.encrypt(value.toString()):value);
        }
        return result;
    }
    static Object normalizeValue(CorrectionCatalog.Field f,Object raw) {
        if(raw instanceof Map<?,?> || raw instanceof Collection<?>)throw new BusinessException("字段格式不正确："+f.label());
        String s=string(raw).trim();if(s.isEmpty()){if(f.required())throw new BusinessException("请填写"+f.label());return null;}
        try {return switch(f.type()) {
            case "USER" -> id(raw);
            case "DATE" -> LocalDate.parse(s).toString();
            case "DATETIME" -> LocalDateTime.parse(s.replace(' ','T')).toString();
            case "INTEGER" -> {int value=Integer.parseInt(s);if(value<Integer.parseInt(f.options().get(0))||value>Integer.parseInt(f.options().get(1)))throw new IllegalArgumentException();yield value;}
            case "DECIMAL" -> new BigDecimal(s);
            case "ENUM" -> {if(!f.options().contains(s))throw new IllegalArgumentException();yield s;}
            default -> {if(s.codePointCount(0,s.length())>f.maxLength())throw new IllegalArgumentException();if(f.type().equals("PHONE")&&!s.matches("1[3-9][0-9]{9}"))throw new IllegalArgumentException();yield s;}
        };}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(f.label()+"格式或范围不正确");}
    }
    private void validateDomain(CorrectionCatalog.Type type,Map<String,Object> before,Map<String,Object> after,CorrectionRequests.Preview request,boolean lock) {
        long projectId=access.projectId(type,before), recordId=id(before.get("id"));
        String feedback=type.code().equals("QUALITY_ISSUE")?"rectification_description":Set.of("ELECTRIC_RECTIFICATION","EDGE_RECTIFICATION").contains(type.code())?"feedback":null;
        if(feedback!=null && request.changes().containsKey(feedback) && !string(before.get(feedback)).isBlank() && string(after.get(feedback)).isBlank())
            throw new BusinessException("已提交的整改说明不能清空");
        if(type.code().equals("COMMITTEE")) {
            CommitteeService.validateMetadata(string(after.get("category")),string(after.get("conclusion")));
            if(LocalDateTime.parse(string(after.get("inspected_at"))).isAfter(now()))throw new BusinessException("检查时间不能晚于当前时间");
        }
        if(type.code().equals("QUALITY_WEEKLY")) {
            LocalDate date=LocalDate.parse(string(after.get("inspection_date")));if(date.isAfter(now().toLocalDate()))throw new BusinessException("检查日期不能为未来日期");
            LocalDate week=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if(repo.count("SELECT COUNT(*) FROM quality_weekly_inspection WHERE project_id=? AND week_start=? AND id<>?",projectId,week,recordId)>0)throw BusinessException.of(409,"目标周已存在周检，不能自动合并");
            after.put("week_start",week.toString());
        }
        if(type.code().equals("QUALITY_ISSUE") && before.get("weekly_inspection_id")!=null && !Objects.equals(before.get("record_date"),after.get("record_date")))
            throw new BusinessException("该问题属于周检，请在质量周检中统一更正检查日期");
        if(type.code().equals("ELECTRIC_INSPECTION")) {
            LocalDate date=LocalDate.parse(string(after.get("check_date")));if(date.isAfter(now().toLocalDate()))throw new BusinessException("检查日期不能为未来日期");
            long box=id(before.get("electric_box_id"));
            if(repo.count("SELECT COUNT(*) FROM inspection_record WHERE electric_box_id=? AND check_date=? AND deleted=0 AND id<>?",box,date,recordId)>0)throw BusinessException.of(409,"目标日期已有该电箱日检，不能自动合并");
            if(!Objects.equals(before.get("check_date"),after.get("check_date")) && !inspectionScope.isRequired(boxes.selectById(box),date))
                throw new BusinessException("目标日期不属于该电箱有效巡检范围");
        }
        if(type.code().equals("PROJECT_LOCATION")) {
            if(!request.confirmLocation())throw new BusinessException("请核对地址、坐标来源和定位点并明确确认");
            BigDecimal lon=new BigDecimal(string(after.get("longitude"))),lat=new BigDecimal(string(after.get("latitude")));
            if(lon.abs().compareTo(new BigDecimal("180"))>0||lat.abs().compareTo(new BigDecimal("90"))>0)throw new BusinessException("经纬度超出范围");
        }
        if(type.code().equals("PROJECT")) {
            for(var entry:request.changes().entrySet())if(entry.getValue()!=null && Set.of("contract_amount","building_area","land_area","building_height","excavation_depth").contains(entry.getKey()) && new BigDecimal(string(entry.getValue())).signum()<0)throw new BusinessException("项目数值不能为负数");
            if(request.changes().containsKey("fixed_ip_address"))profiles.validateIp(string(after.get("fixed_ip_address")));
        }
        if(type.code().equals("PROJECT")) for(String prefix:List.of("","actual_")) {
            String start=string(after.get(prefix+"start_date")),end=string(after.get(prefix+"end_date"));
            if(!start.isEmpty()&&!end.isEmpty()&&LocalDate.parse(start).isAfter(LocalDate.parse(end)))throw new BusinessException("项目开始日期不得晚于结束日期");
        }
        if(type.code().equals("DOCUMENT_FOLDER") && repo.count("SELECT COUNT(*) FROM document_folder WHERE project_id=? AND parent_id<=>? AND folder_name=? AND deleted=0 AND id<>?",projectId,before.get("parent_id"),after.get("folder_name"),recordId)>0)throw BusinessException.of(409,"同级已有同名资料目录");
        if(type.code().equals("ELECTRIC_BOX") && repo.count("SELECT COUNT(*) FROM electric_box WHERE project_id=? AND box_code=? AND deleted=0 AND id<>?",projectId,after.get("box_code"),recordId)>0)throw BusinessException.of(409,"项目内电箱编号已存在");
        if(type.code().equals("EDGE_POINT") && repo.count("SELECT COUNT(*) FROM general_inspection_point WHERE project_id=? AND point_code=? AND deleted=0 AND id<>?",projectId,after.get("point_code"),recordId)>0)throw BusinessException.of(409,"项目内点位编号已存在");
    }
    private void apply(CorrectionCatalog.Type type,State before,CorrectionRequests.Preview request,SysUser user) {
        long recordId=id(before.row().get("id")),projectId=access.projectId(type,before.row());
        var updates=normalize(type.fields(),request.changes(),before.row(),projectId,true);
        var patched=new LinkedHashMap<>(before.row());patched.putAll(updates);validateDomain(type,before.row(),patched,request,true);
        if(type.code().equals("QUALITY_WEEKLY") && updates.containsKey("inspection_date")) {
            updates.put("week_start",patched.get("week_start"));
            repo.rows("SELECT id FROM quality_issue WHERE weekly_inspection_id=? ORDER BY id FOR UPDATE",recordId);
            repo.jdbc().update("UPDATE quality_issue SET record_date=?,version=version+1,update_time=? WHERE weekly_inspection_id=? AND deleted=0",updates.get("inspection_date"),now(),recordId);
        }
        if(type.code().equals("EDGE_RECTIFICATION")) {
            // The existing edge workflow submits all items of one sheet together under one responsible person.
            if(updates.containsKey("assignee_id") && !"VOIDED".equals(before.row().get("status"))) {
                repo.jdbc().update("UPDATE general_inspection_rectification SET assignee_id=?,assignee_name=?,version=version+1,update_time=? WHERE task_id=? AND id<>? AND status<>'VOIDED'",updates.get("assignee_id"),updates.get("assignee_name"),now(),before.row().get("task_id"),recordId);
                repo.jdbc().update("UPDATE general_inspection_task SET default_rectifier_id=?,default_rectifier_name=? WHERE id=?",updates.get("assignee_id"),updates.get("assignee_name"),before.row().get("task_id"));
            }
            repo.jdbc().update("UPDATE general_inspection_task SET version=version+1,update_time=? WHERE id=?",now(),before.row().get("task_id"));
        }
        if(request.children()!=null)for(var child:type.children()) if(request.children().containsKey(child.key()))for(var change:request.children().get(child.key())) {
            long childId=id(change.get("id"));var original=before.children().get(child.key()).stream().filter(r->id(r.get("id"))==childId).findFirst().orElseThrow();
            var input=new LinkedHashMap<>(change);input.remove("id");var changed=normalize(child.fields(),input,original,projectId,true);
            if(!changed.isEmpty()&&original.containsKey("update_time"))changed.put("update_time",now());repo.update(child.table(),childId,changed);
        }
        if(type.code().equals("PROJECT") && updates.containsKey("building_area"))updates.put("area",updates.get("building_area")==null?null:string(updates.get("building_area")));
        files.apply(type,before,request.attachments(),user,request.reason(),updates);
        if(type.versionColumn()!=null)updates.put(type.versionColumn(),((Number)before.row().get(type.versionColumn())).longValue()+1);
        if(before.row().containsKey("update_time"))updates.put("update_time",now());
        repo.update(type.table(),recordId,updates);
        synchronizePeople(type,before.row(),patched);
    }
    private void synchronizePeople(CorrectionCatalog.Type type,Map<String,Object> before,Map<String,Object> after) {
        if(!Set.of("SINGLE_VISIT","MEETING_REGISTRATION","GUARD_REGISTRATION").contains(type.code()))return;
        var child=type.children().get(0);
        // Main-contact fields and the existing main person describe the same individual.
        repo.jdbc().update("UPDATE "+child.table()+" SET person_company=?,person_name=?,phone_encrypted=?,update_time=? WHERE "+child.parentColumn()+"=? AND person_type='CONTACT' AND deleted=0",
                after.get("visitor_company"),after.get("contact_name"),after.get("contact_phone_encrypted"),now(),before.get("id"));
    }
    public PageResult<Map<String,Object>> history(String type,long targetId,int page,SysUser user) {
        access.target(catalog.require(type),targetId,user,false);if(page<1)throw new BusinessException("页码不正确");
        long total=repo.count("SELECT COUNT(*) FROM sys_data_correction_log WHERE target_type=? AND target_id=?",type,targetId);
        return PageResult.of(page,20,total,repo.rows("SELECT id,operator_name,reason,create_time FROM sys_data_correction_log WHERE target_type=? AND target_id=? ORDER BY id DESC LIMIT 20 OFFSET ?",type,targetId,(long)(page-1)*20));
    }
    public Map<String,Object> log(long id,SysUser user) {
        access.operator(user);var row=repo.one("SELECT * FROM sys_data_correction_log WHERE id=?",id);access.project(id(row.get("project_id")),catalog.require(string(row.get("target_type"))),user);
        var result=new LinkedHashMap<>(row);result.remove("request_key");result.remove("preview_token");result.remove("before_snapshot_encrypted");result.remove("after_snapshot_encrypted");
        result.put("before",repo.object(crypto.decrypt(string(row.get("before_snapshot_encrypted")))));result.put("after",repo.object(crypto.decrypt(string(row.get("after_snapshot_encrypted")))));
        return result;
    }
    public Map<String,Object> lastCorrection(String type,long id) {
        var rows=repo.rows("SELECT id,operator_name,create_time FROM sys_data_correction_log WHERE target_type=? AND target_id=? ORDER BY id DESC LIMIT 1",type,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private static BusinessException conflict(){return BusinessException.of(409,"记录或预览已变化，请保留输入并重新核对");}
}
