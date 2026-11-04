# TimeFractureFX 0.5.0 — world-space shader geometry repair

NeoForge **1.21.1**, Java 21, client-side preview.

## Changes from 0.4

- Portal no longer has a continuous purple cone. A true world-positioned sky aperture uses the portal GLSL program, surrounded by nine vertically separated narrow, animated energy rings and three slim spiralling ribbons. They have real world X/Y/Z depth.
- The shader receives a monotonic elapsed-time float (previously a huge UNIX float, which lost sub-second precision).
- Rift/window use ONE fixed, non-self-intersecting contour shared across the scene and both sides of the border. Width is **0.31 / 0.58 Minecraft blocks** (scaled by opening animation), not a multiplier of the whole aperture radius.
- Aperture background has its own procedural alternate-world landscape. A live view of an ACTUAL independent timeline is not yet implemented in TimeFracture.
- Animation: open, close, collapse. Two custom core GLSL shaders are still used; no particle stand-ins.

## Client commands

`/tfx preview info` — shader-load status.
`/tfx preview portal` — sky aperture at Y+58.
`/tfx preview rift` — vertical tear Y+24.
`/tfx preview window` — smaller tear Y+4.
`/tfx preview close`, `collapse`, `stop`.

## Testing

GitHub Actions compiles the project, but **Minecraft in-game visuals, runtime shader resource loading and compatibility with Iris/Sodium still need testing**. Run on a backed-up world and send screenshot/latest.log if problems appear.
