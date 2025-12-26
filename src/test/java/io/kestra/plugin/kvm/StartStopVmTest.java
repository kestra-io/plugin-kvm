package io.kestra.plugin.kvm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@MicronautTest
class StartStopVmTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testLifecycle() throws Exception {
        RunContext runContext = runContextFactory.of();

        // 1. Start the default 'test' VM provided by the driver
        StartVm startTask = StartVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("test"))
                .waitForRunning(Property.ofValue(true))
                .timeToWait(Property.ofValue(Duration.ofSeconds(10)))
                .build();

        StartVm.Output startOutput = startTask.run(runContext);
        assertThat(startOutput.getState(), is("VIR_DOMAIN_RUNNING"));

        // 2. Stop the VM
        StopVm stopTask = StopVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("test"))
                .force(Property.ofValue(true)) // Test driver handles destroy better than shutdown
                .build();

        StopVm.Output stopOutput = stopTask.run(runContext);
        assertThat(stopOutput.getState(), is("VIR_DOMAIN_SHUTOFF"));
    }
}