# Top Monitor

Small Spring Boot web application that shows server CPU load, RAM usage, disk usage, and top processes by CPU and RAM.

The application is intended to be deployed as a small self-contained monitoring dashboard for the host where it runs.

## Stack

- Java 25
- Spring Boot 4.0.6
- Gradle 9.5.0 (Groovy build script)
- Static HTML/CSS/JavaScript frontend
- OS metrics collected with OSHI

## Features

- CPU, RAM, and disk gauges.
- Process tables sorted by CPU usage and RAM usage.
- Configurable process table size from the UI: `5`, `10`, `20`, `30`, `50`, `100`, or `All`.
- Default table size: `10` processes.
- Auto-refresh every 5 seconds by default; set refresh interval to `0` in the UI to pause refreshes.
- Optional Home button configured from `application.yml`.
- Initial loading screen hides placeholder dashboard data until the first metrics sample is ready.

## Configuration

Default configuration is in `src/main/resources/application.yml`.

```yaml
server:
  port: 20002

home:
  show: false
  location: ""
```

The Home button is shown only when `home.show` is `true` and `home.location` is not blank.

An external config file can be supplied with either form:

```bash
java -jar build/libs/top-monitor.jar --config /opt/top-monitor/application.yml
java -jar build/libs/top-monitor.jar --config=/opt/top-monitor/application.yml
```

`--config` is expanded internally to Spring Boot's `spring.config.additional-location`.

## Run

```bash
./gradlew bootJar
java -jar build/libs/top-monitor.jar
```

Open <http://localhost:20002/> or the URL matching the configured `server.port`.

## API

- `GET /api/config` — frontend configuration, including optional Home button settings.
- `GET /api/monitor` — metrics and process data with default process limit `10`.
- `GET /api/monitor?limit=20` — returns up to 20 processes per table.
- `GET /api/monitor?limit=all` — returns all available non-idle processes.

## Systemd service

An example service unit is provided in `top-monitor.service`.

Expected deployment layout:

```text
/opt/top-monitor/top-monitor.jar
/opt/top-monitor/application.yml
```

Install example:

```bash
sudo useradd --system --home /opt/top-monitor --shell /usr/sbin/nologin topmonitor
sudo mkdir -p /opt/top-monitor
sudo cp build/libs/top-monitor.jar /opt/top-monitor/top-monitor.jar
sudo cp src/main/resources/application.yml /opt/top-monitor/application.yml
sudo cp top-monitor.service /etc/systemd/system/top-monitor.service
sudo systemctl daemon-reload
sudo systemctl enable --now top-monitor
```

## Build artifact

The runnable JAR is configured as:

```text
build/libs/top-monitor.jar
```
