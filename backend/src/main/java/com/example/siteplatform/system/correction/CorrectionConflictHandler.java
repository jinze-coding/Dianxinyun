package com.example.siteplatform.system.correction;

import com.example.siteplatform.common.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=CorrectionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrectionConflictHandler {
    @ExceptionHandler({DuplicateKeyException.class,ConcurrencyFailureException.class})
    public ResponseEntity<Result<?>> conflict(RuntimeException error) {
        return ResponseEntity.status(409).body(Result.error(409,"记录、日期或附件已发生并发变化，请保留输入并重新核对"));
    }
}
