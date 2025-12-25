package io.kestra.plugin.kvm;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import java.time.Duration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Abstract task for KVM operations.
 *
 * <p>
 * This class provides common properties and methods for connecting to a Libvirt
 * instance.
 */
@SuperBuilder
@NoArgsConstructor
@Getter
public abstract class AbstractKvmTask extends Task {
    @PluginProperty(dynamic = true)
    protected String uri;

    @PluginProperty
    protected Duration connectionTimeout;

    /**
     * Creates a connection to the Libvirt instance.
     *
     * @param runContext The run context.
     * @return A {@link LibvirtConnection} object.
     * @throws Exception If an error occurs while connecting.
     */
    protected LibvirtConnection getConnection(RunContext runContext) throws Exception {
        return new LibvirtConnection(runContext.render(this.uri));
    }
}
