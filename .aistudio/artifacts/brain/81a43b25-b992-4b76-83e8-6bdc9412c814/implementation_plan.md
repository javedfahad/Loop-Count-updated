# Implementation Plan: Redesign Loopify "Now Playing" Screen

Redesign the "Now Playing" screen in Loopify to be responsive across all screen formats (phones, flips, foldables, tablets, portrait, and landscape), matching the exact vertical layout and visual requirements.

## Proposed Changes

### 1. Responsiveness & Adaptive Sizing
- Use `BoxWithConstraints` to dynamically read available width and height without hardcoded pixel sizes.
- Compute album art size using the formula:
  `artSize = min(maxWidth * 0.62f, maxHeight * 0.35f)`
- Dynamic scaling factor based on width for padding, icons, and corner radii.
- **Two-Pane Layout** when `maxWidth >= 600.dp` or in Landscape (`maxWidth > maxHeight`):
  - **Left Pane:** Album Art card, bold track title, artist subtitle.
  - **Right Pane:** Ringtone | Shuffle pill, 2x 3x 4x speed pill, Repeat Count card, Custom Seekbar, Playback Controls.
- **Single-Screen Portrait Mode:**
  - Vertically ordered content with `verticalScroll` fallback so controls stay reachable on small/flip devices.
  - No playback restart across orientation / fold changes.

### 2. Layout (Top to Bottom)
1. **Top Bar**:
   - Chevron-down icon on the left (`Icons.Default.KeyboardArrowDown`).
   - "NOW PLAYING" in small letter-spaced caps.
   - Song title below it (one single line with ellipsis).
   - Removed headphone icon and Dual Listen action.
2. **Album Art Card**:
   - Big rounded square with diagonal gradient, faint circular grooves, music-note icon in a circular badge, and track title & artist displayed inside.
3. **Song Title & Artist**:
   - Big bold song title with artist below it.
4. **Ringtone | Shuffle Pill**:
   - One rounded pill with two buttons: `Ringtone` | `Shuffle`, separated by a thin vertical divider.
   - Ringtone opens the existing `RingtoneDialog`.
   - Shuffle toggles playback shuffle state with active highlight.
5. **Speed Pill (2x 3x 4x)**:
   - Small capsule pill with text-only options: `2x`, `3x`, `4x`.
   - Selected option highlighted; stays active until another is tapped (per user answer).
6. **Repeat Count Card**:
   - Tappable rounded card with repeat icon in a circle, `"REPEAT COUNT"` label, `"Off"` / repeat count status below it, and `"Tap to set repeat"` on the right.
   - Tapping opens the existing `RepeatCountDialog`.
7. **Custom Seekbar**:
   - Thin track.
   - Thin vertical bar thumb with a small dot at the end.
   - Left time (`00:00`) and right total time (`00:16`).
8. **Controls Row**:
   - Replay-10 (`Icons.Default.FastRewind`), Previous (`Icons.Default.SkipPrevious`), big Play/Pause with soft glow (`Icons.Default.PlayArrow` / `Icons.Default.Pause`), Next (`Icons.Default.SkipNext`), Forward-10 (`Icons.Default.FastForward`).
   - Curved rectangle container with subtle press animations.

### 3. Cleanup & Removals
- Remove headphone icon from top bar.
- Remove "Loopify Audio" / folder subtitle from top bar.
- Remove standalone "Dual Listen" button.
- Remove "SPEED 1.0x" label.
- Remove extra background boxes behind speed buttons and controls.
- Strictly adhere to existing theme colors (`MaterialTheme.colorScheme`, `SunsetPink`, `NeonCyan`).

## Verification Plan
1. Run `compile_applet` to verify compilation with 0 errors.
2. Run `gradle :app:testDebugUnitTest` to ensure all existing tests pass.
3. Verify test tags and functionality (playback control, speed selector, repeat dialog, ringtone cutter dialog) are fully operational.
