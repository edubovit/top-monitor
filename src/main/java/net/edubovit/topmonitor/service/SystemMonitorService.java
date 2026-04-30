package net.edubovit.topmonitor.service;

import com.sun.management.OperatingSystemMXBean;
import net.edubovit.topmonitor.api.MonitorSnapshot;
import net.edubovit.topmonitor.api.ProcessInfo;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SystemMonitorService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(1);
    private static final int DEFAULT_PROCESS_LIMIT = 10;

    private final Clock clock;
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final OperatingSystem operatingSystem;
    private final Path diskPath;
    private final String hostName;
    private final int logicalProcessorCount;

    private Instant cachedAt = Instant.EPOCH;
    private MonitorSnapshot cachedSnapshot;
    private int cachedProcessLimit = DEFAULT_PROCESS_LIMIT;
    private Map<Integer, OSProcess> previousProcesses = Map.of();

    public SystemMonitorService() {
        this(
                Clock.systemUTC(),
                resolveOperatingSystemMxBean(),
                new SystemInfo(),
                detectDiskPath(),
                detectHostName()
        );
    }

    SystemMonitorService(
            Clock clock,
            OperatingSystemMXBean operatingSystemMXBean,
            SystemInfo systemInfo,
            Path diskPath,
            String hostName) {
        this.clock = clock;
        this.operatingSystemMXBean = operatingSystemMXBean;
        this.operatingSystem = systemInfo.getOperatingSystem();
        this.diskPath = diskPath;
        this.hostName = hostName;
        this.logicalProcessorCount = Math.max(1, systemInfo.getHardware().getProcessor().getLogicalProcessorCount());
    }

    public synchronized MonitorSnapshot currentSnapshot(int processLimit) {
        Instant now = clock.instant();
        int normalizedProcessLimit = Math.max(0, processLimit);
        if (cachedSnapshot != null
                && cachedProcessLimit == normalizedProcessLimit
                && Duration.between(cachedAt, now).compareTo(CACHE_TTL) < 0) {
            return cachedSnapshot;
        }

        cachedSnapshot = sample(now, normalizedProcessLimit);
        cachedAt = now;
        cachedProcessLimit = normalizedProcessLimit;
        return cachedSnapshot;
    }

    private MonitorSnapshot sample(Instant timestamp, int processLimit) {
        Double cpuUsagePercent = sampleCpuUsagePercent();
        Long memoryTotalBytes = sampleTotalMemoryBytes();
        Long memoryUsedBytes = sampleUsedMemoryBytes(memoryTotalBytes);
        DiskUsage diskUsage = sampleDiskUsage();

        List<MeasuredProcess> measuredProcesses = currentProcesses().stream()
                .map(process -> measureProcess(process, previousProcesses.get(process.getProcessID()), memoryTotalBytes))
                .toList();

        List<ProcessInfo> topCpuProcesses = measuredProcesses.stream()
                .sorted(Comparator.comparing(MeasuredProcess::cpuUsagePercent, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(streamLimit(processLimit))
                .map(MeasuredProcess::processInfo)
                .toList();

        List<ProcessInfo> topMemoryProcesses = measuredProcesses.stream()
                .sorted(Comparator.comparing(MeasuredProcess::memoryResidentBytes, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(streamLimit(processLimit))
                .map(MeasuredProcess::processInfo)
                .toList();

        previousProcesses = measuredProcesses.stream()
                .collect(Collectors.toMap(
                        measured -> measured.process().getProcessID(),
                        MeasuredProcess::process,
                        (left, right) -> left
                ));

        return new MonitorSnapshot(
                timestamp,
                hostName,
                cpuUsagePercent,
                memoryUsedBytes,
                memoryTotalBytes,
                diskUsage.usedBytes(),
                diskUsage.totalBytes(),
                diskUsage.path(),
                topCpuProcesses,
                topMemoryProcesses
        );
    }

    private Double sampleCpuUsagePercent() {
        if (operatingSystemMXBean == null) {
            return null;
        }

        double cpuLoad = operatingSystemMXBean.getCpuLoad();
        if (!Double.isFinite(cpuLoad) || cpuLoad < 0) {
            return null;
        }
        return clamp(cpuLoad * 100.0, 0.0, 100.0);
    }

    private Long sampleTotalMemoryBytes() {
        if (operatingSystemMXBean == null) {
            return null;
        }

        long totalMemorySize = operatingSystemMXBean.getTotalMemorySize();
        return totalMemorySize > 0 ? totalMemorySize : null;
    }

    private Long sampleUsedMemoryBytes(Long totalMemoryBytes) {
        if (operatingSystemMXBean == null || totalMemoryBytes == null) {
            return null;
        }

        long freeMemorySize = operatingSystemMXBean.getFreeMemorySize();
        if (freeMemorySize < 0) {
            return null;
        }
        return Math.max(0, totalMemoryBytes - freeMemorySize);
    }

    private DiskUsage sampleDiskUsage() {
        try {
            FileStore fileStore = Files.getFileStore(diskPath);
            long totalSpace = fileStore.getTotalSpace();
            long unallocatedSpace = fileStore.getUnallocatedSpace();
            if (totalSpace >= 0 && unallocatedSpace >= 0) {
                return new DiskUsage(
                        Math.max(0, totalSpace - unallocatedSpace),
                        totalSpace,
                        diskPath.toString()
                );
            }
        } catch (IOException ignored) {
            // Disk metrics are optional and can be unavailable on some platforms/filesystems.
        }
        return new DiskUsage(null, null, diskPath.toString());
    }

    private List<OSProcess> currentProcesses() {
        return operatingSystem.getProcesses().stream()
                .filter(process -> process != null && process.getState() != OSProcess.State.INVALID)
                .filter(process -> process.getProcessID() != 0 && !"Idle".equalsIgnoreCase(fallback(safeString(process::getName), "")))
                .toList();
    }

    private static long streamLimit(int processLimit) {
        return processLimit <= 0 ? Long.MAX_VALUE : processLimit;
    }

    private MeasuredProcess measureProcess(OSProcess process, OSProcess previousProcess, Long memoryTotalBytes) {
        double rawCpuLoad = safeDouble(() -> process.getProcessCpuLoadBetweenTicks(previousProcess));
        Double cpuUsagePercent = Double.isFinite(rawCpuLoad)
                ? clamp((rawCpuLoad * 100.0) / logicalProcessorCount, 0.0, 100.0)
                : null;

        Long residentBytes = safeLong(process::getResidentSetSize);
        Long virtualBytes = safeLong(process::getVirtualSize);
        Double memoryUsagePercent = residentBytes != null && memoryTotalBytes != null && memoryTotalBytes > 0
                ? clamp((residentBytes.doubleValue() / memoryTotalBytes) * 100.0, 0.0, 100.0)
                : null;

        ProcessInfo processInfo = new ProcessInfo(
                process.getProcessID(),
                safeParentPid(process),
                fallback(safeString(process::getName), "unknown"),
                fallback(safeString(process::getUser), ""),
                process.getState() == null ? "UNKNOWN" : process.getState().name(),
                cpuUsagePercent,
                residentBytes,
                virtualBytes,
                memoryUsagePercent,
                safeLong(process::getUpTime),
                fallbackCommand(process)
        );
        return new MeasuredProcess(process, processInfo, cpuUsagePercent, residentBytes);
    }

    private static int safeParentPid(OSProcess process) {
        try {
            return process.getParentProcessID();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String fallbackCommand(OSProcess process) {
        String commandLine = safeString(process::getCommandLine);
        if (commandLine != null && !commandLine.isBlank()) {
            return commandLine;
        }

        String path = safeString(process::getPath);
        if (path != null && !path.isBlank()) {
            return path;
        }

        return fallback(safeString(process::getName), "unknown");
    }

    private static String safeString(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Long safeLong(LongSupplier supplier) {
        try {
            long value = supplier.getAsLong();
            return value >= 0 ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static double safeDouble(DoubleSupplierWithRuntimeException supplier) {
        try {
            return supplier.getAsDouble();
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static OperatingSystemMXBean resolveOperatingSystemMxBean() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystemMXBean) {
            return operatingSystemMXBean;
        }
        return null;
    }

    private static Path detectDiskPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        if (workingDirectory.getRoot() != null) {
            return workingDirectory.getRoot();
        }

        Iterator<Path> roots = FileSystems.getDefault().getRootDirectories().iterator();
        if (roots.hasNext()) {
            return roots.next();
        }

        return workingDirectory;
    }

    private static String detectHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return "localhost";
        }
    }

    @FunctionalInterface
    private interface DoubleSupplierWithRuntimeException {
        double getAsDouble();
    }

    private record DiskUsage(Long usedBytes, Long totalBytes, String path) {
    }

    private record MeasuredProcess(
            OSProcess process,
            ProcessInfo processInfo,
            Double cpuUsagePercent,
            Long memoryResidentBytes) {
    }
}
