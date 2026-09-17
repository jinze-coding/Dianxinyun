package com.example.siteplatform.system.correction;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CorrectionNoticeService {
    private final JdbcTemplate jdbc;
    public String text(String type,Long id) {
        if(id==null)return "";
        String predicate="target_type=? AND target_id=?";var args=new ArrayList<Object>(List.of(type,id));
        if(type.equals("PROJECT"))predicate="target_type IN ('PROJECT','PROJECT_LOCATION') AND target_id=?";
        if(type.equals("PROJECT"))args=new ArrayList<>(List.of(id));
        if(type.equals("QUALITY_ISSUE")) {predicate="("+predicate+") OR (target_type='QUALITY_WEEKLY' AND target_id=(SELECT weekly_inspection_id FROM quality_issue WHERE id=?))";args.add(id);}
        var rows=jdbc.queryForList("SELECT create_time FROM sys_data_correction_log WHERE "+predicate+" ORDER BY id DESC LIMIT 1",args.toArray());
        return rows.isEmpty()?"":"本记录已由管理员纠错（"+rows.get(0).get("create_time").toString().replaceAll("\\.0$","")+"），原流程状态与实际操作事件保留。";
    }
    public String append(String text,String type,Long id) {String notice=text(type,id);return (text==null?"":text)+(notice.isEmpty()?"":"\n"+notice);}
}
