# MythicAC
### Context-Aware Movement & Rotation Anti-Cheat (PoC)

Lightweight **proof-of-concept Minecraft anti-cheat** focused on detecting **movement and rotation anomalies** while correctly handling **server-induced mechanics** (e.g. MythicMobs skills).

Designed to demonstrate **false-positive reduction via context awareness**, not a full production rule set.

---

## Problem
Many anti-cheats assume player input is the only source of movement and rotation.

In practice, servers apply:
- Forced rotations
- Scripted velocity (pulls, knockback, dashes)
- Teleports and target-locking mechanics

These produce cheat-like patterns and lead to:
- False positives
- Over-exempted checks
- Reduced detection accuracy

---

## Approach
MythicAC focuses on **attribution**, not blanket exemptions.

- Track per-player movement & rotation deltas
- Detect abnormal patterns
- Apply **short, scoped exemptions** when server mechanics are active
- Preserve detection outside exemption windows

Detection and exemptions are **decoupled** to avoid blind spots.

---

## Structure
- check/ Base check + movement & rotation checks
- data/ Per-player state tracking
- hook/ MythicMobs, MMOItems, ProtocolLib context
- listener/ Player movement & rotation capture
- manager/ Check routing, exemptions, player tracking
- util/ Shared helpers

---

## Checks
- **MovementCheck**
  - Velocity & positional anomalies
  - Invalid acceleration patterns
- **RotationCheck**
  - Unnatural yaw/pitch deltas
  - Non-human rotation behavior

---

## Exemptions
Exemptions are applied only when required.

Examples:
- Server-forced rotation → exempted
- Scripted knockback → tracked, not flagged
- Anomalies outside context → detected normally

This prevents cheats from hiding behind server mechanics.

---

## Hooks
- **MythicMobs** – scripted mechanics context
- **MMOItems** – item-based abilities
- **ProtocolLib** – packet-level movement & rotation data

---

## Running
1. Paper / Spigot server  
2. Install:
   - MythicMobs
   - ProtocolLib (recommended)
   - MythicAC
3. Trigger MythicMobs skills affecting movement or rotation
4. Observe detection & exemption behavior via logs

---

## Purpose
This project demonstrates:
- Context-aware anti-cheat design
- False-positive reduction without weakening detection
- Clean, extensible architecture suitable for large servers

---

## Disclaimer
Research and educational proof-of-concept only.
