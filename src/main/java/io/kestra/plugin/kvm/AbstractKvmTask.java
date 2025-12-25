package io.kestra.plugin.kvm;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractKvmTask extends Task {
    @Schema(
        title = "Libvirt connection URI",
        description = "Example: 'qemu+ssh://user@host/system' or 'qemu+tls://host/system'"
    )
    protected Property<String> uri;

    @Schema(title = "Connection timeout", description = "Optional timeout for the connection.")
    protected Property<java.time.Duration> connectionTimeout;

    protected Connect getConnection(RunContext runContext) throws Exception {
        String connectionUri = runContext.render(this.uri).as(String.class).orElseThrow();
        // In libvirt-java, authentication and timeouts are primarily handled
        // by the URI parameters or the underlying SSH config.
        return new Connect(connectionUri, false);
    }
}