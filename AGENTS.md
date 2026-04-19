# Kestra KVM Plugin

## What

- Provides plugin components under `io.kestra.plugin.kvm`.
- Includes classes such as `CreateVm`, `LibvirtConnection`, `VmEventTrigger`, `StartVm`.

## Why

- What user problem does this solve? Teams need to manage KVM virtual machines using the Libvirt API from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps KVM / Libvirt steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on KVM / Libvirt.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `kvm`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.kvm.CreateVm`
- `io.kestra.plugin.kvm.DeleteVm`
- `io.kestra.plugin.kvm.ListVms`
- `io.kestra.plugin.kvm.StartVm`
- `io.kestra.plugin.kvm.StopVm`
- `io.kestra.plugin.kvm.UpdateVm`
- `io.kestra.plugin.kvm.VmEventTrigger`

### Project Structure

```
plugin-kvm/
├── src/main/java/io/kestra/plugin/kvm/
├── src/test/java/io/kestra/plugin/kvm/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
