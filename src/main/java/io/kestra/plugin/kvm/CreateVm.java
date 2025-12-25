package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.LibvirtException;

/**
 * Task to create a KVM Virtual Machine.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin
public class CreateVm extends AbstractKvmTask implements RunnableTask<CreateVm.Output> {

    @PluginProperty(dynamic = true)
    private String name;

    @PluginProperty(dynamic = true)
    private String xmlDefinition;

    @Builder.Default
    private Boolean startAfterCreate = false;

    private Domain getDomain(Connect conn, String name) {
        try {
            return conn.domainLookupByName(name);
        } catch (LibvirtException e) {
            return null;
        }
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String xml = runContext.render(xmlDefinition);
            String name = runContext.render(this.name);

            // This updates the XML if it exists, or creates a new one if it doesn't.
            // Libvirt's defineXML is natively idempotent for configuration.
            Domain domain = getDomain(conn, name);
            if (domain == null) {
                domain = conn.domainDefineXML(xml);
            }
            runContext.logger().info("VM definition synchronized for {}.", domain.getName());

            if (Boolean.TRUE.equals(startAfterCreate) && domain.getInfo().state != DomainState.VIR_DOMAIN_RUNNING) {
                domain.create();
                runContext.logger().info("VM {} booted.", domain.getName());
            }

            return Output.builder()
                    .name(domain.getName())
                    .uuid(domain.getUUIDString())
                    .state(domain.getInfo().state.toString())
                    .build();
        }
    }

    /**
     * Output for the CreateVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private String uuid;
        private String state;
    }
}
