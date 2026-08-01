package com.example.siteplatform.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ErrorController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500})
    void businessErrorCodeIsAlsoTheHttpStatus(int code) throws Exception {
        mockMvc.perform(get("/test/business-error/" + code))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("业务错误"));
    }

    @Test
    void invalidRequestBodyReturnsHttp400WithFirstValidationMessage() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败：账号不能为空"));
    }

    @Test
    void unknownExceptionReturnsHttp500WithoutLeakingInternalMessage() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统异常，请稍后重试"));
    }

    @Test
    void invalidNumericRequestParameterReturnsHttp400() throws Exception {
        mockMvc.perform(get("/test/numeric-parameter").param("businessId", "[objectUndefined]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数格式错误：businessId"));
    }

    @Test
    void oversizedMultipartReturnsHttp413() throws Exception {
        mockMvc.perform(get("/test/upload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(413))
                .andExpect(jsonPath("$.message").value("上传文件过大，单次请求不能超过50MB"));
    }

    @Test
    void missingResourceReturnsHttp404InsteadOfInternalError() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("请求的资源不存在"));
    }

    @RestController
    static class ErrorController {
        @GetMapping("/test/business-error/{code}")
        Result<Void> businessError(@PathVariable Integer code) {
            throw BusinessException.of(code, "业务错误");
        }

        @PostMapping("/test/validate")
        Result<Void> validate(@Valid @RequestBody ValidationRequest request) {
            return Result.success();
        }

        @GetMapping("/test/internal-error")
        Result<Void> internalError() {
            throw new IllegalStateException("数据库密码等内部信息");
        }

        @GetMapping("/test/numeric-parameter")
        Result<Long> numericParameter(@RequestParam Long businessId) {
            return Result.success(businessId);
        }

        @GetMapping("/test/upload-too-large")
        Result<Void> uploadTooLarge() {
            throw new MaxUploadSizeExceededException(50L * 1024 * 1024);
        }

        @GetMapping("/test/missing-resource")
        Result<Void> missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing");
        }
    }

    static class ValidationRequest {
        @NotBlank(message = "账号不能为空")
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
