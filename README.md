# Dove Agent

A Java agent for **Minecraft 1.17.1, 1.18.2, 1.19.4 and 1.20.1 on Fabric**, running on **Java 25**, for Dove Client.

One codebase covers the four versions: the patches target Fabric Loader (0.19.5 on all of them)
and DataFixerUpper calls, selected by what the code calls rather than by Minecraft's obfuscated
names, so each applies only where its target exists.

## What it does

| Patch | Applies to | Effect |
|---|---|---|
| `LazyDataFixerPatch` | 1.17.1, 1.18.2 (Minecraft's `DataFixers`, found by its `DataFixerBuilder.build(Executor)` call) | These versions warm DataFixerUpper's whole rule cache on every start, on the background threads, for rules only needed to open a world saved by an older version. The tasks only fill a cache and drop their results, so they are skipped and each rule is built the first time a fix needs it, as the LazyDFU mod does. **Title screen about 9-10 s sooner.** 1.19.4 and 1.20.1 already start DataFixerUpper unoptimized (Mojang's own fix), so the patch finds nothing to change there. |
| `JarFileSystemPatch` | all four (Fabric Loader's `FileSystemUtil`) | Mod jars are opened read-only where Fabric only reads them, skipping a slow Windows writability check: about 0.35-0.45 s less to the title screen (measured inside the game on 1.21.x, same Fabric Loader). Fabric's one writer (the runtime mod remapper) keeps its arguments. |

Off switch for both: `fastStartup=false`.

Title screen, Java 25 with ZGC, agent with the changes off and then on (after a warm-up start):

| Version | Off | On |
|---|---|---|
| 1.17.1 | 23.5 s | 14.8 s |
| 1.18.2 | 23.6 s | 13.5 s |

The same change on 1.14.4 left the frame rate unchanged (2 alternating rounds: 88.9 vs 87.9 FPS
still, 87.8 vs 86.2 turning) and opened a world saved by an older version correctly; see the
1.14.4-1.16.5 agent.

## What was checked

**Java 25 needs no fixes on these versions** (built for Java 16 and 17):

- The jar scan (34,000-40,000 classes a version with FPS Boost, nested jars included) finds nothing
  removed between Java 21 and 25 that runs: only ImmediatelyFast's bundled Reflect library names
  Java's internal ASM, in a fallback it doesn't use on Java 25. The Reflect calls ImmediatelyFast
  makes (`Objects.cast` in 1.19.4's HUD batching, `Objects.allocate` for maps) were run on Java 21
  and 25 with the same result. Older Java's removed APIs are only used by code these games never
  run (log4j's email, JMS and database add-ons, JNA's transparent-window helper, Netty's
  self-signed certificate helper).
- A session on each version with the agent and FPS Boost (create a world, play, save, quit, then
  open it again, play, close the window): all exit cleanly. ZGC on 1.20.1: longest pause 0.24 ms,
  no allocation stalls.
- **Frame rate:** no agent change measured as a gain on the versions profiled (1.14.4-1.21.11): with
  FPS Boost the time is in the graphics driver, without it in Minecraft's own rendering, which is
  Sodium's job rather than an agent's. Not A/B'd again here, to keep testing short.

## Building

Requires nothing but a JDK to run Gradle; the build downloads a JDK 25 toolchain if one isn't installed.

```bash
./gradlew build
```

The agent is `build/libs/dove-agent.jar`. (The `-slim` jar is the same code without ASM shaded
in; don't use it as the agent.)

## Using

```
-javaagent:/path/to/dove-agent.jar=log=<minecraft dir>/logs/dove-agent.log
--enable-native-access=ALL-UNNAMED
--sun-misc-unsafe-memory-access=allow
```

Agent options (comma-separated after `=`):

| Option  | Default                       | Meaning                                       |
|---------|-------------------------------|-----------------------------------------------|
| `log`   | console only                  | file to also write the agent log to           |
| `cache` | `<java.io.tmpdir>/dove-agent` | directory for cached work                     |
| `debug` | `false`                       | verbose logging (also `-Ddove.debug=true`)    |
| `fastStartup` | `true`                  | `false` turns off the startup changes         |
| `timer` | `false`                       | `true` asks Windows for a 1 ms timer          |
| `renderPriority` | `false`              | `true` raises the render thread's priority    |

## Testing tools

- `./gradlew apiScan --args="--jdk21 <JDK 21 home> [--where java.io.File.delete] [--out report.txt] <jar or folder>..."`
  lists every Java API the given jars (and the jars inside them) use that was removed, is marked for
  removal, or behaves differently on Java 25 than on Java 21, checked against both JDKs' own class files.
- `python tools/mcrun.py --version 1.17.1|1.18.2|1.19.4|1.20.1 --java <home> --dove <jar> --fabric-api <jar> --game-dir <dir> [--agent build/libs/dove-agent.jar[=options]] [--pack <folder>] [--mode bench] [--rejoin] [--jfr <file>]`
  starts the game the way the Dove launcher does (its .minecraft, libraries and Java 25 flags) in a
  separate game folder, and prints the time to the title screen, into the world, and the bench's
  frame rates as one JSON line. `--rejoin` joins the newest world again, plays, and closes the window
  (before 1.20, which has no Quick Play, through the test-only helper mod in `tools/joinworld`).
  For a version the launcher hasn't installed, set `DOVE_MC_DIR` to a folder laid out like `.minecraft`.

## Project layout

```
src/main/java/dev/dove/agent/
├── DoveAgent.java        premain entry point; registers patches
├── AgentOptions.java     log= / cache= / debug= / fastStartup= options
├── transform/            ClassPatch interface, registry (by name or constant-pool marker), transformer
├── patches/              bytecode patches
├── hooks/                runtime code that patched Fabric Loader code (or premain) calls into
├── boot/                 puts the hooks Minecraft's own classes call on the bootstrap class path
└── util/                 console + file logger, constant-pool helpers
src/boot/java/            hooks Minecraft's classes call; shipped under dove-boot/ (see boot/BootClasses)
src/scan/java/            the jar scanner (not part of the agent jar)
tools/mcrun.py            the launch and benchmark harness
```

**Gotcha:** the shaded jar relocates every string literal that looks like `org/objectweb/asm/...`.
To name the game's own ASM classes, build the string at runtime.

**Hooks called from Minecraft's classes go in `src/boot`:** Fabric's class loader hides the
application class path from Minecraft and mods, so those hooks are put on the bootstrap class path
at startup and may only use the JDK.

**These versions are obfuscated:** in a real launch Minecraft's classes are named `net/minecraft/class_1234`
(Fabric's intermediary names), not the Mojang names seen in the dev environment. A patch on a
Minecraft class must target the intermediary name, or better, select classes by what they call
(`markers()`), as `LazyDataFixerPatch` does.
