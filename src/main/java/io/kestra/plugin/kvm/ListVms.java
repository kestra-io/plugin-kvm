package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.stream.Collectors;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;

/**
 * Task to list KVM Virtual Machines.
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
                id: kvm_lifecycle_ssh
                namespace: kvmtest.ssh

                tasks:
                  - id: list_initial_vms
                    type: io.kestra.plugin.kvm.ListVms
                    uri: qemu+ssh://root@167.99.104.163/system
                """
        )
    }
)
@Schema(title = "List VMs")
public class ListVms extends AbstractKvmTask implements RunnableTask<ListVms.Output> {

    @Schema(title = "Status Filter")
    private Property<String> statusFilter;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            java.util.List<VmEntry> vms = new java.util.ArrayList<>();

            // 1. Get Active (Running) VMs
            int[] activeIds = conn.listDomains();
            for (int id : activeIds) {
                Domain d = conn.domainLookupByID(id);
                vms.add(VmEntry.builder()
                        .name(d.getName())
                        .uuid(d.getUUIDString())
                        .state(d.getInfo().state.toString())
                        .build());
            }

            // 2. Get Inactive (Defined but stopped) VMs
            String[] inactiveNames = conn.listDefinedDomains();
            for (String name : inactiveNames) {
                Domain d = conn.domainLookupByName(name);
                vms.add(VmEntry.builder()
                        .name(d.getName())
                        .uuid(d.getUUIDString())
                        .state(d.getInfo().state.toString())
                        .build());
            }

            String rFilter = runContext.render(this.statusFilter).as(String.class).orElse(null);
            if (rFilter != null && !rFilter.isEmpty()) {
                vms = vms.stream()
                        .filter(v -> v.getState().equalsIgnoreCase(rFilter))
                        .collect(Collectors.toList());
            }

            return Output.builder().vms(vms).build();
        }
    }

    /**
     * Output for the ListVms task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private java.util.List<VmEntry> vms;
    }

    /**
     * Entry representing a VM.
     */
    @Builder
    @AllArgsConstructor
    @Getter
    public static class VmEntry {
        @Schema(title = "VM Name")
        private String name;

        @Schema(title = "VM UUID")
        private String uuid;

        @Schema(title = "VM State")
        private String state;
    }
}
