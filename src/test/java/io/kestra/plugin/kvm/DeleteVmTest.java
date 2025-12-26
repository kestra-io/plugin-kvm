package io.kestra.plugin.kvm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.libvirt.LibvirtException;

@MicronautTest
class DeleteVmTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testIdempotentDelete() throws Exception {
        RunContext runContext = runContextFactory.of();

        // Attempt to delete a VM that doesn't exist
        DeleteVm task = DeleteVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("i-do-not-exist"))
                .failIfNotFound(Property.ofValue(false))
                .build();

        // Should log a warning but NOT throw an exception
        assertDoesNotThrow(() -> task.run(runContext));
    }

    @Test
    void testFailOnMissingDelete() throws Exception {
        RunContext runContext = runContextFactory.of();

        DeleteVm task = DeleteVm.builder()
                .uri(Property.ofValue("test:///default"))
                .name(Property.ofValue("i-do-not-exist"))
                .failIfNotFound(Property.ofValue(true))
                .build();

        // Should throw LibvirtException (VIR_ERR_NO_DOMAIN)
        assertThrows(LibvirtException.class, () -> task.run(runContext));
    }
}