package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
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
@EqualsAndHashCode
@ToString
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: update_kvm_vm_ssh
                namespace: kvmtest.ssh

                tasks:
                  - id: update_vm
                    type: io.kestra.plugin.kvm.UpdateVm
                    uri: qemu+ssh://root@167.99.104.163/system

                    # This is the standard Libvirt XML format
                    xmlDefinition: |
                      <domain type='kvm'>
                          <name>kestra-worker-nodes</name>
                          <memory unit='MiB'>700</memory>
                          <vcpu placement='static'>1</vcpu>
                          <os>
                            <type arch='x86_64' machine='pc-q35-6.2'>hvm</type>
                            <boot dev='hd'/>
                          </os>
                          <devices>
                              <disk type='volume' device='disk'>
                                  <driver name='qemu' type='qcow2'/>
                                  <source pool='default' volume='kestra-worker-nodes-os.qcow2'/>
                                  <target dev='vda' bus='virtio'/>
                              </disk>

                              <disk type='volume' device='disk'>
                                  <driver name='qemu' type='qcow2'/>
                                  <source pool='default' volume='kestra-worker-nodes-data.qcow2'/>
                                  <target dev='vdb' bus='virtio'/>
                              </disk>
                          </devices>
                      </domain>

                    # If true, it attempts to stop and start the VM to apply the changes
                    restart: true

                  - id: log_result
                    type: io.kestra.plugin.core.log.Log
                    message: |
                      VM Updated!
                      Name: {{outputs.update_vm.name}}
                      wasRestarted: {{ outputs.update_vm.wasRestarted }}
                      State: {{ outputs.update_vm.state }}
                """
        )
    }
)
@Schema(
    title = "Update KVM domain definition",
    description = "Redefines a libvirt domain from rendered XML while preserving the existing UUID if missing in the template. Optionally restarts running or paused domains to apply changes when restart is true (default false)."
)
public class UpdateVm extends AbstractKvmTask implements RunnableTask<UpdateVm.Output> {
    @Schema(
        title = "Domain name",
        description = "Domain to redefine; should match the <name> in the XML."
    )
    private Property<String> name;

    @Schema(
        title = "Domain XML",
        description = "Libvirt domain XML template rendered with flow variables before defineXML; UUID is injected when absent."
    )
    private Property<String> xmlDefinition;

    @Builder.Default
    @Schema(
        title = "Restart after update",
        description = "If true, destroys then starts the domain when it is RUNNING or PAUSED to apply changes. Default false."
    )
    private Property<Boolean> restart = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String rXml = runContext.render(this.xmlDefinition).as(String.class).orElseThrow();
            String rName = runContext.render(this.name).as(String.class).orElseThrow();

            Domain domain = conn.domainLookupByName(rName);
            String existingUuid = domain.getUUIDString();
            if (!rXml.contains("<uuid>")) {
                rXml = rXml.replaceFirst("<name>", "<uuid>" + existingUuid + "</uuid>\n<name>");
            }
            domain = conn.domainDefineXML(rXml);
            runContext.logger().info("Updated definition for VM: {}", rName);

            // Handle Restart logic
            boolean wasRestarted = false;
            if (runContext.render(this.restart).as(Boolean.class).orElse(false)) {
                DomainState state = domain.getInfo().state;
                if (state == DomainState.VIR_DOMAIN_RUNNING || state == DomainState.VIR_DOMAIN_PAUSED) {
                    runContext.logger().info("Restarting VM {} to apply changes...", rName);
                    domain.destroy(); // Hard stop
                    domain.create(); // Start
                    wasRestarted = true;
                } else {
                    runContext.logger().info("VM {} is not running; configuration updated for next boot.",
                            rName);
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
        @Schema(
            title = "VM Name",
            description = "Redefined domain name."
        )
        private String name;

        @Schema(
            title = "Restart performed",
            description = "True when the domain was destroyed and started due to restart = true."
        )
        private Boolean wasRestarted;

        @Schema(
            title = "VM State",
            description = "Libvirt domain state after the update."
        )
        private String state;
    }
}
