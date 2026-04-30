package net.edubovit.topmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "home")
public record HomeProperties(boolean show, String location) {

    public HomeProperties {
        location = location == null ? "" : location.trim();
    }
}
