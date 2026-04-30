package net.edubovit.topmonitor.web;

import net.edubovit.topmonitor.api.MonitorSnapshot;
import net.edubovit.topmonitor.api.UiConfigResponse;
import net.edubovit.topmonitor.config.HomeProperties;
import net.edubovit.topmonitor.service.SystemMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MonitorApiController {

    private static final int DEFAULT_PROCESS_LIMIT = 10;

    private final SystemMonitorService systemMonitorService;
    private final HomeProperties homeProperties;

    public MonitorApiController(SystemMonitorService systemMonitorService, HomeProperties homeProperties) {
        this.systemMonitorService = systemMonitorService;
        this.homeProperties = homeProperties;
    }

    @GetMapping("/monitor")
    public MonitorSnapshot monitor(@RequestParam(name = "limit", defaultValue = "10") String limit) {
        return systemMonitorService.currentSnapshot(resolveProcessLimit(limit));
    }

    @GetMapping("/config")
    public UiConfigResponse config() {
        return new UiConfigResponse(new UiConfigResponse.HomeConfig(
                homeProperties.show() && !homeProperties.location().isBlank(),
                homeProperties.location()
        ));
    }

    private static int resolveProcessLimit(String limit) {
        if (limit == null || limit.isBlank()) {
            return DEFAULT_PROCESS_LIMIT;
        }

        String normalizedLimit = limit.trim();
        if ("all".equalsIgnoreCase(normalizedLimit)) {
            return 0;
        }

        try {
            return Math.max(0, Integer.parseInt(normalizedLimit));
        } catch (NumberFormatException ignored) {
            return DEFAULT_PROCESS_LIMIT;
        }
    }
}
