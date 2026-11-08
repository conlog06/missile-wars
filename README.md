# MissileWars

Missile Wars für **Paper 26.3** (Java 21+). Zwei Teams, Raketen, Schilde, Feuerbälle – wer zuerst das gegnerische Portal zerstört, gewinnt.

## Installation

1. JAR aus den [Releases](../../releases) in `plugins/` legen.
2. Server starten – die Standard-Arena `standard` wird automatisch angelegt.
3. Ingame `/mw join`.

Alte Clients (1.8 / 1.12) kommen per **ViaVersion + ViaBackwards** auf den Server.

## Spielablauf

- Ab `players.min` Spielern startet der Countdown, dann gibt es Bogen (Flame), Lederrüstung und alle 15 s ein zufälliges Item.
- **Raketen** (Rechtsklick auf einen Block): Tomahawk, Juggernaut, Shieldbuster (bricht durch Glas), Lightning (schnell), Guardian (mit Plattform zum Mitfahren).
- **Schild**: Schneeball werfen → klappt zu einer Glaswand auf.
- **Feuerball**: abfeuern, kann zurückgeschlagen werden.
- Raketen entschärfen: Antrieb (Schleim, Kolben, Observer) abbauen → Rakete bleibt stehen.
- Wird **ein** Portalblock zerstört, verliert das Team.
- Kein Todesbildschirm: Tod/Void → direkt zurück zum Team-Spawn, Inventar bleibt.

Die Raketen werden vom Plugin bewegt (wie Flugmaschinen, aber ohne echtes Redstone) – das läuft stabil, ohne Lag und unabhängig von Redstone-Änderungen in neuen Versionen.

## Befehle

| Befehl | |
|---|---|
| `/mw join [map]` | Runde beitreten (auch während der Runde, siehe `allow-late-join`) |
| `/mw spectate [map]` | Zuschauen |
| `/mw team <grün\|rot>` | Team wechseln (vor dem Start) |
| `/mw leave` | Verlassen – Inventar & Position werden wiederhergestellt |
| `/mw list` | Maps & laufende Runden |
| `/mw start [map]` | *(Admin)* sofort starten |
| `/mw stop <map>` | *(Admin)* Runde beenden |
| `/mw setlobby` | *(Admin)* Ziel nach der Runde |
| `/mw reload` | *(Admin)* Config & Maps neu laden |

Rechte: `missilewars.play` (alle), `missilewars.admin` (OP).

## Maps

Es gibt zwei Arten:

**Generiert** – keine Welt nötig, die Arena wird jede Runde frisch gebaut:
```
/mw map generate lang 90      # Abstand Mitte → Portal = 90
```

**Eigene Welt** – wird für jede Runde kopiert, das Original bleibt unberührt:
```
/mw map create meinemap arena   # Void-Welt mit vorgebauter Arena, Spawns & Portale schon gesetzt
/mw map create meinemap leer    # leere Void-Welt
/mw map import meinemap welt1   # vorhandenen Weltordner nutzen (z. B. eine heruntergeladene Map)
```
Dann bauen und einrichten:
```
/mw map setspawn meinemap gruen   # Position = Spawn
/mw map setspawn meinemap rot
/mw map setportal meinemap gruen  # auf einen Portalblock schauen
/mw map setportal meinemap rot
/mw map setdeathy meinemap 30     # unter dieser Höhe = Void-Tod
/mw map finish meinemap           # Welt wird entladen, Map ist spielbar
```
`/mw map tp <name>` holt dich zum Weiterbauen zurück (Map ist dann bis zum nächsten `finish` gesperrt).

Wichtig bei eigenen Maps: Die Teams müssen sich **auf der Z-Achse** gegenüberstehen, die Portale quer dazu (Achse X). Netherportale sind in Vanilla max. 21 × 21 groß.

## Config

Alles in `plugins/MissileWars/config.yml`: Spielerzahlen, Countdown, Item-Intervall, Item-Gewichtung & -Limits, Raketen-Tempo, Zündzeit, Schildgröße, Feuerball-Stärke, abbaubare Blöcke.

## Build

```
mvn package
```
GitHub Actions baut bei jedem Push auf `main` und veröffentlicht ein Release.
