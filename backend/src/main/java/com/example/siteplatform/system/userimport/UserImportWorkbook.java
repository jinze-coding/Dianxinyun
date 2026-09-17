package com.example.siteplatform.system.userimport;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.system.entity.SystemRole;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class UserImportWorkbook {
    static final int MAX_ROWS = 5000;
    private static final String[] HEADERS = {"姓名", "手机号", "所属项目", "项目角色"};

    public byte[] template(List<ProjectInfo> projects, List<SystemRole> roles) {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet people = sheet(book, "用户名单", HEADERS);
            Sheet projectSheet = sheet(book, "项目参考", new String[]{"所属项目（复制或在名单中选择）"});
            for (int i = 0; i < projects.size(); i++) text(projectSheet.createRow(i + 1), 0, projectLabel(projects.get(i)));
            Sheet roleSheet = sheet(book, "角色参考", new String[]{"项目角色（复制或在名单中选择）"});
            for (int i = 0; i < roles.size(); i++) text(roleSheet.createRow(i + 1), 0, roleLabel(roles.get(i)));
            addDropdown(book, people, "ProjectOptions", "项目参考", projects.size(), 2);
            addDropdown(book, people, "RoleOptions", "角色参考", roles.size(), 3);
            CellStyle phoneStyle = book.createCellStyle(); phoneStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            people.setDefaultColumnStyle(1, phoneStyle);
            Sheet help = sheet(book, "填写说明", new String[]{"填写说明"});
            String[] lines = {
                "在“用户名单”填写，姓名、手机号、所属项目、项目角色均必填。每批最多500人、5000行。",
                "同一人多个项目或角色填写多行；姓名与手机号保持一致。项目和角色选择参考表中的完整选项。",
                "手机号是登录账号，请保持文本格式，不填写密码。已有账号只跳过，不会更新授权。",
                "错误必须修正后整批重新校验。待审核申请由管理员先在注册审核中处理。",
                "确认导入时由管理员设置本批统一临时密码，30天内首次登录改密，然后在小程序自行绑定微信。",
                "账号发放表仅24小时可下载，请将每个人的凭据分别发给本人，不要向用户分发整份表。"
            };
            for (int i = 0; i < lines.length; i++) text(help.createRow(i + 1), 0, lines[i]);
            help.setColumnWidth(0, 120 * 256);
            return bytes(book);
        } catch (IOException e) { throw new BusinessException("模板生成失败"); }
    }

    public List<UserImportItem> parse(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("请上传5MiB以内的.xlsx名单");
        }
        try {
            byte[] source = file.getBytes(); validateZip(source);
            try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(source))) {
                if (book.getNumberOfSheets() < 1 || book.getNumberOfSheets() > 4 || !book.getExternalLinksTable().isEmpty()) {
                    throw new BusinessException("请使用系统模板，文件不能包含外部链接");
                }
                int cells = 0;
                for (Sheet s : book) for (Row row : s) for (Cell c : row) {
                    if (++cells > 80000 || c.getCellType() == CellType.FORMULA) throw new BusinessException("名单不能包含公式或超量单元格");
                }
                Sheet sheet = book.getSheet("用户名单");
                if (sheet == null) throw new BusinessException("缺少“用户名单”工作表，请使用系统模板");
                if (sheet.getLastRowNum() > MAX_ROWS) throw new BusinessException("每批最多5000行，请删除多余行");
                Row header = sheet.getRow(0);
                for (int c = 0; c < HEADERS.length; c++) if (!HEADERS[c].equals(value(header, c))) {
                    throw new BusinessException("名单表头必须依次为姓名、手机号、所属项目、项目角色");
                }
                List<UserImportItem> result = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    String name = value(row, 0), phone = value(row, 1), project = value(row, 2), role = value(row, 3);
                    if ((name + phone + project + role).isEmpty()) continue;
                    if (name.length() > 50 || phone.length() > 20 || project.length() > 300 || role.length() > 150) {
                        throw new BusinessException("第" + (i + 1) + "行字段过长，请修正后重新上传");
                    }
                    if (row != null) for (Cell c : row) if (c.getColumnIndex() > 3 && !value(row, c.getColumnIndex()).isEmpty()) {
                        throw new BusinessException("名单只能包含模板中的四列，不要填写密码或其他信息");
                    }
                    UserImportItem item = new UserImportItem(); item.setRowNumber(i + 1); item.setRealName(name);
                    item.setPhone(phone); item.setProjectLabel(project); item.setRoleLabel(role);
                    result.add(item);
                }
                if (result.isEmpty()) throw new BusinessException("名单为空，请先填写人员信息");
                if (result.stream().map(UserImportItem::getPhone).distinct().count() > 500) throw new BusinessException("每批最多500人");
                return result;
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("文件损坏或不是有效的.xlsx名单，请使用模板重新保存"); }
    }

    private void validateZip(byte[] source) throws IOException {
        if (source.length < 4 || source[0] != 'P' || source[1] != 'K' || source[2] != 3 || source[3] != 4) throw new BusinessException("文件格式与扩展名不符");
        int entries = 0; long total = 0; boolean workbook = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source))) {
            java.util.zip.ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 500) throw new BusinessException("文件结构过于复杂，请使用系统模板");
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject") || name.contains("externallinks/") || name.contains("embeddings/") || name.contains("connections.xml")) throw new BusinessException("名单不允许宏、嵌入对象或外部数据连接");
                if (name.equals("xl/workbook.xml")) workbook = true;
                int read; ByteArrayOutputStream types = name.equals("[content_types].xml") ? new ByteArrayOutputStream() : null;
                while ((read = zip.read(buffer)) != -1) {
                    total += read; if (total > 20L * 1024 * 1024) throw new BusinessException("文件解压后过大，请使用系统模板");
                    if (types != null) types.write(buffer, 0, read);
                }
                if (types != null && types.toString(java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("macroenabled")) throw new BusinessException("名单不允许包含宏");
            }
        }
        if (!workbook) throw new BusinessException("文件不是有效的Excel工作簿");
    }

    public byte[] credentials(List<List<String>> values) {
        try (XSSFWorkbook book = new XSSFWorkbook()) {
            Sheet sheet = sheet(book, "账号发放表", new String[]{"姓名", "手机号 / 登录账号", "临时密码", "密码失效时间（北京时间）", "登录说明"});
            for (int i = 0; i < values.size(); i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < values.get(i).size(); c++) text(row, c, values.get(i).get(c));
            }
            sheet.setColumnWidth(3, 30 * 256); sheet.setColumnWidth(4, 85 * 256);
            return bytes(book);
        } catch (IOException e) { throw new BusinessException("账号发放表生成失败"); }
    }

    public static String projectLabel(ProjectInfo project) { return project.getProjectName() + " [P:" + project.getId() + "]"; }
    public static String roleLabel(SystemRole role) { return role.getRoleName() + " [R:" + role.getId() + "]"; }
    private String value(Row row, int column) {
        Cell c = row == null ? null : row.getCell(column);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC) return BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
        if (c.getCellType() == CellType.ERROR) throw new BusinessException("名单中存在错误单元格");
        return new DataFormatter(Locale.ROOT).formatCellValue(c).trim();
    }
    private Sheet sheet(XSSFWorkbook book, String name, String[] headers) {
        Sheet sheet = book.createSheet(name); sheet.createFreezePane(0, 1);
        Row row = sheet.createRow(0); row.setHeightInPoints(28);
        CellStyle style = book.createCellStyle(); style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); style.setFont(font);
        for (int i = 0; i < headers.length; i++) { text(row, i, headers[i]); row.getCell(i).setCellStyle(style); sheet.setColumnWidth(i, (i >= 2 ? 45 : 24) * 256); }
        return sheet;
    }
    private void addDropdown(XSSFWorkbook book, Sheet target, String range, String sheet, int size, int column) {
        if (size == 0) return;
        Name name = book.createName(); name.setNameName(range); name.setRefersToFormula("'" + sheet + "'!$A$2:$A$" + (size + 1));
        DataValidationHelper helper = target.getDataValidationHelper();
        DataValidation validation = helper.createValidation(helper.createFormulaListConstraint(range), new CellRangeAddressList(1, MAX_ROWS, column, column));
        validation.setShowErrorBox(true); validation.createErrorBox("请选择现有选项", "请从下拉列表或参考表复制完整项目、角色选项"); target.addValidationData(validation);
    }
    private static void text(Row row, int column, String value) { row.createCell(column, CellType.STRING).setCellValue(value); }
    private byte[] bytes(XSSFWorkbook book) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); book.write(out); return out.toByteArray(); }
}
