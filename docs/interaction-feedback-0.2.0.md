# Overlay Cam 0.2.0 — touch-operation feedback

## Interaction contract

Overlay editing now has two deliberately separate direct-manipulation modes:

| Active pointers | Operation | Applied changes | Visual feedback |
|---:|---|---|---|
| 1 | Move | X/Y translation only | Primary-colored border, move arrows, “Move” badge |
| 2 or more | Resize + rotate | Scale and rotation only | Tertiary-colored border, two-touch/rotation symbol, “Resize + rotate” badge |
| 0 | Idle | No transform | No border or transient badge |

The first event after adding or lifting a finger establishes the new gesture baseline and does not apply a transform delta. This prevents the overlay from jumping when switching between one- and two-finger manipulation.

## Opacity control

During the `EditOverlay` stage, opacity is exposed as a persistent bottom fader below the workflow dock. It remains visible throughout direct manipulation and reports its current percentage. The duplicate opacity slider was removed from the tools sheet so there is one stable control location.

```text
┌──────────────────────────────────────┐
│                                      │
│     camera/background + overlay      │
│                                      │
│        [ active gesture badge ]      │
│                                      │
├──────────────────────────────────────┤
│ workflow stage + primary action      │
├──────────────────────────────────────┤
│ Overlay opacity   70%                 │
│ ───────────────●──────────────        │
└──────────────────────────────────────┘
```

## Accessibility

- Canvas semantics announce the active gesture instruction while contact is in progress.
- The opacity fader has a stable content description and visible numeric value.
- Size and rotation remain available as precision sliders in the fine-tuning sheet.
- Visual modes differ by label, symbol, and color rather than color alone.

## Verification scope

Automated checks cover pointer-count classification and workflow visibility. Docker-backed unit tests, Kotlin compilation, Android lint, and APK assembly are release gates.

Manual device checks remain necessary for:

- one-finger drag latency and edge behavior;
- second-finger transition without jumps;
- pinch and twist stability with thumbs at different distances;
- gesture badge readability over bright and dark imagery;
- fader reachability above gesture navigation and three-button navigation;
- compact-height landscape layouts and large font sizes.
