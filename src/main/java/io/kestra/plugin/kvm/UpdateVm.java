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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Update a VM definition", description = "Re-defines a VM using new XML.")
@Plugin
public class UpdateVm extends AbstractKvmTask implements RunnableTask<UpdateVm.Output> {
    @Schema(title = "Domain XML Definition")
    protected Property<String> xmlDefinition;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = getConnection(runContext); // Move outside the try
        try {
            String xml = runContext.render(this.xmlDefinition).as(String.class).orElseThrow();
            Domain domain = conn.domainDefineXML(xml);
            return Output.builder()
                .uuid(domain.getUUIDString())
                .name(domain.getName())
                .state(domain.getInfo().state.toString())
                .build();
        } finally {
            if (conn != null) {
                conn.close(); // Manually close in finally
            }
        }
    }

    @Builder @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final String uuid;
        private final String name;
        private final String state;
    }
}