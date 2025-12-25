package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

@SuperBuilder @ToString @EqualsAndHashCode @Getter @NoArgsConstructor
@Schema(title = "Delete a VM", description = "Undefines a VM.")
@Plugin
public class DeleteVm extends AbstractKvmTask implements RunnableTask<DeleteVm.Output> {
    @Schema(title = "VM Name or UUID")
    protected Property<String> name;

    @Schema(title = "Fail if VM does not exist", description = "If false, the task will succeed even if the VM is already gone.")
    @Builder.Default
    protected Property<Boolean> failIfNotExists = Property.of(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = getConnection(runContext); // Move outside the try
        try {
            String vmName = runContext.render(this.name).as(String.class).orElseThrow();
            boolean failIfMissing = runContext.render(this.failIfNotExists).as(Boolean.class).orElse(true);

            try {
                Domain domain = conn.domainLookupByName(vmName);
                if (domain.isActive() == 1) {
                    domain.destroy();
                }
                domain.undefine();
                return Output.builder().name(vmName).deleted(true).build();
            } catch (LibvirtException e) {
                if (failIfMissing) throw e;
                return Output.builder().name(vmName).deleted(false).build();
            }
        } finally {
            if (conn != null) {
                conn.close(); // Manually close in finally
            }
        }
    }

    @Builder @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final String name;
        private final boolean deleted;
    }
}