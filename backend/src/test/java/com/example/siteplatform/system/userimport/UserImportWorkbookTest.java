package com.example.siteplatform.system.userimport;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.system.entity.SystemRole;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UserImportWorkbookTest {
    final UserImportWorkbook service = new UserImportWorkbook();
    ProjectInfo project() { ProjectInfo p = new ProjectInfo(); p.setId(7L); p.setProjectName("合成测试项目"); return p; }
    SystemRole role() { SystemRole r = new SystemRole(); r.setId(8L); r.setRoleName("合成项目角色"); return r; }
    byte[] populated(int count) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template(List.of(project()), List.of(role()))))) {
            var sheet = book.getSheet("用户名单");
            for (int i = 0; i < count; i++) {
                var row = sheet.createRow(i + 1); row.createCell(0).setCellValue("合成人员" + i);
                row.createCell(1).setCellValue(String.format("19999%06d", i));
                row.createCell(2).setCellValue(UserImportWorkbook.projectLabel(project())); row.createCell(3).setCellValue(UserImportWorkbook.roleLabel(role()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(); book.write(out); return out.toByteArray();
        }
    }
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "users.xlsx", "application/octet-stream", bytes); }
    @Test void templateHasStableSelectionsTextPhoneAndNoAccounts() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template(List.of(project()), List.of(role()))))) {
            assertThat(book.getSheet("用户名单").getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(book.getSheet("用户名单").getDataValidations()).hasSize(2);
            assertThat(book.getSheet("项目参考").getRow(1).getCell(0).getStringCellValue()).endsWith("[P:7]");
            assertThat(book.getSheet("用户名单").getColumnStyle(1).getDataFormatString()).isEqualTo("@");
        }
    }
    @Test void accepts500PeopleAndRejects501() throws Exception {
        assertThat(service.parse(file(populated(500)))).hasSize(500);
        assertThatThrownBy(() -> service.parse(file(populated(501)))).isInstanceOf(BusinessException.class).hasMessageContaining("500人");
    }
    @Test void acceptsNumericPhoneWithoutScientificNotation() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(populated(1)))) {
            book.getSheet("用户名单").getRow(1).getCell(1).setCellValue(19999000000d);
            var out = new ByteArrayOutputStream(); book.write(out);
            assertThat(service.parse(file(out.toByteArray())).get(0).getPhone()).isEqualTo("19999000000");
        }
    }
    @Test void rejectsFormulaEvenInReferenceSheet() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(populated(1)))) {
            book.getSheet("角色参考").createRow(2).createCell(0).setCellFormula("1+1");
            var out = new ByteArrayOutputStream(); book.write(out);
            assertThatThrownBy(() -> service.parse(file(out.toByteArray()))).hasMessageContaining("公式");
        }
    }
    @Test void rejectsRenamedDamagedOversizedAndExtraPasswordColumn() throws Exception {
        assertThatThrownBy(() -> service.parse(file("not a zip".getBytes()))).hasMessageContaining("格式");
        assertThatThrownBy(() -> service.parse(file(new byte[5 * 1024 * 1024 + 1]))).hasMessageContaining("5MiB");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(populated(1)))) {
            book.getSheet("用户名单").getRow(1).createCell(4).setCellValue("不要填写密码");
            var out = new ByteArrayOutputStream(); book.write(out);
            assertThatThrownBy(() -> service.parse(file(out.toByteArray()))).hasMessageContaining("四列");
        }
    }
    @Test void rowLimitIsIndependentOfPersonCount() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(populated(1)))) {
            book.getSheet("用户名单").createRow(5001).createCell(0).setCellValue("多余行");
            var out = new ByteArrayOutputStream(); book.write(out);
            assertThatThrownBy(() -> service.parse(file(out.toByteArray()))).hasMessageContaining("5000行");
        }
    }
    @Test void handoutTreatsFormulaLikeTextAsLiteral() throws Exception {
        byte[] bytes = service.credentials(List.of(List.of("=1+1", "19999000000", "sample", "2026-10-14 12:00:00", "说明")));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        }
    }
    @Test void encryptionIsAuthenticatedAndUnavailableWithoutIndependentKey() {
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        var cipher = new UserImportCredentialCipher(Base64.getEncoder().encodeToString(key));
        String secret = UUID.randomUUID().toString(); String encrypted = cipher.encrypt(secret, "item:user:version");
        assertThat(encrypted).doesNotContain(secret);
        assertThat(cipher.decrypt(encrypted, "item:user:version")).isEqualTo(secret);
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "other:user:version")).hasMessageContaining("不可用");
        assertThatThrownBy(() -> new UserImportCredentialCipher("").requireConfigured()).hasMessageContaining("密钥");
    }
}
