package com.example.siteplatform.system.correction;

import com.example.siteplatform.file.entity.FileResource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Adds correction-only files to the same business deletion preview and transaction. Journal text remains immutable. */
@Component
@RequiredArgsConstructor
public class CorrectionDeletionSupport {
    private final JdbcTemplate jdbc;
    public List<FileResource> files(String type,long id) {
        var args=new ArrayList<Object>();String scope;
        switch(type) {
            case "PROJECT", "USER", "ROLE", "REGISTRATION_APPLICATION" -> {return List.of();} // Project deletion already includes every project file.
            case "DOCUMENT_FOLDER" -> {
                List<Long> folders=new ArrayList<>();folders.add(id);
                for(int i=0;i<folders.size();i++)for(Long child:jdbc.queryForList("SELECT id FROM document_folder WHERE parent_id=?",Long.class,folders.get(i)))if(!folders.contains(child))folders.add(child);
                String placeholders=String.join(",",Collections.nCopies(folders.size(),"?"));
                scope="(a.target_type='DOCUMENT_FOLDER' AND a.target_id IN ("+placeholders+")) OR (a.target_type='DOCUMENT' AND a.target_id IN (SELECT id FROM project_document WHERE folder_id IN ("+placeholders+")))";args.addAll(folders);args.addAll(folders);
            }
            case "ELECTRIC_BOX" -> {scope="(a.target_type='ELECTRIC_BOX' AND a.target_id=?) OR (a.target_type='ELECTRIC_INSPECTION' AND a.target_id IN (SELECT id FROM inspection_record WHERE electric_box_id=?)) OR (a.target_type='ELECTRIC_RECTIFICATION' AND a.target_id IN (SELECT id FROM inspection_rectification WHERE electric_box_id=?))";args.addAll(List.of(id,id,id));}
            case "INSPECTION_RECORD" -> {scope="(a.target_type='ELECTRIC_INSPECTION' AND a.target_id=?) OR (a.target_type='ELECTRIC_RECTIFICATION' AND a.target_id IN (SELECT id FROM inspection_rectification WHERE inspection_record_id=?))";args.addAll(List.of(id,id));}
            case "SITE_ACCESS_INVITATION" -> {scope="(a.target_type IN ('SINGLE_VISIT','MEETING') AND a.target_id=?) OR (a.target_type='MEETING_REGISTRATION' AND a.target_id IN (SELECT id FROM site_meeting_visit_registration WHERE invitation_id=?)) OR (a.target_type='MEETING_MATERIAL' AND a.target_id IN (SELECT id FROM site_meeting_material WHERE invitation_id=?))";args.addAll(List.of(id,id,id));}
            default -> {
                String target=switch(type){case "PROJECT_DOCUMENT"->"DOCUMENT";case "FILE"->"QUALITY_DOCUMENT";case "COMMITTEE_INSPECTION"->"COMMITTEE";case "MEETING_MATERIAL","QUALITY_ISSUE"->type;default->null;};
                if(target==null)return List.of();scope="a.target_type=? AND a.target_id=?";args.add(target);args.add(id);
            }
        }
        return jdbc.query("SELECT DISTINCT f.* FROM file_resource f JOIN sys_data_correction_attachment a ON (f.id=a.file_id OR f.id=a.original_file_id) WHERE f.business_type LIKE 'DATA\\_CORRECTION\\_%' AND ("+scope+")",new BeanPropertyRowMapper<>(FileResource.class),args.toArray());
    }
}
