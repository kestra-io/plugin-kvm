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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;

/**
 * Trigger that polls a KVM Virtual Machine state.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
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
@Schema(title = "VM Event Trigger")
public class VmEventTrigger extends AbstractTrigger
        implements PollingTriggerInterface, TriggerOutput<VmEventTrigger.Output> {

    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Schema(title = "Libvirt URI")
    protected Property<String> uri;

    @Schema(title = "VM Name")
    private Property<String> name;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        String renderedUri = runContext.render(this.uri).as(String.class).orElse(null);
        String renderedName = runContext.render(this.name).as(String.class).orElseThrow();

        try (LibvirtConnection connection = new LibvirtConnection(renderedUri)) {
            Connect conn = connection.get();
            Domain domain = conn.domainLookupByName(renderedName);
            String currentState = domain.getInfo().state.toString();

            var output = Output.builder()
                    .name(renderedName)
                    .state(currentState)
                    .build();
            Execution execution = TriggerService.generateExecution(this, conditionContext, context, output);

            return Optional.of(execution);
        } catch (Exception e) {
            runContext.logger().error("KVM Trigger failed for VM {}: {}", renderedName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Output for the VmEventTrigger.
     */
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String name;
        private String state;
    }
}