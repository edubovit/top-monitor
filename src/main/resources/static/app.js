const GAUGE_SWEEP_DEGREES = 330;
const DEFAULT_REFRESH_SECONDS = 5;

const state = {
    refreshIntervalSeconds: DEFAULT_REFRESH_SECONDS,
    processLimit: '10',
    hasLoadedData: false,
    monitorRequestId: 0,
    refreshTimer: null,
    loading: false,
};

const ui = {
    homeLink: document.getElementById('home'),
    refreshIntervalInput: document.getElementById('refresh-interval'),
    processLimitControl: document.getElementById('process-limit-control'),
    processLimitButton: document.getElementById('process-limit-button'),
    processLimitValue: document.getElementById('process-limit-value'),
    processLimitMenu: document.getElementById('process-limit-menu'),
    processLimitOptions: [...document.querySelectorAll('.dropdown-option[data-value]')],
    message: document.getElementById('message'),
    loadingMessage: document.getElementById('loading-message'),
    hostName: document.getElementById('host-name'),
    sampleTime: document.getElementById('sample-time'),
    cpuGauge: document.getElementById('cpuGauge'),
    cpuValue: document.getElementById('cpuValue'),
    cpuDetail: document.getElementById('cpuDetail'),
    cpuHint: document.getElementById('cpuHint'),
    memoryGauge: document.getElementById('memoryGauge'),
    memoryValue: document.getElementById('memoryValue'),
    memoryDetail: document.getElementById('memoryDetail'),
    memoryHint: document.getElementById('memoryHint'),
    diskGauge: document.getElementById('diskGauge'),
    diskValue: document.getElementById('diskValue'),
    diskDetail: document.getElementById('diskDetail'),
    diskHint: document.getElementById('diskHint'),
    cpuProcesses: document.getElementById('cpu-processes'),
    memoryProcesses: document.getElementById('memory-processes'),
};

ui.refreshIntervalInput.addEventListener('change', () => {
    state.refreshIntervalSeconds = Math.max(Number(ui.refreshIntervalInput.value) || 0, 0);
    ui.refreshIntervalInput.value = state.refreshIntervalSeconds;
    scheduleAutoRefresh();
});

ui.processLimitButton.addEventListener('click', () => {
    toggleProcessLimitMenu();
});

ui.processLimitButton.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown') {
        event.preventDefault();
        openProcessLimitMenu();
        const selectedOption = ui.processLimitOptions.find((option) => option.dataset.value === state.processLimit);
        (selectedOption || ui.processLimitOptions[0]).focus();
    }
});

ui.processLimitOptions.forEach((option) => {
    option.addEventListener('click', () => {
        setProcessLimit(option.dataset.value);
        closeProcessLimitMenu();
        void loadMonitor();
    });

    option.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            setProcessLimit(option.dataset.value);
            closeProcessLimitMenu();
            ui.processLimitButton.focus();
            void loadMonitor();
        }
    });
});

document.addEventListener('click', (event) => {
    if (!ui.processLimitControl.contains(event.target)) {
        closeProcessLimitMenu();
    }
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        closeProcessLimitMenu();
    }
});

setProcessLimit(state.processLimit);
void initialize();
scheduleAutoRefresh();

async function initialize() {
    await loadConfig();
    await loadMonitor();
}

async function loadConfig() {
    try {
        const response = await fetch(appUrl('api/config', {t: Date.now()}), {cache: 'no-store'});
        if (!response.ok) {
            throw new Error(`Config request failed: ${response.status} ${response.statusText}`);
        }
        renderConfig(await response.json());
    } catch {
        renderConfig({home: {show: false, location: ''}});
    }
}

function renderConfig(config) {
    const home = config?.home || {};
    if (home.show && home.location) {
        ui.homeLink.href = home.location;
        ui.homeLink.hidden = false;
    } else {
        ui.homeLink.hidden = true;
        ui.homeLink.removeAttribute('href');
    }
}

async function loadMonitor() {
    const requestId = ++state.monitorRequestId;
    const requestedProcessLimit = state.processLimit;
    state.loading = true;
    hideMessage();

    try {
        const response = await fetch(appUrl('api/monitor', {limit: requestedProcessLimit, t: Date.now(), r: Math.random()}), {
            cache: 'no-store'
        });
        if (!response.ok) {
            throw new Error(`Monitor request failed: ${response.status} ${response.statusText}`);
        }

        const snapshot = await response.json();
        if (requestId === state.monitorRequestId && requestedProcessLimit === state.processLimit) {
            renderSnapshot(snapshot);
            markAppReady();
        }
    } catch (error) {
        if (requestId === state.monitorRequestId) {
            if (state.hasLoadedData) {
                showMessage(error.message || 'Unable to load monitor data.');
            } else {
                ui.loadingMessage.textContent = `${error.message || 'Unable to load monitor data.'} Retrying…`;
            }
        }
    } finally {
        if (requestId === state.monitorRequestId) {
            state.loading = false;
        }
    }
}

function markAppReady() {
    if (!state.hasLoadedData) {
        state.hasLoadedData = true;
        document.body.classList.remove('app-loading');
        document.body.classList.add('app-ready');
    }
}

function setProcessLimit(value) {
    state.processLimit = value || '10';
    ui.processLimitValue.textContent = state.processLimit === 'all' ? 'All' : state.processLimit;
    ui.processLimitOptions.forEach((option) => {
        option.setAttribute('aria-selected', String(option.dataset.value === state.processLimit));
    });
}

function toggleProcessLimitMenu() {
    if (ui.processLimitMenu.hidden) {
        openProcessLimitMenu();
    } else {
        closeProcessLimitMenu();
    }
}

function openProcessLimitMenu() {
    ui.processLimitMenu.hidden = false;
    ui.processLimitButton.setAttribute('aria-expanded', 'true');
    ui.processLimitControl.classList.add('is-open');
}

function closeProcessLimitMenu() {
    ui.processLimitMenu.hidden = true;
    ui.processLimitButton.setAttribute('aria-expanded', 'false');
    ui.processLimitControl.classList.remove('is-open');
}

function scheduleAutoRefresh() {
    if (state.refreshTimer !== null) {
        window.clearInterval(state.refreshTimer);
        state.refreshTimer = null;
    }

    if (state.refreshIntervalSeconds > 0) {
        state.refreshTimer = window.setInterval(() => {
            void loadMonitor();
        }, state.refreshIntervalSeconds * 1000);
    }
}

function renderSnapshot(snapshot) {
    ui.hostName.textContent = snapshot.hostName || 'localhost';
    ui.sampleTime.textContent = snapshot.timestamp ? formatDateTime(snapshot.timestamp) : '—';

    renderCpu(snapshot.cpuUsagePercent);
    renderMemory(snapshot.memoryUsedBytes, snapshot.memoryTotalBytes);
    renderDisk(snapshot.diskUsedBytes, snapshot.diskTotalBytes, snapshot.diskPath);
    renderProcessTable(ui.cpuProcesses, snapshot.topCpuProcesses || [], 'cpu');
    renderProcessTable(ui.memoryProcesses, snapshot.topMemoryProcesses || [], 'memory');
}

function renderCpu(value) {
    if (typeof value !== 'number') {
        renderGaugeUnavailable(ui.cpuGauge, ui.cpuValue, ui.cpuDetail, ui.cpuHint, 'All cores', 'Metric unavailable');
        return;
    }

    const percent = clamp(value, 0, 100);
    setGaugePercentage(ui.cpuGauge, percent);
    ui.cpuValue.textContent = formatPercent(percent, 1);
    ui.cpuDetail.textContent = 'All cores';
    ui.cpuHint.textContent = `Current server CPU load: ${formatPercent(percent, 1)}`;
}

function renderMemory(usedBytes, totalBytes) {
    if (!isPositiveNumber(totalBytes) || typeof usedBytes !== 'number') {
        renderGaugeUnavailable(ui.memoryGauge, ui.memoryValue, ui.memoryDetail, ui.memoryHint, 'Used RAM', 'Metric unavailable');
        return;
    }

    const percent = calculateUsagePercent(usedBytes, totalBytes);
    setGaugePercentage(ui.memoryGauge, percent);
    ui.memoryValue.textContent = formatPercent(percent, 0);
    ui.memoryDetail.textContent = 'Used RAM';
    ui.memoryHint.textContent = `${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}`;
}

function renderDisk(usedBytes, totalBytes, path) {
    if (!isPositiveNumber(totalBytes) || typeof usedBytes !== 'number') {
        renderGaugeUnavailable(ui.diskGauge, ui.diskValue, ui.diskDetail, ui.diskHint, path || 'Filesystem', 'Metric unavailable');
        return;
    }

    const percent = calculateUsagePercent(usedBytes, totalBytes);
    setGaugePercentage(ui.diskGauge, percent);
    ui.diskValue.textContent = formatPercent(percent, 0);
    ui.diskDetail.textContent = path || 'Filesystem';
    ui.diskHint.textContent = `${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}`;
}

function renderProcessTable(tbody, processes, tableKind) {
    tbody.replaceChildren();

    if (processes.length === 0) {
        const row = document.createElement('tr');
        row.innerHTML = '<td colspan="4" class="empty-cell">No process data available.</td>';
        tbody.append(row);
        return;
    }

    processes.forEach((process) => {
        const row = document.createElement('tr');
        const processCell = renderProcessCell(process);
        const cpuValue = renderMetricValue(formatOptionalPercent(process.cpuUsagePercent, 1), process.cpuUsagePercent);
        const memoryValue = renderMetricValue(formatCompactBytes(process.memoryResidentBytes), process.memoryUsagePercent);

        if (tableKind === 'cpu') {
            row.append(
                td(pidLabel(process.pid)),
                processCell,
                td(cpuValue),
                td(memoryValue)
            );
        } else {
            row.append(
                td(pidLabel(process.pid)),
                processCell,
                td(memoryValue),
                td(cpuValue)
            );
        }
        tbody.append(row);
    });
}

function renderProcessCell(process) {
    const cell = document.createElement('td');
    cell.className = 'process-cell';

    const name = document.createElement('div');
    name.className = 'process-name';
    name.textContent = process.name || 'unknown';
    name.title = process.name || 'unknown';

    const command = document.createElement('div');
    command.className = 'process-command';
    command.textContent = process.commandLine || process.name || 'unknown';
    command.title = process.commandLine || process.name || 'unknown';

    cell.append(name, command);
    return cell;
}

function td(content) {
    const cell = document.createElement('td');
    if (content instanceof Node) {
        cell.append(content);
    } else {
        cell.textContent = content;
    }
    return cell;
}

function pidLabel(pid) {
    const span = document.createElement('span');
    span.className = 'pid';
    span.textContent = pid || '—';
    return span;
}

function renderMetricValue(text, percent) {
    const span = document.createElement('span');
    span.className = `metric-value ${metricTemperature(percent)}`;
    span.textContent = text;
    return span;
}

function metricTemperature(percent) {
    if (typeof percent !== 'number') {
        return '';
    }
    if (percent >= 80) {
        return 'hot';
    }
    if (percent >= 55) {
        return 'warm';
    }
    return 'cool';
}

function renderGaugeUnavailable(gaugeElement, valueElement, detailElement, summaryElement, detailText, summaryText) {
    gaugeElement.style.setProperty('--fill-sweep', '0deg');
    gaugeElement.classList.add('is-unavailable');
    valueElement.textContent = '—';
    detailElement.textContent = detailText;
    summaryElement.textContent = summaryText;
}

function setGaugePercentage(gaugeElement, percent) {
    const normalized = clamp(percent, 0, 100);
    const fillSweep = (normalized / 100) * GAUGE_SWEEP_DEGREES;
    gaugeElement.style.setProperty('--fill-sweep', `${fillSweep}deg`);
    gaugeElement.classList.remove('is-unavailable');
}

function appUrl(path, params = {}) {
    const url = new URL(path.replace(/^\/+/, ''), document.baseURI);
    Object.entries(params).forEach(([name, value]) => {
        if (value !== undefined && value !== null) {
            url.searchParams.set(name, String(value));
        }
    });
    return url.href;
}

function showMessage(message) {
    ui.message.hidden = false;
    ui.message.textContent = message;
}

function hideMessage() {
    ui.message.hidden = true;
    ui.message.textContent = '';
}

function isPositiveNumber(value) {
    return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

function calculateUsagePercent(usedBytes, totalBytes) {
    if (!totalBytes || totalBytes <= 0) {
        return 0;
    }
    return clamp((usedBytes / totalBytes) * 100, 0, 100);
}

function formatDateTime(value) {
    return new Date(value).toLocaleString(undefined, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

function formatOptionalPercent(value, digits = 0) {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
        return '—';
    }
    return formatPercent(value, digits);
}

function formatPercent(value, digits = 0) {
    return `${value.toFixed(digits)}%`;
}

function formatBytes(bytes) {
    if (typeof bytes !== 'number' || !Number.isFinite(bytes)) {
        return '—';
    }

    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB'];
    let value = bytes;
    let unitIndex = 0;
    while (Math.abs(value) >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function formatCompactBytes(bytes) {
    if (typeof bytes !== 'number' || !Number.isFinite(bytes)) {
        return '—';
    }

    const units = ['B', 'K', 'M', 'G', 'T', 'P'];
    let value = bytes;
    let unitIndex = 0;
    while (Math.abs(value) >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
    }
    return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)}${units[unitIndex]}`;
}

function clamp(value, minimum, maximum) {
    return Math.max(minimum, Math.min(value, maximum));
}
