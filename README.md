MythicAC : Context-Aware Movement & Rotation Anti-Cheat (PoC)

MythicAC is a proof-of-concept Minecraft anti-cheat plugin focused on movement and rotation anomaly detection while correctly handling server-induced mechanics (e.g. MythicMobs skills).
The project demonstrates how to detect cheat-like behavior without false positives caused by scripted server actions.

This PoC prioritizes clean architecture, attribution, and correctness over a large rule set.

Problem Statement

Traditional movement and rotation checks assume that player input is the sole source of motion and camera changes.
In practice, advanced servers frequently apply:

Forced rotations

Scripted velocity (pulls, knockback, dashes)

Teleports and target-locking mechanics

These actions produce patterns that resemble cheating, leading to:

False positives

Over-exempted checks

Poor player experience

MythicAC addresses this by introducing context-aware detection rather than loosening checks globally.

Core Approach

MythicAC is built around attribution:

Track per-player movement and rotation state

Detect abnormal deltas and patterns

Apply exemptions when server mechanics are responsible

Log and score anomalies instead of immediately enforcing

This mirrors real-world anti-cheat design at scale: telemetry and classification first, enforcement second.

Architecture Overview
Plugin Entry

MythicAntiCheat.java
Initializes managers, listeners, and external hooks.

Checks

check/Check.java
Base abstraction for all checks.

check/MovementCheck.java
Detects invalid movement patterns, velocity changes, and positional anomalies.

check/RotationCheck.java
Detects unrealistic yaw/pitch deltas and rotation behavior inconsistent with normal player input.

Player State

data/PlayerData.java
Maintains per-player state including:

last known movement & rotation

recent deltas

tick timing

exemption flags

Managers

manager/CheckManager.java
Registers and executes checks for each player update.

manager/PlayerTracker.java
Handles player lifecycle and state updates.

manager/ExemptionManager.java
Determines when checks should be ignored or reduced due to server-induced effects.

Event Handling

listener/PlayerListener.java
Captures player movement and rotation events and feeds them into the detection pipeline.

Context Hooks

hook/ProtocolLibHook.java
Provides accurate packet-level movement and rotation data.

hook/MythicMobsHook.java
Creates exemption windows for MythicMobs-driven mechanics.

hook/MMOItemsHook.java
Handles item-based abilities that may cause abnormal motion or rotation.

Utilities

util/*
Shared helpers for math, timing, thresholds, and common logic.

Exemption & Detection Logic

Instead of globally disabling checks, MythicAC applies short, controlled exemption windows when server mechanics are active.

During these windows:

Checks may be skipped

Thresholds may be relaxed

Confidence scores may be reduced

Outside of these windows, full detection logic remains active.

This prevents cheats from hiding behind server mechanics while eliminating false positives.

What This PoC Demonstrates

Context-aware movement and rotation detection

False-positive reduction without creating blind spots

Clean separation between:

tracking

detection

exemptions

Extensible structure suitable for large-scale servers

Practical anti-cheat engineering rather than heuristic spam

Typical Scenarios

Forced MythicMobs rotation → detected but exempted

Scripted knockback → tracked without flagging

Unnatural rotation outside exemptions → flagged

Repeated anomalies over time → escalated confidence

Running the PoC

Run a Paper/Spigot server

Install:

MythicMobs

ProtocolLib (recommended)

MythicAC

Trigger MythicMobs skills that affect movement or rotation

Observe detection and exemption behavior via logs

Relevance

This project reflects real challenges faced by large Minecraft networks:

custom gameplay systems

complex player movement

maintaining detection accuracy at scale

It demonstrates an engineering approach aligned with modern anti-cheat design: context-aware, explainable, and extensible.

Disclaimer

This project is a research and educational proof-of-concept.
It is not intended to bypass anti-cheats or facilitate cheating.
