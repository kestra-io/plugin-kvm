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

@SuperBuilder @ToString @EqualsAndHashCode @Getter @NoArgsConstructor
@Schema(title = "Start a VM")
@Plugin
public class StartVm extends AbstractKvmTask implements RunnableTask<StartVm.Output> {
    @Schema(title = "VM Name or UUID")
    protected Property<String> name;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = getConnection(runContext);
        try {
            String vmName = runContext.render(this.name).as(String.class).orElseThrow();
            Domain domain = conn.domainLookupByName(vmName);
            // Idempotent: check if already running
            if (domain.isActive() == 0) {
                domain.create();
            }
            return Output.builder().uuid(domain.getUUIDString()).state(domain.getInfo().state.toString()).build();
        } finally {
            conn.close();
        }
    }

    @Builder @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final String uuid;
        private final String state;
    }
}