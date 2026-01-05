package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.Error.ErrorNumber;
import org.libvirt.LibvirtException;
import org.libvirt.StoragePool;
import org.libvirt.StorageVol;

/**
 * Task to delete (undefine) a KVM Virtual Machine.
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
                  - id: delete_vm
                    type: io.kestra.plugin.kvm.DeleteVm
                    uri: "qemu+ssh://root@167.99.104.163/system"
                    name: "kestra-worker-nodes"
                    deleteStorage: true
                    failIfNotFound: true
                """
        )
    }
)
@Schema(title = "Delete VM")
public class DeleteVm extends AbstractKvmTask implements RunnableTask<DeleteVm.Output> {
    @Schema(title = "VM Name")
    @NotNull
    private Property<String> name;

    @Builder.Default
    @Schema(title = "Delete Storage")
    private Property<Boolean> deleteStorage = Property.ofValue(false);

    @Builder.Default
    @Schema(title = "Fail if VM not found")
    private Property<Boolean> failIfNotFound = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String rName = runContext.render(this.name).as(String.class).orElseThrow();
            List<String> deletedVolumes = new ArrayList<>();
            boolean success = false;

            try {
                Domain domain = conn.domainLookupByName(rName);

                if (runContext.render(this.deleteStorage).as(Boolean.class).orElse(false)) {
                    deletedVolumes = findAndDeleteVolumes(domain, conn, runContext);
                }

                // A VM must be stopped before it can be undefined (deleted)
                if (domain.getInfo().state != DomainState.VIR_DOMAIN_SHUTOFF) {
                    domain.destroy();
                }

                domain.undefine();
                runContext.logger().info("VM {} deleted successfully.", rName);
                success = true;
            } catch (LibvirtException e) {
                if (e.getError().getCode() == ErrorNumber.VIR_ERR_NO_DOMAIN
                        && !runContext.render(this.failIfNotFound).as(Boolean.class).orElse(true)) {
                    runContext.logger().warn("VM {} not found. Skipping deletion.", rName);
                } else {
                    throw e;
                }
            }

            return Output.builder()
                    .success(success)
                    .deletedVolumes(deletedVolumes)
                    .build();
        }
    }

    private List<String> findAndDeleteVolumes(Domain domain, Connect conn, RunContext runContext) throws Exception {
        List<String> paths = new ArrayList<>();
        Map<String, List<String>> poolToVolumes = LibvirtXmlParser.getVolumesGroupedByPool(domain);

        for (Map.Entry<String, List<String>> entry : poolToVolumes.entrySet()) {
            String poolName = entry.getKey();
            List<String> volumeNames = entry.getValue();

            try {
                // Lookup pool once per group
                StoragePool pool = conn.storagePoolLookupByName(poolName);

                for (String volName : volumeNames) {
                    try {
                        StorageVol vol = pool.storageVolLookupByName(volName);
                        vol.delete(0);
                        paths.add(poolName + "/" + volName);
                        runContext.logger().info("Successfully deleted volume {} from pool {}", volName, poolName);
                    } catch (LibvirtException e) {
                        runContext.logger().warn("Failed to delete volume {} in pool {}: {}", volName, poolName,
                                e.getMessage());
                    }
                }
            } catch (LibvirtException e) {
                runContext.logger().error("Could not access pool {}: {}", poolName, e.getMessage());
            }
        }

        return paths;
    }

    /**
     * Output for the DeleteVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Result of the delete operation")
        private boolean success;

        @Schema(title = "List of deleted volumes")
        private List<String> deletedVolumes;
    }
}
