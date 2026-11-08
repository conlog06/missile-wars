# MissileWars

Missile Wars für **Paper 26.3** (Java 21+). Zwei Teams, Raketen, Schilde, Feuerbälle – wer zuerst das gegnerische Portal zerstört, gewinnt.

## Installation

1. JAR aus den [Releases](../../releases) in `plugins/` legen.
2. Server starten – die Standard-Arena `standard` wird automatisch angelegt.
3. Ingame `/mw join`.

Alte Clients (1.8 / 1.12) kommen per **ViaVersion + ViaBackwards** auf den Server. Für sie: `menu-style: inventory` und ggf. `combat.old-pvp: true`; Display-Effekte (Vorschau, Hologramme) sehen sie nicht, das Spiel funktioniert trotzdem.

## Spielablauf

- Ab `players.min` Spielern startet der Countdown, dann gibt es Bogen (Flame), Lederrüstung und alle 15 s ein zufälliges Item.
- **Raketen** (Rechtsklick auf einen Block): Tomahawk, Juggernaut, Shieldbuster, Lightning, Guardian, Gemini, Abfangjäger (siehe Tabelle oben).
- **Schild**: Schneeball werfen → klappt zu einer Glaswand auf.
- **Feuerball**: abfeuern, kann zurückgeschlagen werden.
- Raketen entschärfen: Antrieb (Schleim, Kolben, Observer) abbauen → Rakete bleibt stehen.
- Wird **ein** Portalblock zerstört, verliert das Team.
- Kein Todesbildschirm: Tod/Void → direkt zurück zum Team-Spawn, Inventar bleibt.

Die Raketen werden vom Plugin bewegt (wie Flugmaschinen, aber ohne echtes Redstone) – das läuft stabil, ohne Lag und unabhängig von Redstone-Änderungen in neuen Versionen.

## Neu in 2.0.0

**Wartelobby** – eine generierte, schwebende Lobby-Insel (Denkmal-Rakete, Teamsäulen, Leuchtboden, Hologramm). Spieler warten dort bis zum Start, danach geht's in die Arena. Eigene Lobby: `/mw setwaitlobby`.

**Arena ausgebaut** – Obsidian-**Schutzbunker** vor jedem Portal (Spawn + Schutz für den unteren Portalteil), Glas-Seitenwände, Crying-Obsidian-Akzente, Leuchtsäulen, Stützen unter den Plattformen, Deko-Insel unter der Mitte, zweifarbiger Boden.
> Bestehende generierte Maps bekommen das neue Design automatisch bei der nächsten Runde.

**Mitfahren** – nur auf **Guardian, Juggernaut und Gemini** (steht im Tooltip). Andere Raketen schieben nur, wer im Weg steht.

**Spielmodus / Auto-Join** – `server-mode.enabled: true`: Spieler landen beim Joinen direkt in einer Runde, nach der Runde geht's automatisch in die nächste (oder zurück auf den Lobby-Server). Join/Quit-Nachrichten aus, MOTD zeigt den Rundenstatus.

**BungeeCord / Velocity** – `bungee.enabled` + `bungee.lobby-server`: „Verlassen" und Rundenende schicken Spieler auf den Lobby-Server.

**NameTags & Tab** – Rang-Präfixe aus **LuckPerms** (optional, `&`-Codes, Hex und MiniMessage) über dem Kopf, in der Tab-Liste und im Spielchat; Tab nach Team & Gruppengewicht sortiert; Tab-Header/Footer mit Status.

**Neues Design** – eigene Farbpalette mit Verläufen (Orange/Pink/Gold), Spielmenü (Kompass) mit Map-Status, Statistik-Kopf & Bestenliste, neues Team-Menü mit Rahmen, Zuschauer-Teleporter (`/mw teleporter`).

**Killstreaks & First Blood** – Ansagen bei 3/5/7/10 Kills in Folge mit Bonus-Items (ab 5er-Serie extra Rakete).

### Setup als Spielmodus-Server (BungeeCord)
```yaml
server-mode:
  enabled: true
  after-game: requeue      # oder lobby-server
bungee:
  enabled: true
  lobby-server: lobby
tablist:
  server-name: play.deinserver.de
```
In `spigot.yml` `settings.bungeecord: true` (bzw. Velocity-Forwarding in `paper-global.yml`) setzen.

## Neu in 1.3.0

**Raketen komplett überarbeitet**
- Neue, größere Designs mit **Flossen in Teamfarbe** (Terrakotta) und Redstone-Kern
- Zwei neue Raketen: **Gemini** (Zwillingsrakete, trifft breit) und **Abfangjäger** (sehr schnell, zündet sofort – holt gegnerische Raketen aus der Luft)
- **Einschlag-Effekte**: Funken, Lava-Partikel, Zisch- & Aufprallsound; der Schütze bekommt „💥 eingeschlagen" bzw. „gestoppt" angezeigt
- **Limit aktiver Raketen** pro Team (`missile.max-active-per-team`) gegen Lag
- Alle Raketenteile sind automatisch abbaubar (entschärfen), auch mit alter Config

| Rakete | Größe | TNT | Besonderheit |
|---|---|---|---|
| Tomahawk | 1×2×7 | 5 | Allrounder |
| Juggernaut | 3×3×8 | 17 | langsam, riesige Ladung |
| Shieldbuster | 1×3×9 | 10 | bricht durch Glas |
| Lightning | 1×2×6 | 2 | sehr schnell |
| Guardian | 3×4×6 | 4 | Plattform zum Mitfahren |
| Gemini | 3×2×6 | 6 | zwei Raketen nebeneinander |
| Abfangjäger | 1×2×4 | 1 | zündet sofort |

**Map-Abstimmung** – in der Wartephase Karte im Inventar (Dialog bzw. klickbare Chat-Liste) oder `/mw vote <map>`. Bei 10 s Countdown wird ausgewertet; gewinnt eine andere Map, zieht die ganze Lobby automatisch um.

**Modernes Command-System** – `/mw` wird über Paper/Brigadier registriert (Vorschläge direkt im Client), nicht mehr über plugin.yml.

## Neu in 1.2.0 – volle Nutzung der neuen Version

1. **Raketen-Vorschau** – beim Halten einer Rakete leuchtet ein Geister-Umriss (Block-Displays, nur für dich sichtbar): grün = passt, rot = geht nicht
2. **Portal-Hologramme** – schwebende Anzeige über jedem Portal (Text-Displays)
3. **Portal-HP-Modus** – `portal.loss-percent: 30` → Portal verliert erst bei 30 % Zerstörung; HP-Balken im Hologramm & in der Sidebar, Treffer-Warnung fürs Team
4. **Bestenlisten-Hologramme** – `/mw holo add <stat>` stellt eine Top-10 an deine Position (aktualisiert alle 30 s)
5. **Windkugel** (1.21) – neues Item im Pool: Sprünge über die Lücke, Gegner ins Void schleudern
6. **Streitkolben** (1.21) – optional (`items.mace.weight`)
7. **Item-Cooldowns** – mit nativer Cooldown-Anzeige im Inventar (Anti-Spam)
8. **1.8-PvP umschaltbar** – `combat.old-pvp: true` entfernt den Angriffs-Cooldown
9. **Team-Auswahl als Dialog** (ab 1.21.6) – natives Popup mit Mitglieder-Tooltip; Schleichen + Klick = Inventar-Menü, `menu-style: inventory` für alte Clients
10. **Raketen-Infos im Item** – Tempo (Blöcke/s) und Sprengladung im Tooltip

## Neu in 1.1.0

1. **Statistiken** – Siege, Niederlagen, Kills, Tode, K/D, Raketen, zerstörte Portale (`/mw stats [spieler]`, gespeichert in `stats.yml`)
2. **Bestenliste** – `/mw top <wins|kills|raketen|portale|runden|tode>`
3. **Rundenauswertung** – MVP, Top 3 und eigene Werte am Rundenende; wer das Portal zerstört hat, wird genannt
4. **Lobby-Items** – Wolle öffnet das Team-Menü (Grün/Zufällig/Rot mit Mitgliederliste), Bett = Verlassen
5. **Join-Schilder** – Schild mit `[MissileWars]` in Zeile 1 und Mapname in Zeile 2; zeigt Live-Status & Spielerzahl. Entfernen: als Admin schleichen + abbauen
6. **Teamchat** – im Spiel schreibt man nur ins eigene Team, `@Nachricht` geht an alle
7. **Raketen-Warnung** – Verteidiger bekommen Titel + Sound, wenn eine Rakete aufs eigene Portal zufliegt (inkl. links/rechts/mittig)
8. **Sudden Death** – nach 8 Minuten kommen Items doppelt so schnell; BossBar zeigt den Item-Timer
9. **Raketen-Effekte** – Flammen-/Rauchspur und Antriebsgeräusch
10. **Auto-Balance** – Teams werden beim Start ausgeglichen

## Befehle

| Befehl | |
|---|---|
| `/mw join [map]` | Runde beitreten (auch während der Runde, siehe `allow-late-join`) |
| `/mw spectate [map]` | Zuschauen |
| `/mw team <grün\|rot>` | Team wechseln (vor dem Start) |
| `/mw vote [map]` | Map-Abstimmung |
| `/mw leave` | Verlassen – Inventar & Position werden wiederhergestellt |
| `/mw list` | Maps & laufende Runden |
| `/mw menu` | Spielmenü |
| `/mw teleporter` | (Zuschauer) Spieler-Teleporter |
| `/mw stats [spieler]` | Statistiken |
| `/mw top [stat]` | Bestenliste |
| `/mw start [map]` | *(Admin)* sofort starten |
| `/mw stop <map>` | *(Admin)* Runde beenden |
| `/mw setlobby` | *(Admin)* Ziel nach der Runde |
| `/mw setwaitlobby` | *(Admin)* eigene Wartelobby statt der generierten |
| `/mw reload` | *(Admin)* Config & Maps neu laden |
| `/mw holo add <stat>` / `remove` / `list` | *(Admin)* Bestenlisten-Hologramme |

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

Bei eigenen Maps dürfen sich die Teams auf der **X- oder Z-Achse** gegenüberstehen – die Richtung wird aus den Portalen erkannt (`/mw map info` zeigt sie). Template-Welten werden im Paper-26-Format (`world/dimensions/minecraft/<name>`) gefunden und für jede Runde kopiert.

## Config

Alles in `plugins/MissileWars/config.yml`: Spielerzahlen, Countdown, Item-Intervall, Item-Gewichtung & -Limits, Raketen-Tempo, Zündzeit, Schildgröße, Feuerball-Stärke, abbaubare Blöcke.

## Build

```
mvn package
```
GitHub Actions baut bei jedem Push auf `main` und veröffentlicht ein Release.
