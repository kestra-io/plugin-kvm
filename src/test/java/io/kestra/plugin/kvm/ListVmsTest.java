package io.kestra.plugin.kvm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class ListVmsTest {
    @Inject
    private RunContextFactory runContextFactory;

    private static final String XML = "<domain type='test'>"
            + "<name>unit-test-vm</name>"
            + "<memory unit='KiB'>128</memory>"
            + "<os><type>hvm</type></os>"
            + "</domain>";

    @Test
    void testListAndFilter() throws Exception {
        RunContext runContext = runContextFactory.of();

        CreateVm createVmTask = CreateVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("unit-test-vm"))
                .xmlDefinition(Property.ofValue(XML))
                .startAfterCreate(Property.ofValue(true))
                .build();

        // First run: Create
        CreateVm.Output runOutput = createVmTask.run(runContext);
        assertThat(runOutput.getName(), is("unit-test-vm"));
        assertThat(runOutput.getState(), containsString("RUNNING"));

        ListVms task = ListVms.builder()
                .uri(Property.ofValue("test:///default"))
                .statusFilter(Property.ofValue("VIR_DOMAIN_RUNNING"))
                .build();

        ListVms.Output output = task.run(runContext);

        assertThat(output.getVms(), not(empty()));
        // Verify filter
        output.getVms().forEach(vm -> assertThat(vm.getState(), is("VIR_DOMAIN_RUNNING")));
    }
}
