# Android Liquid Glass integration

The shared Android UI is implemented in `apps/glass-ui` and consumed by both Android apps.

- Upstream: [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- Integrated upstream release: **2.0.1**
- Rendering/effect foundation: Backdrop primitives from the upstream library.
- Upstream sample components used: LiquidBottomTabs, LiquidButton, LiquidToggle, LiquidSlider, and scroll/backdrop examples.
- The upstream sample components are source examples rather than high-level Maven artifacts; their source is retained/adapted in the shared module with upstream licensing notices.
- Theme changes are bridged to the app's existing persisted appearance setting.
- App-owned prompts use the shared Liquid Glass dialog; Android OS-owned UI remains native.

The UI layer does not change the pairing protocol, signaling service, authorization model, or WebRTC transport.
