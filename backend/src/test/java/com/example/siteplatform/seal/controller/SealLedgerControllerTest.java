package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.seal.service.LedgerDateRange;
import com.example.siteplatform.seal.service.SealLedgerService;
import com.example.siteplatform.seal.service.SealLedgerWordRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SealLedgerControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/seal/ledger/export", "/api/v1/seal/applications/export"})
    void bothRoutesReturnDocxWithDownloadHeaders(String path) throws Exception {
        var service = mock(SealLedgerService.class);
        var auth = mock(AuthService.class);
        var user = new SysUser();
        user.setId(7L);
        byte[] bytes = new SealLedgerWordRenderer().render(List.of(), Map.of());
        when(auth.getCurrentUser("Bearer test-token")).thenReturn(user);
        when(service.export(eq(9L), eq("DAY"), eq(LocalDate.of(2026, 9, 11)), isNull(), isNull(),
                isNull(), isNull(), same(user), any())).thenReturn(new SealLedgerService.LedgerExport(
                "用印台账_2026-09-11_2026-09-11.docx", bytes,
                new LedgerDateRange(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11))));
        var mvc = MockMvcBuilders.standaloneSetup(new SealLedgerController(service, auth)).build();
        mvc.perform(get(path).param("projectId", "9").param("period", "DAY").param("anchorDate", "2026-09-11")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(SealLedgerWordRenderer.CONTENT_TYPE))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".docx")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length)))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
