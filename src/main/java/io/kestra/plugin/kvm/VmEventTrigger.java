package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;

/**
 * Trigger that polls a KVM Virtual Machine state.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: monitor_kvm_vm
                namespace: kvmtest.ssh

                tasks:
                  - id: alert
                    type: io.kestra.plugin.core.log.Log
                    message: |
                        Name: {{ render(trigger.name | json) }}
                        State: {{ render(trigger.state | json) }}

                triggers:
                  - id: watch_vm
                    type: io.kestra.plugin.kvm.VmEventTrigger
                    uri: qemu+ssh://root@167.99.104.163/system
                    name: kestra-worker-nodes
                    interval: PT1M
                """
            )
    }
)
@Schema(
    title = "Poll KVM domain state",
    description = "Polling trigger that connects to libvirt on the given URI, reads a domain's state on each interval, and emits an execution with the name and current state. Logs and skips when lookup fails."
)
public class VmEventTrigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<VmEventTrigger.Output> {

    @Builder.Default
    @Schema(
        title = "Poll interval",
        description = "Time between state checks. Default PT1M."
    )
    private Duration interval = Duration.ofMinutes(1);

    @Schema(
        title = "Libvirt URI",
        description = "Connection URI rendered before use; required to reach the hypervisor."
    )
    protected Property<String> uri;

    @Schema(
        title = "Domain name",
        description = "Name of the libvirt domain to monitor."
    )
    @NotNull
    private Property<String> name;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        String rUri = runContext.render(this.uri).as(String.class).orElse(null);
        String rName = runContext.render(this.name).as(String.class).orElseThrow();

        try (LibvirtConnection connection = new LibvirtConnection(rUri)) {
            Connect conn = connection.get();
            Domain domain = conn.domainLookupByName(rName);
            String currentState = domain.getInfo().state.toString();

            var output = Output.builder()
                    .name(rName)
                    .state(currentState)
                    .build();
            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);

            return Optional.of(execution);
        } catch (Exception e) {
            runContext.logger().error("KVM Trigger failed for VM {}: {}", rName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Output for the VmEventTrigger.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "VM Name",
            description = "Monitored domain name."
        )
        private String name;

        @Schema(
            title = "VM State",
            description = "Libvirt domain state at the time of polling."
        )
        private String state;
    }
}
