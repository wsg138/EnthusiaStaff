# Package handoffs

Store one canonical timestamped handoff for each package execution attempt. Link the latest handoff from the package file and registry. Handoffs describe execution state but never override live GitHub or `PACKAGE-REGISTRY.md`.

Use `ai-agents/work-packages/templates/PACKAGE-HANDOFF-TEMPLATE.md`. Never include private databases, raw or reconstructable rows, credentials, raw IPs, private messages, or production routes.
