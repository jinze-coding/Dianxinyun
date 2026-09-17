package com.example.siteplatform.system.userimport;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="user-import", name="worker-enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class UserImportScheduler {
    private final UserImportService service;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "user-import"); t.setDaemon(true); return t; });
    @Scheduled(fixedDelayString = "${user-import.poll-millis:3000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) return;
        worker.execute(() -> { try { service.processNext(); } finally { running.set(false); } });
    }
    @Scheduled(fixedDelayString = "${user-import.cleanup-millis:60000}")
    public void cleanup() { service.purgeCredentials(); }
    @PreDestroy public void stop() { worker.shutdownNow(); }
}
