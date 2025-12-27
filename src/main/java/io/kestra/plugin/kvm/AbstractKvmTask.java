package io.kestra.plugin.kvm;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

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
    @Schema(title = "Libvirt URI")
    protected Property<String> uri;

    /**
     * Creates a connection to the Libvirt instance.
     *
     * @param runContext The run context.
     * @return A {@link LibvirtConnection} object.
     * @throws Exception If an error occurs while connecting.
     */
    protected LibvirtConnection getConnection(RunContext runContext) throws Exception {
        String renderedUri = runContext.render(this.uri).as(String.class).orElse(null);
        return new LibvirtConnection(renderedUri);
    }

    /**
     * Retrieves a Libvirt domain by name.
     *
     * @param conn The Libvirt connection.
     * @param name The name of the domain.
     * @return The Domain object, or null if not found.
     */
    protected Domain getDomain(Connect conn, String name) {
        try {
            return conn.domainLookupByName(name);
        } catch (LibvirtException e) {
            return null;
        }
    }
}
