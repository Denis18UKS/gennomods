# TimeFracture 0.10.0 — performance guard and fixed-world 3D portal (NeoForge 1.21.1)

## Main priorities
- PREVENT LARGE TPS SPIKES: branch snapshot preparation requests chunk futures, avoids blocking getChunk() in the copy loop, and copies no more than 64 blocks or 1 ms of work per server tick. Loaded source/target chunks are required before copying. Portal chunk tickets are released after the job.
- FPS safety: volumetric portal starts at 2 samples, auto-scales between 1 and 4 samples based on measured frame intervals. Second costly 3D noise is skipped below 3 samples.
- Emergency GPU circuit breaker: after three consecutive 180+ ms frame gaps, pause expensive portal rendering for 5 seconds then resume with a single sample.
- World anchored: portal center is captured as XYZ exactly when /tfx preview portal is entered. It never follows the camera position or rotation. Rendering uses vertex coordinates and center in the SAME camera-relative coordinate system; actual world location is stable.
- Preview FX is isolated to the dimension where the portal was opened. Traveling into another timeline clears the old previews rather than drawing stale geometry.
- 3D sphere mesh reduced from 18x36 to 10x28 segments to limit CPU overhead.
- FPS controls: /tfx preview perf, /tfx preview auto, /tfx preview low, /tfx preview medium, /tfx preview high, /tfx preview off, /tfx preview where.
- Layer snapshot profiling: /timefracture layers now shows copy progress, last and worst microseconds spent copying blocks per tick.

## Important limitations (NOT DONE)
- Full infinite-chunk copy-on-write worlds for all dimensions: NOT implemented. Eight existing slots with one chunk initial inheritance.
- True AI players with frozen skin, autonomous development, complex Create factories and flying airships: NOT implemented.
- Bi-directional physically traversable portals, remote timeline live-world rendering: NOT implemented.
- Robust full-world rewind and merge of all new time dimensions: NOT implemented. Old Create condenser merges LEGACY pocket samples only.
- Exact pixel reproduction of supplied cosmic screenshot: cannot guarantee without the original 3D scene/shader sources. The current GLSL is original volumetric synthesis.
- No possible implementation can *guarantee* zero FPS stalls in all hardware / mod combinations. This patch puts hard limits on work triggered by TimeFracture and adaptive shader protection, but other Minecraft activity and chunk generation can still stall. Test with spark profiler and F3 FPS.

## Test
1. Make an entire WORLD backup; remove all previous TimeFracture and TimeFractureFX .jars.
2. Install Create for NeoForge 1.21.1 and optionally JEI.
3. /timefracture focus <onlinePlayer>; /timefracture branch -; /timefracture layers
4. /timefracture enter T-1 then /timefracture return
5. /tfx preview portal; /tfx preview where; move 50 blocks and /tfx preview where again. Anchor XYZ must remain identical while distance changes.
6. /tfx preview perf (check FPS≈ and sample quality). /tfx preview low and /tfx preview off are immediate fallback.
7. /tfx preview stop

## Validated scope
GitHub Actions build and dedicated server boot tests can confirm compilation, resources and command registration only. They cannot certify client FPS, GPU driver behavior or in-game correct display of the portal.
