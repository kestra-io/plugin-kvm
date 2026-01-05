package io.kestra.plugin.kvm;

import org.libvirt.Connect;
import org.libvirt.LibvirtException;

/**
 * A wrapper around a Libvirt {@link Connect} object that implements
 * {@link AutoCloseable}.
 */
public class LibvirtConnection implements AutoCloseable {
    private final Connect connect;

    /**
     * Creates a new Libvirt connection.
     *
     * @param uri The URI to connect to.
     * @throws LibvirtException If the connection fails.
     */
    public LibvirtConnection(String uri) throws LibvirtException {
        this.connect = new Connect(uri);
    }

    /**
     * Gets the underlying {@link Connect} object.
     *
     * @return The {@link Connect} object.
     */
    public Connect get() {
        return this.connect;
    }

    @Override
    public void close() throws LibvirtException {
        if (connect != null) {
            connect.close();
        }
    }
}
