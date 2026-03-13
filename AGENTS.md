# Kestra KVM Plugin

## What

description = 'KVM plugin for Kestra Exposes 7 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with KVM, allowing orchestration of KVM-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
