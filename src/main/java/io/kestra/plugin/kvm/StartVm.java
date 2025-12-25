package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo;
import org.libvirt.DomainInfo.DomainState;

/**
 * Task to start a KVM Virtual Machine.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin
public class StartVm extends AbstractKvmTask implements RunnableTask<StartVm.Output> {
    @PluginProperty(dynamic = true)
    private String name;

    @PluginProperty
    @Builder.Default
    private Boolean waitForRunning = false;

    @PluginProperty
    @Builder.Default
    private Duration timeToWait = Duration.ofSeconds(60);

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            Domain domain = conn.domainLookupByName(runContext.render(name));
            DomainInfo info = domain.getInfo();

            if (info.state == DomainState.VIR_DOMAIN_RUNNING) {
                runContext.logger().info("VM {} is already running. Skipping start.", name);
            } else {
                domain.create();
                runContext.logger().info("VM {} started successfully.", name);

                if (Boolean.TRUE.equals(waitForRunning)) {
                    long end = System.currentTimeMillis() + timeToWait.toMillis();
                    boolean success = false;

                    while (System.currentTimeMillis() < end) {
                        DomainState currentState = domain.getInfo().state;

                        if (currentState == DomainState.VIR_DOMAIN_RUNNING) {
                            success = true;
                            break;
                        }

                        // Break if VM hits a state where it will never reach 'Running' without
                        // intervention
                        if (currentState == DomainState.VIR_DOMAIN_PAUSED
                                || currentState == DomainState.VIR_DOMAIN_CRASHED
                                || currentState == DomainState.VIR_DOMAIN_SHUTOFF) {
                            throw new Exception(
                                    "VM entered terminal state " + currentState + " while waiting for RUNNING");
                        }

                        Thread.sleep(2000); // Poll every 2 seconds
                    }

                    if (!success) {
                        throw new Exception(
                                "Timeout waiting for VM to reach RUNNING state after " + timeToWait.getSeconds() + "s");
                    }
                }
            }

            return Output.builder()
                    .name(domain.getName())
                    .state(domain.getInfo().state.toString())
                    .build();
        }
    }

    /**
     * Output for the StartVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private String state;
    }
}
