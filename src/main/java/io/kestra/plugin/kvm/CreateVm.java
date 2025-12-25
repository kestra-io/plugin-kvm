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
@Schema(title = "Create a VM", description = "Define a new VM using XML.")
@Plugin
public class CreateVm extends AbstractKvmTask implements RunnableTask<CreateVm.Output> {
    @Schema(title = "Domain XML Definition")
    protected Property<String> xmlDefinition;

    @Schema(title = "Start the VM after creation")
    @Builder.Default
    protected Property<Boolean> startAfterCreate = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = getConnection(runContext);
        try {
            String xml = runContext.render(this.xmlDefinition).as(String.class).orElseThrow();
            Domain domain = conn.domainDefineXML(xml);
            if (runContext.render(this.startAfterCreate).as(Boolean.class).orElse(false)) {
                domain.create();
            }
            return Output.builder()
                .uuid(domain.getUUIDString())
                .name(domain.getName())
                .finalState(domain.getInfo().state.toString())
                .build();
        } finally {
            conn.close();
        }
    }

    @Builder @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final String uuid;
        private final String name;
        private final String finalState;
    }
}