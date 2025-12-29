package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(title = "Start VM")
public class StartVm extends AbstractKvmTask implements RunnableTask<StartVm.Output> {
    @Schema(title = "VM Name")
    private Property<String> name;

    @Builder.Default
    @Schema(title = "Wait for Running state")
    private Property<Boolean> waitForRunning = Property.ofValue(false);

    @Builder.Default
    @Schema(title = "Time to wait")
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
                    long end = System.currentTimeMillis() + rWaitDuration.toMillis();
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
        @Schema(title = "VM Name")
        private String name;
        @Schema(title = "VM State")
        private String state;
    }
}
