# Android TV Reference UI

This branch is dedicated to the Android TV / H96 Max interface rebuild.

## Locked visual specification

- Target: Android TV / H96 Max first
- Canvas: 16:9 landscape; design baseline 1920x1080 with 1280x720 support
- Approved reference screenshot is the visual master
- Preserve black / gunmetal surfaces, cyan-blue illumination, white text, and orange accent
- Use the genuine ShadowFox TV logo asset
- Improve typography without redesigning the approved composition
- Do not optimize this pass for portrait/mobile/tablet

## Required dashboard composition

1. Metallic/dark top header with ShadowFox TV branding and ROOTED PRO MODE
2. Fixed left navigation rail
   - OPTIMIZE
   - APPS
   - NETWORK
   - SYSTEM
3. Four top telemetry cards
   - OPTIMIZATION SCORE
   - RAM USAGE
   - RUNNING APPS
   - NETWORK
4. SMART OPTIMIZE hero panel with OPTIMIZE action
5. CACHE CLEANER panel with CLEAN NOW action
6. ULTIMATE CENTER panel
7. THERMAL + PERFORMANCE status strip
8. Bottom ShadowFox TV footer

## Interaction specification

- D-pad / remote is the primary input method.
- Every actionable component must expose an obvious focused state.
- Focus treatment: illuminated cyan-blue border/glow matching the approved reference.
- Back navigation must be deterministic and must never strand focus.
- Do not use a static screenshot as the application UI; cards, meters, values and buttons remain functional Compose components.

## Existing backend to retain

The UI replacement must preserve the current working backend from MainActivity.kt:

- root shell detection and root commands
- AppOptimizer
- RAM telemetry
- storage telemetry
- running-process telemetry
- cache trimming
- network throughput / latency measurements
- device and Android version information
- ShadowFoxUpdateGate

## Implementation rule

The existing System Scan / RAM Booster / Network Monitor dashboard is legacy presentation and will be replaced. Backend behavior should be separated from the new TV presentation rather than rewritten unnecessarily.
