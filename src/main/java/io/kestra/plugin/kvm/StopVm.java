package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;

/**
 * Task to stop a KVM Virtual Machine.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Plugin
public class StopVm extends AbstractKvmTask implements RunnableTask<StopVm.Output> {
    private Property<String> name;

    @Builder.Default
    private Property<Boolean> force = Property.ofValue(false);

    @Builder.Default
    private Property<Boolean> waitForStopped = Property.ofValue(false);

    @Builder.Default
    private Property<Duration> timeToWait = Property.ofValue(Duration.ofSeconds(60));

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String renderedName = runContext.render(this.name).as(String.class).orElseThrow();
            Domain domain = conn.domainLookupByName(renderedName);

            if (domain.getInfo().state == DomainState.VIR_DOMAIN_SHUTOFF) {
                runContext.logger().info("VM {} is already stopped. Skipping stop.", renderedName);
            } else {
                // Use destroy() for hard power off or shutdown() for force
                if (runContext.render(this.force).as(Boolean.class).orElse(false)) {
                    runContext.logger().info("Calling destroy on {}.", renderedName);
                    domain.destroy();
                } else {
                    runContext.logger().info("Calling shutdown on {}.", renderedName);
                    domain.shutdown();
                }

                if (runContext.render(this.waitForStopped).as(Boolean.class).orElse(false)) {
                    Duration waitDuration = runContext.render(this.timeToWait).as(Duration.class)
                            .orElse(Duration.ofSeconds(60));
                    long end = System.currentTimeMillis() + waitDuration.toMillis();
                    boolean success = false;

                    while (System.currentTimeMillis() < end) {
                        DomainState currentState = domain.getInfo().state;

                        if (currentState == DomainState.VIR_DOMAIN_SHUTOFF) {
                            success = true;
                            break;
                        }

                        // Break if VM hits a state where it will never reach 'Running' without
                        // intervention
                        if (currentState == DomainState.VIR_DOMAIN_PAUSED
                                || currentState == DomainState.VIR_DOMAIN_CRASHED) {
                            throw new Exception(
                                    "VM entered terminal state " + currentState + " while waiting for SHUTOFF");
                        }

                        Thread.sleep(2000); // Poll every 2 seconds
                    }

                    if (!success) {
                        throw new Exception(
                                "Timeout waiting for VM to reach RUNNING state after " + waitDuration.getSeconds()
                                        + "s");
                    }
                }

                runContext.logger().info("Stop signal sent to VM {}.", renderedName);
            }

            return Output.builder()
                    .name(domain.getName())
                    .state(domain.getInfo().state.toString())
                    .build();
        }
    }

    /**
     * Output for the StopVm task.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private String state;
    }
}
