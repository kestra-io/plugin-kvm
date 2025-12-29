package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;

/**
 * Task to create a KVM Virtual Machine.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin(
        examples = {
            @Example(
                    full = true,
                    code = """
                        id: crea_kvm_vm_ssh
                        namespace: kvmtest.ssh

                        tasks:
                            - id: create_vm
                              type: io.kestra.plugin.kvm.CreateVm
                              # Replace with your Droplet IP.
                              # ?no_verify=1 skips the 'known_hosts' check for the first run.
                              uri: qemu+ssh://root@167.99.104.163/system

                              # This is the standard Libvirt XML format
                              xmlDefinition: |
                                <domain type='kvm'>
                                    <name>kestra-worker-nodes</name>
                                    <memory unit='MiB'>512</memory>
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

                              # If true, it attempts to boot the VM immediately after defining it
                              startAfterCreate: true

                            - id: log_result
                              type: io.kestra.plugin.core.log.Log
                              message: |
                                VM Created!
                                Name: {{outputs.create_vm.name}}
                                UUID: {{ outputs.create_vm.uuid }}
                                State: {{ outputs.create_vm.state }}
                        """
                    )
        }
)
@Schema(title = "Create VM")
public class CreateVm extends AbstractKvmTask implements RunnableTask<CreateVm.Output> {

    @Schema(title = "VM Name")
    private Property<String> name;

    @Schema(title = "XML Definition")
    private Property<String> xmlDefinition;

    @Builder.Default
    @Schema(title = "Start after create")
    private Property<Boolean> startAfterCreate = Property.ofValue(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String xml = runContext.render(this.xmlDefinition).as(String.class).orElseThrow();
            String name = runContext.render(this.name).as(String.class).orElseThrow();

            // This updates the XML if it exists, or creates a new one if it doesn't.
            // Libvirt's defineXML is natively idempotent for configuration.
            Domain domain = getDomain(conn, name);
            if (domain == null) {
                domain = conn.domainDefineXML(xml);
            }
            runContext.logger().info("VM definition synchronized for {}.", domain.getName());

            if (runContext.render(this.startAfterCreate).as(Boolean.class).orElse(false)
                    && domain.getInfo().state != DomainState.VIR_DOMAIN_RUNNING) {
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
