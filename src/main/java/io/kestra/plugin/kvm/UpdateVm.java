package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
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

/**
 * Task to update a KVM Virtual Machine configuration.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin(examples = {
        @Example(title = "Update a VM configuration and restart to apply changes", full = true, code = """
                id: update_vm
                type: io.kestra.plugin.kvm.UpdateVm
                uri: qemu+ssh://root@1.2.3.4/system
                name: my-server
                xmlDefinition: "{{ outputs.get_new_xml.body }}"
                restart: true
                """)
})
public class UpdateVm extends AbstractKvmTask implements RunnableTask<UpdateVm.Output> {
    @PluginProperty(dynamic = true)
    private String name;

    @PluginProperty(dynamic = true)
    private String xmlDefinition;

    @PluginProperty
    @Builder.Default
    private Boolean restart = false;

    @Override
    public UpdateVm.Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String renderedXml = runContext.render(xmlDefinition);
            String renderedName = runContext.render(name);

            // 1. Update the persistent definition
            // domainDefineXML is the standard way to update an existing domain's config
            Domain domain = conn.domainDefineXML(renderedXml);
            runContext.logger().info("Updated definition for VM: {}", renderedName);

            // 2. Handle Restart logic
            boolean wasRestarted = false;
            if (Boolean.TRUE.equals(restart)) {
                DomainState state = domain.getInfo().state;
                if (state == DomainState.VIR_DOMAIN_RUNNING || state == DomainState.VIR_DOMAIN_PAUSED) {
                    runContext.logger().info("Restarting VM {} to apply changes...", renderedName);
                    domain.destroy(); // Hard stop
                    domain.create(); // Start
                    wasRestarted = true;
                } else {
                    runContext.logger().info("VM {} is not running; configuration updated for next boot.",
                            renderedName);
                }
            }

            return Output.builder()
                    .name(domain.getName())
                    .wasRestarted(wasRestarted)
                    .state(domain.getInfo().state.toString())
                    .build();
        }
    }

    /**
     * Output for the UpdateVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private Boolean wasRestarted;
        private String state;
    }
}