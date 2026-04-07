package com.rstltd.skypulse.service;

import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SystemLogEventListener {

    private final SystemLogService systemLogService;

    public SystemLogEventListener(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        systemLogService.logEvent("SYSTEM", "INFO", "STARTUP",
                "SkyPulse application started");
    }

    @PreDestroy
    public void onShutdown() {
        systemLogService.logEvent("SYSTEM", "INFO", "SHUTDOWN",
                "SkyPulse application shutting down");
    }
}
