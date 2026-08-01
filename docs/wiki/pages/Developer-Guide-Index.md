# Developer Guide Index

Use this page as the starting point for code review and development. It links to
the roadmap, source map, focused technical explanations, tests and operational
boundaries.

## Common questions

- **What should be built next?** See [[Development Blueprint]].
- **What is actually proven right now?** See [[Implementation Status]].
- **What does the protocol do?** See [[Protocol and Network Traffic]].
- **What does ReplayGuard protect against?** See
  [[ReplayGuard|Protocol-and-Network-Traffic#replayguard]].
- **Where does outbound traffic go?** See
  [[Outbound traffic map|Protocol-and-Network-Traffic#outbound-traffic-map]].
- **How does vanish actually hide a player?** See [[Vanish Internals]].
- **Which packets does vanish cancel?** See
  [[Packets and Paper visibility|Vanish-Internals#packets-and-paper-visibility]]
  and the current gaps on that page.
- **Where should I begin reviewing the repository?** See
  [[Recommended review order|Developer-Code-Guide#recommended-review-order]].

## Roadmap and status

- [[Development Blueprint]] — visual road to production, milestone gates,
  workstreams and immediate execution order.
- [[Road to production|Development-Blueprint#road-to-production]]
- [[Workstream map|Development-Blueprint#workstream-map]]
- [[Immediate execution order|Development-Blueprint#immediate-execution-order]]
- [[Feature definition of done|Development-Blueprint#feature-definition-of-done]]
- [[Implementation Status]] — exact checkpoint, proven areas, command surfaces,
  blockers and production gates.

## Architecture and repository map

- [[Architecture]] — dependency direction, runtime ownership and durable write flow.
- [[Developer Code Guide]] — practical source map and feature traces.
- [[Recommended review order|Developer-Code-Guide#recommended-review-order]]
- [[Repository map|Developer-Code-Guide#repository-map]]
- [[Root files reviewers should understand|Developer-Code-Guide#root-files-reviewers-should-understand]]
- [[Dependency direction|Developer-Code-Guide#dependency-direction]]

## Runtime entry points

- [[Paper runtime|Developer-Code-Guide#paper-runtime]]
- [[Paper commands|Developer-Code-Guide#commands]]
- [[Velocity runtime|Developer-Code-Guide#velocity-runtime]]
- [[Domain layer|Developer-Code-Guide#domain-layer]]
- [[Authorization boundary|Developer-Code-Guide#authorization-boundary]]
- [[Persistence layer|Developer-Code-Guide#persistence-layer]]
- [[Important JDBC stores|Developer-Code-Guide#important-stores]]

## Network and security

- [[Protocol and Network Traffic]]
- [[Purpose of the protocol|Protocol-and-Network-Traffic#purpose-of-the-protocol]]
- [[Connection topology|Protocol-and-Network-Traffic#connection-topology]]
- [[TLS and authentication|Protocol-and-Network-Traffic#tls-and-authentication]]
- [[Message envelope|Protocol-and-Network-Traffic#message-envelope]]
- [[ReplayGuard|Protocol-and-Network-Traffic#replayguard]]
- [[Acknowledgements and retries|Protocol-and-Network-Traffic#acknowledgements-and-retries]]
- [[Outbound traffic map|Protocol-and-Network-Traffic#outbound-traffic-map]]
- [[External integration boundaries|Developer-Code-Guide#external-integration-boundaries]]

## Vanish and staff-state internals

- [[Vanish Internals]]
- [[Vanish toggle flow|Vanish-Internals#toggle-flow]]
- [[Vanish events|Vanish-Internals#events-handled-directly]]
- [[Visibility decisions|Vanish-Internals#visibility-decisions]]
- [[Packets and Paper visibility|Vanish-Internals#packets-and-paper-visibility]]
- [[Vanish gaps|Vanish-Internals#what-is-not-currently-intercepted]]
- [[Performance and threading|Vanish-Internals#performance-and-threading]]
- [[Staff mode, vanish, and freeze trace|Developer-Code-Guide#staff-mode-vanish-and-freeze]]

## Feature traces

- [[Punishment creation|Developer-Code-Guide#punishment-creation]]
- [[Punishment changes and removal|Developer-Code-Guide#punishment-change-or-removal]]
- [[Reports and evidence|Developer-Code-Guide#reports-and-evidence]]
- [[Inventory inspection and editing|Developer-Code-Guide#inventory-inspection-and-editing]]
- [[Item confiscation and restoration|Developer-Code-Guide#item-confiscation-and-restoration]]
- [[Economy confiscation|Developer-Code-Guide#economy-confiscation]]
- [[Staff mode, vanish, and freeze|Developer-Code-Guide#staff-mode-vanish-and-freeze]]
- [[Alts and network identity|Developer-Code-Guide#alts-and-network-identity]]
- [[Discord delivery|Developer-Code-Guide#discord-delivery]]
- [[LiteBans migration and cutover|Developer-Code-Guide#litebans-migration-and-cutover]]
- [[Website bridge|Developer-Code-Guide#website-bridge]]

## Build, tests and review

- [[Development Setup]]
- [[Build and Testing]]
- [[Tests and where to look|Developer-Code-Guide#tests-and-where-to-look]]
- [[Threading and concurrency rules|Developer-Code-Guide#threading-and-concurrency-rules]]
- [[High-risk review areas|Developer-Code-Guide#high-risk-review-areas]]
- [[Review completion checklist|Developer-Code-Guide#review-completion-checklist]]

## Documentation maintenance

- [[Wiki Maintenance]]
- [Authoritative goals](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md)
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- [Repository development blueprint](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/development-blueprint.md)

When a repeated reviewer question appears, add the answer to a focused technical
page and link it here rather than making reviewers search the repository again.