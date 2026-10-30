# Overlay Cam — compact on-demand controls

The camera or frozen composition is the primary canvas. The always-visible
alignment control bar provides **Blend** and **Frozen View (0.5×–20×)**
without opening a modal or requiring a second finger. The right-side
**Freeze position / Reposition** toggle changes the gesture target.

The **Tools** panel is reserved for advanced operations: precise overlay
size/rotation, spring/value XYZ faders, image processing, multiple layers,
point matching and optional OBS/streaming. Instead of an almost full-screen
(90%-height) modal, it caps at 52% of the device height and allows partial
expansion and vertical scrolling. Tapping Done or dismissing restores the
unobstructed view. This avoids accidentally hiding the object being aligned.

Camera chrome can also be hidden explicitly via the **Hide** action, leaving
a small **Controls** recovery button. Stage navigation and recent captures
are available on the top rail, not nested in the advanced tools.

The modal nature of the advanced sheet still prevents gesture editing
behind the sheet. For fully interactive picture-in-picture editing, a future
non-modal inspector panel would be required. Physical touch testing on a
narrow screen and accessibility fonts remains necessary.
