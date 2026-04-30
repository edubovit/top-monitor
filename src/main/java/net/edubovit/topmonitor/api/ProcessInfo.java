package net.edubovit.topmonitor.api;

public record ProcessInfo(
        int pid,
        int parentPid,
        String name,
        String user,
        String state,
        Double cpuUsagePercent,
        Long memoryResidentBytes,
        Long memoryVirtualBytes,
        Double memoryUsagePercent,
        Long uptimeMillis,
        String commandLine) {
}
