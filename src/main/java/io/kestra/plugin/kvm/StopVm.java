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
@Schema(title = "Stop a VM")
@Plugin
public class StopVm extends AbstractKvmTask implements RunnableTask<StopVm.Output> {
    @Schema(title = "VM Name or UUID")
    protected Property<String> name;

    @Schema(title = "Force power off")
    @Builder.Default
    protected Property<Boolean> force = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = getConnection(runContext);
        try {
            Domain domain = conn.domainLookupByName(runContext.render(this.name).as(String.class).orElseThrow());
            if (domain.isActive() == 1) {
                if (runContext.render(this.force).as(Boolean.class).orElse(false)) {
                    domain.destroy();
                } else {
                    domain.shutdown();
                }
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