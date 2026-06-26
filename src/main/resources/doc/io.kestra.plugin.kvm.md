# How to use the KVM plugin

Manage KVM virtual machines through the Libvirt API from Kestra flows: create, start, stop, update, delete, and list VMs, and react to VM events.

## Connection

Tasks connect to a Libvirt daemon through a connection URI (for example `qemu:///system` for the local host or `qemu+ssh://user@host/system` for a remote host). Provide any required credentials as [secrets](https://kestra.io/docs/concepts/secret) and share connection settings with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`CreateVm` defines and creates a VM, `StartVm` and `StopVm` control its power state, `UpdateVm` changes its configuration, and `DeleteVm` removes it. `ListVms` returns the known VMs with their UUID and state, and supports a status filter.

`VmEventTrigger` starts a flow when a VM lifecycle event occurs.
