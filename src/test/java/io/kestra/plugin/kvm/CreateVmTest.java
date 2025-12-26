package io.kestra.plugin.kvm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class CreateVmTest {
    @Inject
    private RunContextFactory runContextFactory;

    private static final String XML = "<domain type='test'>"
            + "<name>unit-test-vm</name>"
            + "<memory unit='KiB'>128</memory>"
            + "<os><type>hvm</type></os>"
            + "</domain>";

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        CreateVm task = CreateVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("unit-test-vm"))
                .xmlDefinition(Property.ofValue(XML))
                .startAfterCreate(Property.ofValue(true))
                .build();

        // First run: Create
        CreateVm.Output runOutput = task.run(runContext);
        assertThat(runOutput.getName(), is("unit-test-vm"));
        assertThat(runOutput.getState(), containsString("RUNNING"));

        // Second run: Idempotency check (should just redefine/sync)
        CreateVm.Output secondOutput = task.run(runContext);
        assertThat(secondOutput.getName(), is("unit-test-vm"));
    }
}