package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.*;
import com.example.siteplatform.seal.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SealFormExportControllerTest {
    private final SealFormExportService jobs = mock(SealFormExportService.class);
    private final SealPdfService single = mock(SealPdfService.class);
    private final AuthService auth = mock(AuthService.class);
    private MockMvc mvc;
    private SysUser user;
    @BeforeEach void setup() {
        user = new SysUser(); user.setId(7L);
        when(auth.getCurrentUser("Bearer test")).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new SealFormExportController(jobs, auth),
                new SealApplicationExportController(single, auth))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void allExportRoutesKeepRealForbiddenStatus() throws Exception {
        when(jobs.create(any(), eq(user))).thenThrow(BusinessException.forbidden("没有导出用印申请单权限"));
        when(jobs.download(80L, user)).thenThrow(BusinessException.forbidden("没有导出用印申请单权限"));
        when(single.generate(eq(42L), eq(user), any())).thenThrow(BusinessException.forbidden("没有导出用印申请单权限"));
        mvc.perform(post("/api/v1/seal/applications/form-export-jobs").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"projectId":3,"selectionMode":"SELECTED","applicationIds":[42],"requestKey":"http-test"}
                  """)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        for (String path : new String[]{"42/form.pdf", "42/pdf", "form-export-jobs/80/download"})
            mvc.perform(get("/api/v1/seal/applications/" + path).header("Authorization", "Bearer test"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }
    @Test void malformedRequestsNeverCreateJobs() throws Exception {
        mvc.perform(post("/api/v1/seal/applications/form-export-jobs").header("Authorization", "Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                  {"projectId":3,"selectionMode":"UNKNOWN","requestKey":"http-test"}
                  """)).andExpect(status().isBadRequest());
        verifyNoInteractions(jobs);
    }
}
