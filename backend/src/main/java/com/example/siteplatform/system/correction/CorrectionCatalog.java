package com.example.siteplatform.system.correction;

import com.example.siteplatform.common.BusinessException;
import org.springframework.stereotype.Component;
import java.util.*;

/** Closed, server-owned business catalog. No table, predicate or column comes from a request. */
@Component
public class CorrectionCatalog {
    public record Field(String key, String label, String type, boolean required, int maxLength,
                        List<String> options, String permission, String nameColumn, boolean encrypted) {}
    public record Child(String key, String label, String table, String parentColumn, String predicate,
                        List<Field> fields, List<String> readOnly) {}
    public record Slot(String key, String label, String mode, String column, String businessType,
                       String policy, int min, int max) {}
    public record Type(String code, String label, String module, String group, String table, String predicate,
                       String titleColumn, String dateColumn, String versionColumn, boolean updatedAt,
                       List<Field> fields, List<String> readOnly, List<Child> children, List<Slot> slots) {}
    private final Map<String, Type> types = new LinkedHashMap<>();
    public CorrectionCatalog() {
        var visitor = List.of(text("visitor_company","来访单位",100,false),text("contact_name","本人姓名",50,true),
                phone("contact_phone_encrypted","本人手机号"),choice("travel_mode","出行方式","DRIVING","OTHER"),
                text("vehicle_plate","车牌",30,false),area("visitor_remark","来访备注",500));
        var persons = List.of(text("person_company","单位",100,false),text("person_name","姓名",50,false),phone("phone_encrypted","手机号"));
        var invitation = new ArrayList<Field>(List.of(text("purpose","来访事由／会议主题",200,true),text("visit_location","地点",200,false),
                user("host_user_id","接待人",null,"host_name"),area("internal_remark","内部备注",500)));
        invitation.addAll(visitor);
        add("SINGLE_VISIT","单次预约","SITE_ACCESS","场内管理","site_visit_invitation","deleted=0 AND invite_type='SINGLE'",
                "invite_no","visit_start_time","version",invitation,List.of("status","submitted_time","visit_start_time","visit_end_time","created_by_name"),
                List.of(child("people","来访人员","site_visit_person","invitation_id","deleted=0 AND person_type<>'CONTACT'",persons,"person_type")),List.of());
        add("MEETING","会议邀请","SITE_ACCESS","场内管理","site_visit_invitation","deleted=0 AND invite_type='MEETING'",
                "purpose","visit_start_time","version",invitation.subList(0,4),List.of("status","visit_start_time","visit_end_time","created_by_name"),List.of(),List.of());
        add("MEETING_REGISTRATION","会议预约人员","SITE_ACCESS","场内管理","site_meeting_visit_registration","deleted=0",
                "registration_no","registered_time","version",visitor,List.of("status","invitation_id","registered_time","registration_source"),
                List.of(child("people","预约人员","site_meeting_visit_person","registration_id","deleted=0 AND person_type<>'CONTACT'",persons,"person_type")),List.of());
        add("GUARD_REGISTRATION","门卫登记","SITE_ACCESS","场内管理","site_guard_visit_registration","deleted=0",
                "registration_no","registered_time","version",visitor,List.of("status","registered_time","valid_until"),
                List.of(child("people","登记人员","site_guard_visit_person","registration_id","deleted=0 AND person_type<>'CONTACT'",persons,"person_type")),List.of());
        add("MEETING_MATERIAL","会议资料","SITE_ACCESS","场内管理","site_meeting_material","status<>'DELETED'",
                "title","create_time","version",List.of(text("title","标题",200,true),choice("category","分类","PUBLICITY","AGENDA","MINUTES","MEDIA","OTHER"),area("description","说明",1000)),
                List.of("status","invitation_id","published_version_id"),List.of(),List.of(slot("file","当前文件","MEETING","","MEETING_MATERIAL","MEETING",1,1)));
        add("DOCUMENT_FOLDER","资料目录","DOCUMENT","资料与用印","document_folder","deleted=0","folder_name","create_time",null,
                List.of(text("folder_name","目录名称",100,true),number("sort_no","排序",0,999999)),List.of("parent_id"),List.of(),List.of());
        add("DOCUMENT","工程资料","DOCUMENT","资料与用印","project_document","deleted=0","title","create_time",null,
                List.of(text("title","标题",200,true),text("document_no","资料编号",100,false),choice("category","分类","PROJECT_DATA","DRAWING","FORM","CONSTRUCTION_RECORD","MEETING","OTHER"),area("remark","备注",500)),
                List.of("status","document_type","folder_id","created_by_name"),List.of(),List.of(slot("file","当前版本文件","DOCUMENT","","PROJECT_DOCUMENT","DOCUMENT",1,1)));
        add("DOCUMENT_INCOMING","收文说明","DOCUMENT","资料与用印","document_incoming_batch","1=1","incoming_no","received_at","version",
                List.of(text("source_organization","来文单位",200,true),text("sender_name","发送人",50,false),text("source_reference_no","来文编号",100,false),area("remark","备注",1000)),
                List.of("status","received_at","receiver_name","published_time"),List.of(),List.of());
        add("DOCUMENT_DISTRIBUTION","发放说明","DOCUMENT","资料与用印","document_distribution_batch","1=1","distribution_no","create_time","version",
                List.of(area("message_note","发放说明",1000)),List.of("status","deadline","published_by_name","published_time"),List.of(),List.of());
        add("SEAL_APPLICATION","用印申请","DOCUMENT","资料与用印","seal_application","deleted=0","application_no","application_date","version",
                List.of(text("company_name","单位名称",200,true),text("department_name","项目部名称",200,true),new Field("purpose","用印事由","TEXTAREA",true,1000,List.of(),null,null,false),
                        user("applicant_id","申请人","seal.view","applicant_name"),date("application_date","申请日期",true)),
                List.of("status","seal_name","submit_time","approver_name","approval_time","approval_opinion","stamped_result_required"),
                List.of(child("items","用印文件明细","seal_application_item","application_id","1=1",List.of(text("document_name","文件名称",200,true),number("copies","份数",1,9999)),"sort_order")),
                List.of(slot("source","待盖章附件","SEAL","SOURCE","SEAL_SOURCE","DOCUMENT",0,30),slot("stamped","盖章件","SEAL","STAMPED_RESULT","SEAL_STAMPED_RESULT","DOCUMENT",0,30)));
        add("ELECTRIC_BOX","电箱台账","INSPECTION","电箱巡检","electric_box","deleted=0","box_code","create_time",null,
                List.of(text("box_code","电箱编号",100,true),text("box_name","电箱名称",100,true),text("install_location","安装位置",255,true),
                        user("responsible_electrician_id","责任电工","inspection.submit","responsible_electrician_name"),
                        user("safety_manager_id","安全负责人","inspection.review","safety_manager_name"),area("remark","备注",500)),
                List.of("status","qr_status"),List.of(),List.of());
        add("ELECTRIC_INSPECTION","电箱日检记录","INSPECTION","电箱巡检","inspection_record","deleted=0","id","check_date",null,
                List.of(date("check_date","检查日期",true),user("inspector_id","检查人","inspection.submit","inspector_name"),area("remark","备注",500)),
                List.of("electric_box_id","status","review_status","reviewer_name","review_time","review_comment","abnormal_count"),
                List.of(child("items","检查项目","inspection_record_item","record_id","deleted=0",List.of(area("description","检查说明",500)),"item_name","result")),
                List.of(slot("outer","外观照片","JSON","outer_photo_file_ids","INSPECTION_RECORD","IMAGE",1,20),slot("inner","内部照片","JSON","inner_photo_file_ids","INSPECTION_RECORD","IMAGE",1,20)));
        add("ELECTRIC_RECTIFICATION","电箱整改记录","INSPECTION","电箱巡检","inspection_rectification","deleted=0","box_code","create_time",null,
                List.of(area("problem_desc","问题描述",2000),area("requirement","整改要求",2000),area("feedback","整改说明",2000),
                        user("assignee_id","整改负责人","inspection.rectify","assignee_name"),date("deadline","整改期限",true)),
                List.of("status","inspection_record_id","reviewer_name","review_time","completed_time","close_time"),List.of(),
                List.of(slot("photos","整改照片","JSON","rectification_photo_file_ids","INSPECTION_RECTIFICATION","IMAGE",0,20)));
        add("EDGE_POINT","临边点位","INSPECTION","临边巡检","general_inspection_point","deleted=0","point_name","create_time","version",
                List.of(text("point_code","点位编号",100,true),text("point_name","点位名称",100,true),text("area_name","区域",100,false),text("building_name","楼栋",100,false),
                        text("floor_name","楼层",100,false),area("location_desc","位置说明",500),area("risk_note","风险说明",1000)),
                List.of("status","category_name"),List.of(),List.of(slot("photos","参考照片","JSON","reference_photo_file_ids","INSPECTION_CUSTOM_POINT","IMAGE",0,20)));
        add("EDGE_TASK","临边检查记录","INSPECTION","临边巡检","general_inspection_task","1=1","point_name","occurrence_date","version",
                List.of(area("remark","检查说明",2000),area("public_remark","公开说明",1000),
                        user("assignee_id","执行负责人","EDGE_INSPECTION_SUBMIT","assignee_name")),
                List.of("status","occurrence_date","submitted_by_name","submitted_time","abnormal_count","point_id","plan_id"),
                List.of(child("items","检查项目","general_inspection_task_item","task_id","1=1",List.of(area("description","检查说明",2000)),"item_name","result")),
                List.of(slot("photos","检查照片","JSON","overall_photo_file_ids","EDGE_INSPECTION_TASK","IMAGE",0,20)));
        add("EDGE_RECTIFICATION","临边整改记录","INSPECTION","临边巡检","general_inspection_rectification","1=1","point_name","create_time","version",
                List.of(area("problem_desc","问题描述",2000),area("requirement","整改要求",2000),area("feedback","整改说明",2000),
                        user("assignee_id","整改负责人","EDGE_INSPECTION_RECTIFY","assignee_name"),date("deadline","整改期限",true)),
                List.of("status","task_id","reviewer_name","review_time","completed_time","close_time"),List.of(),
                List.of(slot("photos","整改照片","JSON","rectification_photo_file_ids","EDGE_INSPECTION_RECTIFICATION","IMAGE",0,20)));
        add("QUALITY_WEEKLY","质量周检","QUALITY","质量周检","quality_weekly_inspection","1=1","inspection_no","inspection_date","version",
                List.of(date("inspection_date","检查日期",true),area("conclusion","检查结论",1000)),
                List.of("status","week_start","created_by_name","submitted_by_name","submitted_time","submitted_issue_count"),
                List.of(child("draftItems","已有草稿问题","quality_weekly_inspection_draft_item","inspection_id","1=1",List.of(text("title","问题标题",200,true),text("location","位置",200,true),area("description","问题描述",1000),choice("severity","严重程度","NORMAL","WARNING","DANGER"),user("assignee_id","整改负责人","quality.rectify","assignee_name"),date("deadline","整改期限",true)),"item_order")),
                List.of(slot("photos","周检现场照片","BUSINESS","","QUALITY_WEEKLY_PHOTO","IMAGE",0,20)));
        add("QUALITY_ISSUE","质量问题与整改","QUALITY","质量周检","quality_issue","deleted=0","issue_no","record_date","version",
                List.of(text("title","问题标题",200,true),text("location","问题位置",200,true),area("description","问题描述",2000),
                        choice("severity","严重程度","NORMAL","WARNING","DANGER"),user("assignee_id","整改负责人","quality.rectify","assignee_name"),
                        date("deadline","整改期限",true),date("record_date","业务日期",true),area("rectification_description","整改说明",2000)),
                List.of("status","weekly_inspection_id","created_by_name","rectified_time","reviewer_name","review_time"),List.of(),
                List.of(slot("before","问题照片","BUSINESS","","QUALITY_ISSUE","IMAGE",1,20),slot("after","整改照片","JSON","rectification_photo_file_ids","QUALITY_RECTIFICATION","IMAGE",0,20)));
        add("QUALITY_DOCUMENT","质量资料","QUALITY","质量周检","file_resource","deleted=0 AND business_type='QUALITY_DOCUMENT' AND status<>'PENDING_DELETE'",
                "file_name","create_time",null,List.of(text("file_name","文件名称",200,true),area("remark","说明",500)),List.of("uploader_id"),List.of(),
                List.of(slot("file","资料文件","RESOURCE","","QUALITY_DOCUMENT","DOCUMENT",1,1)));
        add("COMMITTEE","安委会巡检","SAFETY_COMMITTEE","安委会巡检","safety_committee_record","1=1","category","inspected_at","version",
                List.of(new Field("category","隐患分类","ENUM",true,50,com.example.siteplatform.safetycommittee.CommitteeService.CATEGORIES,null,null,false),area("conclusion","检查结论",2000),
                        user("inspector_id","检查人","safety_committee.submit","inspector_name"),datetime("inspected_at","检查时间")),
                List.of("create_time"),List.of(),List.of(slot("files","巡检附件","COMMITTEE","","COMMITTEE_INSPECTION_ATTACHMENT","COMMITTEE",0,30)));
        var profile = new ArrayList<Field>(List.of(text("project_name","项目名称",200,true),text("short_name","项目简称",12,true),
                text("manager","项目经理",50,false),text("manager_phone","联系电话",30,false),text("contractor","施工单位",200,false),
                area("description","项目说明",2000),date("start_date","计划开始日期",false),date("end_date","计划结束日期",false),
                date("actual_start_date","实际开始日期",false),date("actual_end_date","实际结束日期",false)));
        for (String[] pair : List.of(new String[]{"phase","工程阶段"},new String[]{"safety_goal","安全目标"},new String[]{"quality_goal","质量目标"},
                new String[]{"owner_unit","建设单位"},new String[]{"supervision_unit","监理单位"},new String[]{"design_unit","设计单位"},
                new String[]{"direct_company","直属单位"},new String[]{"engineering_type","工程类型"},new String[]{"project_scale","项目规模"},
                new String[]{"project_target","项目目标"},new String[]{"green_construction_goal","绿色施工目标"})) profile.add(text(pair[0],pair[1],200,false));
        for(String[] pair:List.of(new String[]{"space_capacity","空间容量"},new String[]{"contractor_license_number","施工许可证"},new String[]{"general_contract_number","总承包合同编号"},new String[]{"project_classification","项目分类"},new String[]{"investment_entity","投资主体"},new String[]{"contracting_mode","承建模式"},new String[]{"project_category","项目类别"},new String[]{"project_level","项目级别"}))profile.add(text(pair[0],pair[1],100,false));
        profile.add(text("contractor_credit_code","施工单位统一社会信用代码",32,false));profile.add(text("fixed_ip_address","固定 IP",45,false));
        for(String[] pair:List.of(new String[]{"contract_amount","合同金额"},new String[]{"building_area","建筑面积"},new String[]{"land_area","用地面积"},new String[]{"building_height","建筑高度"},new String[]{"excavation_depth","开挖深度"}))profile.add(new Field(pair[0],pair[1],"DECIMAL",false,0,List.of(),null,null,false));
        for(String[] pair:List.of(new String[]{"underground_floor_count","地下层数"},new String[]{"aboveground_floor_count","地上层数"},new String[]{"management_staff_count","管理人数"},new String[]{"attendance_count","考勤人数"},new String[]{"party_member_count","党员人数"}))profile.add(number(pair[0],pair[1],0,999999));
        add("PROJECT","项目档案",null,"项目信息","project_info","deleted=0","project_name","create_time","profile_version",profile,
                List.of("project_status"),List.of(),List.of(slot("images","效果图","BUSINESS","","PROJECT_PROFILE_IMAGE","PROFILE",1,20),slot("route","到访路线图","BUSINESS","","PROJECT_ROUTE_IMAGE","PROFILE",0,1)));
        add("PROJECT_LOCATION","项目定位",null,"项目信息","project_info","deleted=0","project_name","create_time","profile_version",
                List.of(text("address","地址及到访说明",500,true),decimal("longitude","经度"),decimal("latitude","纬度"),choice("coordinate_type","坐标来源","BD09","GCJ02","WGS84")),
                List.of("project_status"),List.of(),List.of());
    }
    public Type require(String code) { Type t=types.get(code); if(t==null) throw new BusinessException("不支持的数据类型"); return t; }
    public Collection<Type> all() { return Collections.unmodifiableCollection(types.values()); }
    private void add(String code,String label,String module,String group,String table,String predicate,String title,String date,String version,
                     List<Field> fields,List<String> readOnly,List<Child> children,List<Slot> slots) {
        types.put(code,new Type(code,label,module,group,table,predicate,title,date,version,true,List.copyOf(fields),List.copyOf(readOnly),children,slots));
    }
    private static Field text(String key,String label,int max,boolean required) { return new Field(key,label,"TEXT",required,max,List.of(),null,null,false); }
    private static Field area(String key,String label,int max) { return new Field(key,label,"TEXTAREA",false,max,List.of(),null,null,false); }
    private static Field phone(String key,String label) { return new Field(key,label,"PHONE",false,30,List.of(),null,null,true); }
    private static Field user(String key,String label,String permission,String name) { return new Field(key,label,"USER",true,0,List.of(),permission,name,false); }
    private static Field date(String key,String label,boolean required) { return new Field(key,label,"DATE",required,0,List.of(),null,null,false); }
    private static Field datetime(String key,String label) { return new Field(key,label,"DATETIME",true,0,List.of(),null,null,false); }
    private static Field choice(String key,String label,String... options) { return new Field(key,label,"ENUM",true,100,List.of(options),null,null,false); }
    private static Field number(String key,String label,int min,int max) { return new Field(key,label,"INTEGER",true,0,List.of(""+min,""+max),null,null,false); }
    private static Field decimal(String key,String label) { return new Field(key,label,"DECIMAL",true,0,List.of(),null,null,false); }
    private static Child child(String key,String label,String table,String parent,String predicate,List<Field> fields,String... readOnly) { return new Child(key,label,table,parent,predicate,fields,List.of(readOnly)); }
    private static Slot slot(String key,String label,String mode,String column,String business,String policy,int min,int max) { return new Slot(key,label,mode,column,business,policy,min,max); }
}
