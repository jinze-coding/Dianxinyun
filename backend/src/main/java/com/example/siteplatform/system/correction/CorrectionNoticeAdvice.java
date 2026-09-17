package com.example.siteplatform.system.correction;

import com.example.siteplatform.common.Result;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.*;

/** Decorates already-authorized business responses. Only a correction timestamp is exposed, never the administrator journal. */
@ControllerAdvice
@RequiredArgsConstructor
public class CorrectionNoticeAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper json;
    private final CorrectionNoticeService notices;
    private static final Map<String,String> TYPES=Map.ofEntries(
        Map.entry("SiteVisitInvitationVO","INVITATION"),Map.entry("SiteMeetingVisitRegistrationVO","MEETING_REGISTRATION"),Map.entry("SiteGuardVisitRegistrationVO","GUARD_REGISTRATION"),
        Map.entry("ProjectDocumentVO","DOCUMENT"),Map.entry("ProjectDocumentDetailVO","DOCUMENT"),Map.entry("DocumentFolderVO","DOCUMENT_FOLDER"),
        Map.entry("DocumentIncomingBatchVO","DOCUMENT_INCOMING"),Map.entry("DocumentDistributionBatchVO","DOCUMENT_DISTRIBUTION"),Map.entry("SealApplicationVO","SEAL_APPLICATION"),
        Map.entry("ElectricBox","ELECTRIC_BOX"),Map.entry("ElectricBoxVO","ELECTRIC_BOX"),Map.entry("InspectionRecordVO","ELECTRIC_INSPECTION"),Map.entry("InspectionRectificationVO","ELECTRIC_RECTIFICATION"),
        Map.entry("EdgeInspectionPointVO","EDGE_POINT"),Map.entry("GeneralInspectionTaskVO","EDGE_TASK"),Map.entry("GeneralInspectionRectificationVO","EDGE_RECTIFICATION"),
        Map.entry("QualityWeeklyInspectionVO","QUALITY_WEEKLY"),Map.entry("QualityIssueVO","QUALITY_ISSUE"),Map.entry("ProjectProfileDetailVO","PROJECT"),Map.entry("MeetingMaterial","MEETING_MATERIAL"));
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {
        String name=method.getContainingClass().getName();
        return name.startsWith("com.example.siteplatform.") && !name.contains(".system.") && !name.contains(".auth.");
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(!(body instanceof Result<?> result)||!Integer.valueOf(200).equals(result.getCode())||result.getData()==null)return body;
        JsonNode tree=json.valueToTree(body);decorate(result.getData(),tree.get("data"),new HashMap<>(),0);return tree;
    }
    private void decorate(Object value,JsonNode node,Map<String,String> cache,int depth) {
        if(value==null||node==null||depth>10)return;
        if(value instanceof Collection<?> list && node.isArray()) {int i=0;for(Object v:list)decorate(v,node.get(i++),cache,depth+1);return;}
        if(value instanceof Map<?,?> map) {map.forEach((k,v)->decorate(v,node.get(String.valueOf(k)),cache,depth+1));return;}
        if(!value.getClass().getName().startsWith("com.example.siteplatform.")||!node.isObject())return;
        String type=TYPES.get(value.getClass().getSimpleName());
        if(value instanceof com.example.siteplatform.file.entity.FileResource file && "QUALITY_DOCUMENT".equals(file.getBusinessType()))type="QUALITY_DOCUMENT";
        if(value.getClass().getName().equals("com.example.siteplatform.safetycommittee.CommitteeService$RecordView"))type="COMMITTEE";
        if("INVITATION".equals(type))type="MEETING".equals(node.path("inviteType").asText())?"MEETING":"SINGLE_VISIT";
        boolean edgeItem=value.getClass().getName().equals("com.example.siteplatform.inspection.general.vo.EdgeInspectionRectificationSheetVO$Item");
        if(edgeItem)type="EDGE_RECTIFICATION";
        if(type!=null) {
            long id=node.path(edgeItem?"rectificationId":type.equals("PROJECT")?"projectId":"id").asLong();
            if(id>0) {String code=type;String notice=cache.computeIfAbsent(type+":"+id,k->notices.text(code,id));if(!notice.isEmpty())((ObjectNode)node).put("correctionNotice",notice);}
        }
        for(var property:json.getSerializationConfig().introspect(json.constructType(value.getClass())).findProperties()) {
            if(property.getAccessor()!=null && node.has(property.getName()))decorate(property.getAccessor().getValue(value),node.get(property.getName()),cache,depth+1);
        }
        if(value.getClass().getSimpleName().equals("EdgeInspectionRectificationSheetVO")) {
            var messages=new LinkedHashSet<String>();for(JsonNode item:node.path("items"))if(item.has("correctionNotice"))messages.add(item.path("correctionNotice").asText());
            if(!messages.isEmpty())((ObjectNode)node).put("correctionNotice",String.join(" ",messages));
        }
        // Detail wrappers (documents, materials and edge sheets) inherit their displayed record's marker.
        for(String key:List.of("document","material","task","application"))if(node.path(key).has("correctionNotice"))((ObjectNode)node).set("correctionNotice",node.path(key).get("correctionNotice"));
    }
}
