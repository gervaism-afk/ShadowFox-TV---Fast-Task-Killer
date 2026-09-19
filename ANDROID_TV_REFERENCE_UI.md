# Android TV Reference UI

This branch is the implementation track for the approved ShadowFox TV Android TV dashboard.

## Locked target
- Android TV / H96 Max first.
- 16:9 master canvas; 1920x1080 primary, 1280x720 supported.
- Approved reference screenshot is the visual specification.
- Preserve the existing optimizer/root/device telemetry backend.
- Use the real ShadowFox logo asset.
- Dark gunmetal/black surfaces with cyan/blue illumination and restrained orange accents.
- Consistent D-pad focus treatment.

## Dashboard structure
1. Metallic/dark header with ShadowFox branding and device/root status.
2. Narrow left navigation rail.
3. Four telemetry cards: optimization, RAM, running apps, network.
4. Smart Optimize hero panel.
5. Cache Cleaner panel.
6. Ultimate Center panel.
7. Thermal + Performance strip.
8. Footer/status strip.

## Deliberately deferred
- Mobile/portrait responsive layout.
- Tablet-specific layout.
- Visual redesigns that depart from the approved reference.
