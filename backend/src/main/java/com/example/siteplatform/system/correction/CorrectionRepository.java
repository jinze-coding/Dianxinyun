package com.example.siteplatform.system.correction;

import com.example.siteplatform.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class CorrectionRepository {
    final JdbcTemplate jdbc;
    final ObjectMapper json;
    private final Map<String,List<Map<String,Object>>> columnCache=new java.util.concurrent.ConcurrentHashMap<>();
    public void validateColumns(String table,Map<String,Object> values) {
        var columns=columnCache.computeIfAbsent(table,t->rows("SELECT COLUMN_NAME,CHARACTER_MAXIMUM_LENGTH,NUMERIC_PRECISION,NUMERIC_SCALE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",t));
        for(var entry:values.entrySet()) {
            var column=columns.stream().filter(c->entry.getKey().equals(c.get("column_name"))).findFirst().orElseThrow(()->new IllegalStateException("Unknown catalog field"));
            if(entry.getValue()==null && "NO".equals(column.get("is_nullable")))throw new BusinessException("该字段不能为空："+entry.getKey());
            if(entry.getValue() instanceof String v && column.get("character_maximum_length") instanceof Number max && v.codePointCount(0,v.length())>max.longValue())throw new BusinessException("该字段内容过长："+entry.getKey());
            if(entry.getValue() instanceof java.math.BigDecimal v && column.get("numeric_precision") instanceof Number precision && column.get("numeric_scale") instanceof Number scale && (v.scale()>scale.intValue() || v.precision()-v.scale()>precision.intValue()-scale.intValue()))throw new BusinessException("数值超出允许精度："+entry.getKey());
        }
    }
    public JdbcTemplate jdbc() { return jdbc; }
    public Map<String,Object> one(String sql,Object... args) {
        List<Map<String,Object>> rows=rows(sql,args);
        if(rows.isEmpty()) throw BusinessException.notFound("记录不存在或已删除");
        if(rows.size()!=1) throw BusinessException.of(409,"记录关系异常，请刷新");
        return rows.get(0);
    }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            var row=new LinkedHashMap<String,Object>(); var md=rs.getMetaData();
            for(int i=1;i<=md.getColumnCount();i++) {
                Object value=rs.getObject(i);
                if(value instanceof Timestamp v) value=v.toLocalDateTime().toString();
                if(value instanceof java.sql.Date v) value=v.toLocalDate().toString();
                if(value instanceof LocalDateTime || value instanceof LocalDate) value=value.toString();
                if(value instanceof Boolean v) value=v?1:0;
                if(value instanceof byte[] v) value=HexFormat.of().formatHex(v);
                row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT),value);
            }
            return row;
        },args);
    }
    public long count(String sql,Object...args) { Long count=jdbc.queryForObject(sql,Long.class,args); return count==null?0:count; }
    public void changed(int count) { if(count!=1) throw BusinessException.of(409,"记录已发生变化，请重新核对"); }
    public long insert(String sql,Object...args) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        changed(jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]); return statement;},key));
        if(key.getKey()==null) throw new IllegalStateException("Missing inserted key"); return key.getKey().longValue();
    }
    /** Identifiers exclusively originate from CorrectionCatalog or hardcoded adapter logic. */
    public void update(String table,long id,Map<String,Object> values) {
        if(values.isEmpty()) return;
        var args=new ArrayList<>(values.values()); args.add(id);
        changed(jdbc.update("UPDATE "+table+" SET "+String.join(",",values.keySet().stream().map(k->k+"=?").toList())+" WHERE id=?",args.toArray()));
    }
    public String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Correction serialization failed");} }
    public <T> T decode(String value,Class<T> type) { try{return json.readValue(value,type);}catch(Exception e){throw BusinessException.of(409,"纠错信息已失效，请重新打开");} }
    public Map<String,Object> object(String value) { try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Correction journal cannot be read");} }
    public List<Long> ids(Object value) {
        if(value==null || value.toString().isBlank()) return List.of();
        try {
            if(value instanceof List<?> list) return list.stream().map(CorrectionRepository::id).toList();
            String str=value.toString().trim();
            if(str.startsWith("[")) return json.readValue(str,new TypeReference<List<Long>>(){});
            return Arrays.stream(str.split(",")).map(String::trim).filter(s->!s.isEmpty()).map(Long::valueOf).toList();
        }catch(Exception e){throw BusinessException.of(409,"原记录的附件关联不完整，请先核对原数据");}
    }
    public static long id(Object value) {
        try { long id=Long.parseLong(String.valueOf(value)); if(id<=0)throw new NumberFormatException(); return id; }
        catch(Exception e){throw new BusinessException("记录编号不正确");}
    }
    public static String string(Object v) { return v==null?"":v.toString(); }
    public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
}
