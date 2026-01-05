package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.RetryUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import lombok.*;
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
                  - id: stop_vm
                    type: io.kestra.plugin.kvm.StopVm
                    uri: qemu+ssh://root@167.99.104.163/system
                    name: "kestra-worker-nodes"
                    force: true
                """
        )
    }
)
@Schema(title = "Stop VM")
public class StopVm extends AbstractKvmTask implements RunnableTask<StopVm.Output> {
    @Schema(title = "VM Name")
    @NotNull
    private Property<String> name;

    @Builder.Default
    @Schema(title = "Force Stop")
    private Property<Boolean> force = Property.ofValue(false);

    @Builder.Default
    @Schema(title = "Wait for Stopped state")
    private Property<Boolean> waitForStopped = Property.ofValue(false);

    @Builder.Default
    @Schema(title = "Time to wait")
    private Property<Duration> timeToWait = Property.ofValue(Duration.ofSeconds(60));

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String rName = runContext.render(this.name).as(String.class).orElseThrow();
            Domain domain = conn.domainLookupByName(rName);

            if (domain.getInfo().state == DomainState.VIR_DOMAIN_SHUTOFF) {
                runContext.logger().info("VM {} is already stopped. Skipping stop.", rName);
            } else {
                // Use destroy() for hard power off or shutdown() for force
                if (runContext.render(this.force).as(Boolean.class).orElse(false)) {
                    runContext.logger().info("Calling destroy on {}.", rName);
                    domain.destroy();
                } else {
                    runContext.logger().info("Calling shutdown on {}.", rName);
                    domain.shutdown();
                }

                if (runContext.render(this.waitForStopped).as(Boolean.class).orElse(false)) {
                    Duration rWaitDuration = runContext.render(this.timeToWait).as(Duration.class)
                            .orElse(Duration.ofSeconds(60));

                    RetryUtils.Instance<Boolean, IllegalStateException> retryUtils = new RetryUtils().of(
                            Exponential.builder()
                                    .delayFactor(2.0)
                                    .interval(Duration.ofMillis(100))
                                    .maxInterval(Duration.ofSeconds(2))
                                    .maxAttempts(-1)
                                    .maxDuration(rWaitDuration)
                                    .build()
                    );

                    boolean success;
                    try {
                        success = retryUtils.run(
                                IllegalStateException.class,
                                () -> {
                                    DomainState currentState = domain.getInfo().state;

                                    if (currentState == DomainState.VIR_DOMAIN_SHUTOFF) {
                                        return true;
                                    }

                                    // Break if VM hits a state where it will never reach 'Running' without
                                    // intervention
                                    if (currentState == DomainState.VIR_DOMAIN_PAUSED
                                            || currentState == DomainState.VIR_DOMAIN_CRASHED) {
                                        throw new Exception(
                                                "VM entered terminal state " + currentState
                                                        + " while waiting for SHUTOFF");
                                    }

                                    throw new IllegalStateException("Waiting for VM to reach SHUTOFF state, "
                                            + "current state: " + currentState);
                                }
                        );
                    } catch (Throwable e) {
                        if (e instanceof IllegalStateException) {
                            success = false;
                        } else if (e instanceof Exception ex) {
                            throw ex;
                        } else {
                            throw new Exception(e);
                        }
                    }

                    if (!success) {
                        throw new Exception(
                                "Timeout waiting for VM to reach SHUTOFF state after " + rWaitDuration.getSeconds()
                                        + "s");
                    }
                }

                runContext.logger().info("Stop signal sent to VM {}.", rName);
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
        @Schema(title = "VM Name")
        private String name;

        @Schema(title = "VM State")
        private String state;
    }
}
