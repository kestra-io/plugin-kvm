# Kestra KVM Plugin

## What

- Provides plugin components under `io.kestra.plugin.kvm`.
- Includes classes such as `CreateVm`, `LibvirtConnection`, `VmEventTrigger`, `StartVm`.

## Why

- This plugin integrates Kestra with KVM / Libvirt.
- It provides manage KVM virtual machines using the Libvirt API.

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
