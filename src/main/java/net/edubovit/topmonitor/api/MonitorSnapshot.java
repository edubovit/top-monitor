package net.edubovit.topmonitor.api;

import java.time.Instant;
import java.util.List;

public record MonitorSnapshot(
        Instant timestamp,
        String hostName,
        Double cpuUsagePercent,
        Long memoryUsedBytes,
        Long memoryTotalBytes,
        Long diskUsedBytes,
        Long diskTotalBytes,
        String diskPath,
        List<ProcessInfo> topCpuProcesses,
        List<ProcessInfo> topMemoryProcesses) {
}
