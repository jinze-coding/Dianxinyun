package com.example.siteplatform.system.security;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.common.GlobalExceptionHandler;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.service.SystemPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessModulePermissionInterceptorTest {

    private AuthService authService;
    private SystemPermissionService permissionService;
    private FileResourceMapper fileMapper;
    private BusinessModulePermissionInterceptor interceptor;
    private MockMvc mockMvc;
    private SysUser user;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        permissionService = mock(SystemPermissionService.class);
        fileMapper = mock(FileResourceMapper.class);
        interceptor = new BusinessModulePermissionInterceptor(authService, permissionService, fileMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new GuardTestController())
                .addInterceptors(interceptor)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        user = new SysUser();
        user.setId(9L);
        when(authService.getCurrentUser(anyString())).thenReturn(user);
    }

    @ParameterizedTest
    @MethodSource("permissionMappings")
    void resolvesFormalModulePermission(String method, String path, String expectedPermission) {
        assertThat(interceptor.resolveStaticPermission(method, path)).isEqualTo(expectedPermission);
    }

    static Stream<Arguments> permissionMappings() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/project-documents", SystemPermissionCodes.DOCUMENT_VIEW),
                Arguments.of("HEAD", "/api/v1/document-folders", SystemPermissionCodes.DOCUMENT_VIEW),
                Arguments.of("POST", "/api/v1/project-documents", SystemPermissionCodes.DOCUMENT_UPLOAD),
                Arguments.of("POST", "/api/v1/project-documents/21/versions", SystemPermissionCodes.DOCUMENT_UPLOAD),
                Arguments.of("PUT", "/api/v1/project-documents/21", SystemPermissionCodes.DOCUMENT_MANAGE),
                Arguments.of("POST", "/api/v1/document-folders", SystemPermissionCodes.DOCUMENT_MANAGE),
                Arguments.of("GET", "/api/v1/inspection/records", SystemPermissionCodes.INSPECTION_VIEW),
                Arguments.of("GET", "/api/v1/inspection/records/export", SystemPermissionCodes.INSPECTION_EXPORT),
                Arguments.of("POST", "/api/v1/inspection/records", SystemPermissionCodes.INSPECTION_SUBMIT),
                Arguments.of("POST", "/api/v1/inspection/records/8/submit", SystemPermissionCodes.INSPECTION_SUBMIT),
                Arguments.of("PUT", "/api/v1/inspection/settings/2", SystemPermissionCodes.INSPECTION_MANAGE),
                Arguments.of("GET", "/api/v1/electric-boxes/7", SystemPermissionCodes.INSPECTION_VIEW),
                Arguments.of("POST", "/api/v1/electric-boxes/import", SystemPermissionCodes.INSPECTION_MANAGE),
                Arguments.of("GET", "/api/v1/quality/issues", SystemPermissionCodes.QUALITY_VIEW),
                Arguments.of("POST", "/api/v1/quality/issues", SystemPermissionCodes.QUALITY_MANAGE),
                Arguments.of("POST", "/api/v1/quality/issues/3/assign", SystemPermissionCodes.QUALITY_MANAGE),
                Arguments.of("POST", "/api/v1/quality/issues/3/rectify", SystemPermissionCodes.QUALITY_RECTIFY),
                Arguments.of("POST", "/api/v1/quality/issues/3/review", SystemPermissionCodes.QUALITY_REVIEW)
        );
    }

    @Test
    void directRequestWithoutPermissionReturnsHttp403() throws Exception {
        when(permissionService.hasPermission(9L, SystemPermissionCodes.DOCUMENT_VIEW)).thenReturn(false);

        mockMvc.perform(get("/api/v1/project-documents")
                        .header("Authorization", "Bearer denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无操作权限：document.view"));
    }

    @Test
    void missingOrInvalidSessionReturnsRealHttp401() throws Exception {
        when(authService.getCurrentUser(anyString()))
                .thenThrow(BusinessException.unauthorized("未登录"));

        mockMvc.perform(get("/api/v1/inspection/records")
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void platformAdministratorBypassFromPermissionServiceIsAccepted() throws Exception {
        when(permissionService.hasPermission(9L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(true);

        mockMvc.perform(get("/api/v1/quality/issues")
                        .header("Authorization", "Bearer platform-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(permissionService).hasPermission(9L, SystemPermissionCodes.QUALITY_VIEW);
    }

    @Test
    void sharedQualityFileDownloadRequiresQualityView() throws Exception {
        FileResource qualityFile = new FileResource();
        qualityFile.setId(18L);
        qualityFile.setBusinessType("QUALITY_REVIEW");
        when(fileMapper.selectById(18L)).thenReturn(qualityFile);
        when(permissionService.hasPermission(9L, SystemPermissionCodes.QUALITY_VIEW)).thenReturn(false);

        mockMvc.perform(get("/api/v1/files/18/download")
                        .header("Authorization", "Bearer denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无操作权限：quality.view"));
    }

    @Test
    void publicAndAuthenticationPathsHaveNoModulePermissionMapping() {
        assertThat(interceptor.resolveStaticPermission("GET", "/api/v1/public/electric-boxes/X/summary")).isNull();
        assertThat(interceptor.resolveStaticPermission("GET", "/api/v1/scan/electric-boxes/X")).isNull();
        assertThat(interceptor.resolveStaticPermission("POST", "/api/v1/auth/login")).isNull();
        assertThat(interceptor.resolveStaticPermission("POST", "/api/v1/registration-applications")).isNull();
        assertThat(interceptor.resolveStaticPermission("POST", "/api/v1/auth/web-qr/challenges")).isNull();
    }

    @RestController
    static class GuardTestController {
        @RequestMapping(
                value = {
                        "/api/v1/project-documents",
                        "/api/v1/inspection/records",
                        "/api/v1/quality/issues",
                        "/api/v1/files/{id}/download"
                },
                method = {RequestMethod.GET, RequestMethod.HEAD}
        )
        Result<String> guarded(@PathVariable(required = false) Long id) {
            return Result.success("ok");
        }

        @GetMapping("/test/errors/{code}")
        Result<Void> error(@PathVariable Integer code) {
            throw BusinessException.of(code, "test");
        }
    }
}
