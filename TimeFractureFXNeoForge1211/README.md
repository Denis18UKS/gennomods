# TimeFractureFX 0.4.0 shader alpha (NeoForge 1.21.1)

Two registered runtime GLSL shader programs: `timefracture_portal`, `timefracture_rift`. NO particles are used in this build. The **world-space** triangle geometry is attached to fixed X/Y/Z coordinates. Portal: filled shader-rendered sky vortex + ~42-block tapered 3D tunnel with glowing rim. Rift: jagged scene-filled aperture + ~4.5-block extruded illuminated rim. Window: small rift with PROCEDURAL fake alternate landscape, NOT a true view of another timeline (separate dimension system is not implemented). 

## Client commands
`/tfx preview portal`, `/tfx preview rift`, `/tfx preview window`, `/tfx preview close`, `/tfx preview collapse`, `/tfx preview stop`, `/tfx preview info`.

Use `/tfx preview info` to confirm both shaders were registered. If they show false, inspect latest.log. These are client-only preview commands and local effects, NOT synced to server.

## Known limitations
Untested visually in Minecraft. Shader code and Java may compile, but actual on-screen result, depth sorting, Iris/Sodium compatibility, and shader runtime errors require in-game testing. The shader-created landscape is not an actual alternate world's block geometry.
