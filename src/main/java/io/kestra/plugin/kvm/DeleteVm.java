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
import org.libvirt.Error.ErrorNumber;
import org.libvirt.LibvirtException;

/**
 * Task to delete (undefine) a KVM Virtual Machine.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin
public class DeleteVm extends AbstractKvmTask implements RunnableTask<DeleteVm.Output> {
    @PluginProperty(dynamic = true)
    private String name;

    @PluginProperty
    @Builder.Default
    private Boolean deleteStorage = false;

    @PluginProperty
    @Builder.Default
    private Boolean failIfNotFound = true;

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String renderedName = runContext.render(name);

            try {
                Domain domain = conn.domainLookupByName(renderedName);

                if (Boolean.TRUE.equals(deleteStorage)) {
                    // TODO: need to check the requirements here
                }

                // A VM must be stopped before it can be undefined (deleted)
                if (domain.getInfo().state != DomainState.VIR_DOMAIN_SHUTOFF) {
                    domain.destroy();
                }

                domain.undefine();
                runContext.logger().info("VM {} deleted successfully.", renderedName);
            } catch (LibvirtException e) {
                if (e.getError().getCode() == ErrorNumber.VIR_ERR_NO_DOMAIN) {
                    if (Boolean.TRUE.equals(failIfNotFound)) {
                        throw e;
                    } else {
                        runContext.logger().warn("VM {} not found. Skipping deletion.", renderedName);
                    }
                } else {
                    // It's a different error (e.g., Auth, Network, Read-Only), always throw
                    throw e;
                }
            }

            return Output.builder()
                    .name(renderedName)
                    .success(true)
                    .build();
        }
    }

    /**
     * Output for the DeleteVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private Boolean success;
    }
}
