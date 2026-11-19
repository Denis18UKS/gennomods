# Project: First Contact — 0.1.0 (prototype)

Independent story mod for Minecraft 1.21.1 / NeoForge / Java 21. Author: Zunserz.

### Implemented

- Custom 3D retro wooden/brass radio-receiver block with a model of multiple cuboids, speaker grille, tuning window and two knobs.
- Right-click GUI: tune to 98.7 MHz, replay Morse ... - --- .--. (STOP), read first decoded message.
- Story triggers after BOTH radar calibration/rotation and spaceship readiness. Spaceship detection is a test operator command for now.
- SavedData persists world story flags and individual radio frequencies across restarts. The radio archive delivers a message after 60 seconds of game time, without notifying chat.
- Built-in JEI category draws real rotating 3D block models of the Create-based Ø13 radar, using the same block positions used for calibration.
- Radar uses real Create Mechanical Bearing and Create kinetic power. The mod detects speed on the bearing via reflection (without a hard API compile dependency).
- Works in multiplayer; no forced two-player online requirement.

### How to test

1. Install First Contact, Create 6.0.8–6.0.x, NeoForge 1.21.1 on the server and client. JEI 19.x on client for 3D blueprint.
2. Give yourself the radio: /give @s firstcontact:radio_receiver. Place and right-click; by default it is tuned to 98.7 MHz.
3. In JEI check the Create Radar Schematic category. Build the copper Ø13 dish atop an UP-facing Create Mechanical Bearing, then bond the parts with Create Super Glue for physical rotation.
4. While it is stopped, stand near the bearing and run /firstcontact radar calibrate. The server checks all expected blocks. Correct any missing blocks.
5. Power the Create bearing and check /firstcontact status. If moving=false, verify actual kinetic motion; during API compatibility tests an operator can instead run /firstcontact radar test_motion.
6. An operator enters /firstcontact ship ready to simulate completion of the spacecraft. After 60 seconds of in-game time, the receiver archives an incoming message.
7. Open the receiver, replay Morse and decode. For immediate testing: /firstcontact radio deliver; for clearing the signal: /firstcontact radio reset.

### Prototype limitations

- The spacecraft is not yet recognized by a structure checker; ship ready is an OP-only developer toggle.
- JEI preview is an actual rotating 3D view, but there is not yet an interactive layer selection or .nbt export for Schematicannon.
- Animation, glue and rotational mechanics are performed by Create; no custom spinning radar entity is supplied.
- The Morse preview uses Minecraft note-block sounds; these are short tones, not a generated continuous CW oscillator.
- No subsequent chapters, alien NPCs, animal behavior changes, or new dimensions yet.
- Other existing mods in this repository are unchanged.
