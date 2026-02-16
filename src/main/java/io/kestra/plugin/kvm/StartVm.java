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
import org.libvirt.DomainInfo;
import org.libvirt.DomainInfo.DomainState;

/**
 * Task to start a KVM Virtual Machine.
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
                  - id: start_vm
                    type: io.kestra.plugin.kvm.StartVm
                    uri: qemu+ssh://root@167.99.104.163/system
                    name: "kestra-worker-nodes"
                    waitForRunning: true
                """
            )
    }
)
@Schema(
    title = "Start a KVM domain",
    description = "Boots a libvirt domain if it isn't already running. Can optionally wait until the domain reaches RUNNING state using exponential retry up to timeToWait (default PT60S). Requires access to the target libvirt URI."
)
public class StartVm extends AbstractKvmTask implements RunnableTask<StartVm.Output> {
    @Schema(
        title = "Domain name",
        description = "Name of the libvirt domain to start."
    )
    @NotNull
    private Property<String> name;

    @Builder.Default
    @Schema(
        title = "Wait for RUNNING",
        description = "If true, polls domain state until RUNNING or timeout. Default false."
    )
    private Property<Boolean> waitForRunning = Property.ofValue(false);

    @Builder.Default
    @Schema(
        title = "Max wait duration",
        description = "Maximum time to wait for RUNNING when waitForRunning is true. Default PT60S."
    )
    private Property<Duration> timeToWait = Property.ofValue(Duration.ofSeconds(60));

    @Override
    public Output run(RunContext runContext) throws Exception {
        try (LibvirtConnection connection = getConnection(runContext)) {
            Connect conn = connection.get();
            String rName = runContext.render(this.name).as(String.class).orElseThrow();
            Domain domain = conn.domainLookupByName(rName);
            DomainInfo info = domain.getInfo();

            if (info.state == DomainState.VIR_DOMAIN_RUNNING) {
                runContext.logger().info("VM {} is already running. Skipping start.", rName);
            } else {
                domain.create();
                runContext.logger().info("VM {} started successfully.", rName);

                if (runContext.render(this.waitForRunning).as(Boolean.class).orElse(false)) {
                    Duration rWaitDuration = runContext.render(this.timeToWait).as(Duration.class)
                            .orElse(Duration.ofSeconds(60));

                    RetryUtils.Instance<Boolean, IllegalStateException> retryUtils = RetryUtils.of(
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

                                    if (currentState == DomainState.VIR_DOMAIN_RUNNING) {
                                        return true;
                                    }

                                    // Break if VM hits a state where it will never reach 'Running' without
                                    // intervention
                                    if (currentState == DomainState.VIR_DOMAIN_PAUSED
                                            || currentState == DomainState.VIR_DOMAIN_CRASHED
                                            || currentState == DomainState.VIR_DOMAIN_SHUTOFF) {
                                        throw new Exception(
                                                "VM entered terminal state " + currentState
                                                        + " while waiting for RUNNING");
                                    }

                                    throw new IllegalStateException("Waiting for VM to reach RUNNING state, "
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
                                "Timeout waiting for VM to reach RUNNING state after " + rWaitDuration.getSeconds()
                                        + "s");
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
        @Schema(
            title = "VM Name",
            description = "Started domain name."
        )
        private String name;

        @Schema(
            title = "VM State",
            description = "Libvirt domain state after the start attempt."
        )
        private String state;
    }
}
