# Third-party notices

## AndroidLiquidGlass / Backdrop 2.0.1

The following component implementations and interaction utilities are copied from the upstream AndroidLiquidGlass repository and are retained in their original package namespace:

- `LiquidBottomTabs`
- `LiquidBottomTab`
- `LiquidButton`
- `LiquidToggle`
- `LiquidSlider`
- `DampedDragAnimation`
- `DragGestureInspector`
- `InteractiveHighlight`

Upstream: https://github.com/Kyant0/AndroidLiquidGlass/tree/2.0.1

The upstream library is distributed under the Apache License, Version 2.0. A copy of that license is included in this directory as `AndroidLiquidGlass-LICENSE.txt`.

MEIZZO adaptation note: the copied LiquidBottomTabs, LiquidToggle, and LiquidSlider files replace their system-theme lookup with a CompositionLocal so the app's in-app appearance setting controls their colors. The copied DampedDragAnimation also replaces the upstream deprecated awaitFrame call with Compose's current withFrameNanos API for compatibility. No Liquid Glass rendering or interaction effect has been reimplemented.

The published runtime dependency `io.github.kyant0:backdrop:2.0.1` and `io.github.kyant0:shapes:1.2.1` remain external Maven dependencies.
