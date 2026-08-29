# NetRestore

Paper plugin that detects **server-wide network lag** and offers a 5-minute `/restore` after eligible deaths.

Built for [A Zombie Pigman Broke My Door](https://github.com/wilderop) (semi-anarchy). It does **not** keep inventory on personal ping spikes or PvP deaths.

## How an incident is detected

An incident starts only when **two independent signals** fire:

1. Outbound TCP probe RTT to `1.1.1.1:443` / `8.8.8.8:443` jumps (3× baseline or ≥250 ms, or two timeouts)
2. Crowd ping: several online players spike together
3. Disconnect burst in a short window
4. Optional MSPT spike (server freeze)

Probe-only never grants a restore. Personal high ping never grants a restore.

After the network recovers, a short grace window stays hot so lava/mob deaths that apply when ticks catch up still qualify.

## Player flow

1. Eligible death during a hot incident
2. Death drops are cleared (anti-dupe)
3. On respawn, a clickable `/restore` is offered for **5 minutes**
4. `/restore confirm` applies the snapshot once

## Abuse defaults

- No PvP killer, no combat tag (20s)
- Nearby player within 8 blocks = contested, no offer
- Must have been online 45s this session
- One offer per player per incident
- 6 hour restore cooldown
- Denied if current inventory already overlaps the snapshot ≥70%
- `/kill` and `SUICIDE` never qualify
- Staff: `/netrestore preview|deny|force <player>`
- Every offer/deny/restore is written to `plugins/NetRestore/audit.log`

## Commands

| Command | Permission | Description |
|---|---|---|
| `/restore` | `netrestore.use` | Show offer |
| `/restore confirm` | `netrestore.use` | Apply snapshot |
| `/netrestore status` | `netrestore.admin` | Incident + probe state |
| `/netrestore reload` | `netrestore.admin` | Reload config |
| `/netrestore preview <player>` | `netrestore.admin` | Inspect offer |
| `/netrestore deny <player>` | `netrestore.admin` | Revoke offer |
| `/netrestore force <player>` | `netrestore.admin` | Staff restore |

## Build

Requires **JDK 25** and Maven.

```bash
mvn -B package
```

Jar: `target/NetRestore.jar`

Drop it in the Paper `plugins/` folder. Paper 26.1.2+.
