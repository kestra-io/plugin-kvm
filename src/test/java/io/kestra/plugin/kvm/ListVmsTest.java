package io.kestra.plugin.kvm;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class ListVmsTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        // We use the 'test:///default' URI which is a built-in mock hypervisor in libvirt
        ListVms task = ListVms.builder()
            .uri(Property.of("test:///default"))
            .build();

        ListVms.Output runOutput = task.run(runContext);

        assertThat(runOutput.getVms(), notNullValue());
    }
}