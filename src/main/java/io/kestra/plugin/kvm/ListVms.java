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

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List VMs",
    description = "Lists all virtual machines on a KVM host."
)
@Plugin
public class ListVms extends AbstractKvmTask implements RunnableTask<ListVms.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        Connect conn = null;
        try {
            conn = getConnection(runContext);
            List<VmInfo> vms = new ArrayList<>();

            // Add persistent (defined) domains that are shutoff
            for (String name : conn.listDefinedDomains()) {
                Domain d = conn.domainLookupByName(name);
                vms.add(VmInfo.builder()
                    .uuid(d.getUUIDString())
                    .name(d.getName())
                    .state(d.getInfo().state.toString())
                    .build());
            }

            // Add active (running) domains
            for (int id : conn.listDomains()) {
                Domain d = conn.domainLookupByID(id);
                vms.add(VmInfo.builder()
                    .uuid(d.getUUIDString())
                    .name(d.getName())
                    .state(d.getInfo().state.toString())
                    .build());
            }

            return Output.builder().vms(vms).build();
        } finally {
            if (conn != null) conn.close();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final List<VmInfo> vms;
    }

    @Builder
    @Getter
    public static class VmInfo {
        private String uuid;
        private String name;
        private String state;
    }
}