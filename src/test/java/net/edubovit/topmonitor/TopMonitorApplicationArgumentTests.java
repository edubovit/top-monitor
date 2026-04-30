package net.edubovit.topmonitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopMonitorApplicationArgumentTests {

    @Test
    void expandsConfigArgumentWithSeparatePath() {
        assertThat(TopMonitorApplication.expandConfigArgument(new String[] {"--config", "config.yaml", "--server.port=9090"}))
                .containsExactly(
                        "--spring.config.additional-location=file:config.yaml",
                        "--server.port=9090");
    }

    @Test
    void expandsConfigArgumentWithEqualsPath() {
        assertThat(TopMonitorApplication.expandConfigArgument(new String[] {"--config=config.yaml"}))
                .containsExactly("--spring.config.additional-location=file:config.yaml");
    }

    @Test
    void failsWhenConfigPathIsMissing() {
        assertThatThrownBy(() -> TopMonitorApplication.expandConfigArgument(new String[] {"--config"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--config requires a configuration file path");
    }
}
