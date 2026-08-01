# Overlay Cam 0.3.0 — comparison workspace

## Product intent

The comparison workspace helps a person inspect a drawing against a reference without repeatedly entering menus or losing a carefully aligned transform. It provides visual evidence and prompts for observation; it does not assign an objective score to artistic quality.

## Primary interaction states

### Position unlocked

- One finger moves the reference overlay.
- Two fingers resize and rotate the reference overlay.
- A primary-colored border and operation badge confirm movement.
- A tertiary-colored border and operation badge confirm resize/rotation.
- The floating control freezes the aligned transform without completing the workflow.

### Position frozen

- The overlay transform cannot change.
- One finger pans the complete drawing/reference comparison.
- Two fingers zoom around the gesture centroid.
- The floating control returns to repositioning and resets the inspection viewport so alignment manipulation remains predictable.
- The tools sheet provides an explicit reset-view action.

### Review

After **Use this position**, the same comparison rail remains visible. The user can save, change guides, pan and zoom, or use the floating control to reopen the latest overlay for repositioning.

## Persistent comparison rail

The bottom surface combines the workflow action and the controls needed during comparison:

1. current stage and primary action;
2. tools access;
3. continuous `Drawing ↔ selected view` fader;
4. direct filter chips;
5. compact metrics and the current instructional prompt when a guide is active.

The rail uses one row for actions on wider screens and separates actions on screens narrower than 420 dp to preserve touch targets and prevent truncation.

## Guide modes

### Overlay

The fader controls reference opacity. At 0%, only the drawing/background is shown; at 100%, the reference is shown at full strength.

### Similarity

- Green: pixels are within the chosen similarity threshold.
- Yellow to red: increasing difference.
- **Accuracy**: percentage of compared reference pixels within the selected threshold.
- **Similarity**: average RGB similarity over visible reference pixels.

Suggested use: inspect broad proportion, placement, color, and value agreement. Warm regions indicate where another visual check is worthwhile.

### Value difference

- Quiet translucent marks: already close.
- Bright warm marks: larger lightness or color difference.

Suggested use: find overly light, dark, warm, or cool areas after the major shapes are aligned.

### Contours

- Green: detected contours overlap.
- Cyan: a contour is present in the drawing/background but absent from the reference at that location.
- Magenta: a contour is present in the reference but absent from the drawing/background at that location.

Suggested use: inspect silhouette, feature placement, and line direction. The detector is deliberately lightweight and may respond to texture or lighting edges.

## Accuracy caveat

The metrics compare rendered pixels after cropping, alignment, and scaling. They are sensitive to lighting, paper color, line style, camera exposure, and reference transparency. They should be interpreted as navigation aids, not as grades or aesthetic judgments.

## Performance contract

- Live guides are debounced by 120 ms during transform changes.
- Live analysis is limited to a 720-pixel longest edge and scaled to the viewport.
- Export analysis is limited to a 1440-pixel longest edge and then filtered to the output dimensions.
- Temporary analysis bitmaps are recycled in `finally` blocks.

## Manual device validation

- Verify that adding or lifting a second finger does not jump the overlay or viewport.
- Check pan and centroid zoom at the minimum and maximum zoom levels.
- Test the floating mode control with left- and right-handed grips.
- Test the bottom surface at 320–420 dp widths, large font settings, and landscape orientation.
- Inspect guide readability over bright, dark, monochrome, and highly textured drawings.
- Confirm the fader clears gesture navigation and three-button navigation insets.
