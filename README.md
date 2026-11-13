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

## Neu in 2.8.0
- **Befehle ohne /mw** – `/aufgaben`, `/lobbys`, `/bereit`, `/shop`, `/stats`, `/top`, `/revanche`, `/leave` … (Liste in `shortcuts.commands`, `/mw …` geht weiterhin).
- **MySQL oder lokal** – `storage.type: local` (stats.yml) oder `mysql` (MySQL/MariaDB). Beim Wechsel wird die stats.yml automatisch übernommen; ist die DB weg, läuft das Plugin lokal weiter.
- **Netzwerk-tauglich** – mit MySQL teilen sich mehrere Server Coins, Stats & Aufgaben (Daten werden beim Join frisch geladen). BungeeCord/Velocity bleibt optional.

## Neu in 2.7.0
- **Verlängerung** – ist es am Zeitlimit knapp (≤ 10 % Portal-Unterschied), gibt es bis zu 3 Minuten Verlängerung: der nächste Portaltreffer entscheidet.
- **Comeback-Bonus** – fällt das eigene Portal das erste Mal unter 50 %, bekommt das Team einmalig Schild + Abfangjäger.
- **Tagesaufgaben** – jeden Tag 3 Aufgaben (Kills, Raketen, Portale …) mit Coins, Bonus für alle 3. `/mw aufgaben` oder im Lobby-Menü.
- **Siegesserie** – Bonus-Coins für Siege in Folge, Ansage ab 3 Siegen.
- **Revanche** – nach der Runde per Klick automatisch für dieselbe Map in der nächsten Lobby stimmen.
- **Performance** – Arena-Welten ohne Mob-Spawns, Random-Ticks, Feuer-Ausbreitung & Tageszyklus, eigene Sicht-/Simulationsweite, Aufräumen alter Projektile, schnellere Physik-Prüfung am Portal, Stats werden gebündelt & asynchron gespeichert.
- **Bugfixes** – Feuer breitete sich in der Arena aus (Dschungel-Map!), verirrte Feuerbälle luden endlos Chunks, Tagesbelohnungs-Serie ging nach Neustart verloren, leere Lobbys behielten alte Abstimmungen.

## 2.6.1
- **Fix Bereit:** Beim Map-Wechsel durch die Abstimmung ging der Bereit-Status verloren (man musste nochmal drücken) – wird jetzt übernommen.
- **Neue Lobby automatisch:** Startet eine Runde, öffnet sich sofort die nächste Lobby (bis `queues.max-games`). Leere Lobbys bleiben offen.
- **Fix Teampunkte bei 24/32 Schichten:** alles Glas wird erkannt, Punkte pro Block an die Glasdicke angepasst (32 Schichten ≈ 0,5 Punkte pro Block).
- **Inventar-Sperre:** Lobby-Items lassen sich nicht mehr verschieben; im Spiel bleiben Bogen, Runden-Shop und Rüstung fest, der Rest ist frei.

## Neu in 2.6.0
- **Mehrere Lobbys** – standardmäßig 2 Runden gleichzeitig (`queues.max-games`). Spieler kommen automatisch in die offene Lobby; startet sie, öffnet die nächste. Wechseln per Menü oder `/mw lobbys`.
- **Neue Start-Logik** – ab 2 Spielern startet die Runde, sobald alle **Bereit** drücken (kein Timer). Ab 4 Spielern läuft ein 60-s-Timer, alle bereit = sofort los.
- **Synchroner Timer** – Titel, Bossbar, XP-Leiste und Scoreboard zeigen dieselbe Sekunde.
- **AFK nur als Hinweis** – nach 5 Minuten ohne Bewegung bekommt nur der Spieler eine Nachricht (`afk-action: spectate` für das alte Verhalten).
- **Join-/Leave-Nachrichten** mit LuckPerms-Rang, Erst-Join-Begrüßung, Willkommens-Titel und MOTD (`join-messages`).
- **Performance & Stabilität** – Scoreboard/Tablist senden nur Änderungen, Fehler in einer Runde werden geloggt statt sie hängen zu lassen.

## Neu in 2.5.0
- **Raketen explodieren zuverlässig** – TNT fällt nicht mehr in den Void, Raketen am Reichweiten-Ende detonieren.
- **Keine Raketen mehr durch Wände** – Schildbrecher/Bohrer durchdringen nur noch von Spielern gesetzte Blöcke, nicht die Map.
- **Start nur per Bereit** – der 60-s-Timer ist nur noch ein AFK-Check.
- **Glasdicke abstimmen** – 15/24/32 Schichten in der Wartelobby (`glass-vote` in der config).
- **4 neue Themen-Maps** – Dschungeltempel, Wüstentempel, Atlantis, Ritterburg.
- **Schönere GUIs** – neuer Rahmen mit Farbverlauf, Trennlinien in den Beschreibungen.
- **Fairere, höhere Preise** – Team-Upgrades und Runden-Shop teurer; Glas-/Portal-Reparatur repariert nur einen Teil, ist begrenzt und wird jedes Mal teurer.

## 2.4.1
- Fix: „Bereit" musste man zweimal drücken (doppelte Klick-Events vom Client)
- 15 Blöcke Schutzglas vor dem Portal für längere Runden

## Neu in 2.4.0

**5 Arena-Themes** (Map-Abstimmung): **Klassisch**, **Piratenschiffe** (Galeonen mit Masten, Segeln, Kajüte und Meer), **Raumschiffe** (eigene Sci-Fi-Schiffe mit Triebwerken, Cockpit, Asteroiden & Mond, Nachthimmel), **Eisfestung** (Türme, Zinnen, Eisschollen), **Netherfestung** (Basaltsäulen, Netherrack-Inseln). Werden beim ersten Start einmal angelegt (`piraten`, `weltraum`, `eisfestung`, `nether`); eigene: `/mw map generate <name> [abstand] [thema]`.

**Mehr Schutz** – vor jedem Portal liegt ein 15 Blöcke dicker Glasblock (ab 2.4.1), Spawn vorne auf dem Dach.

**Schleichen beim Platzieren** – Rakete wird 5 Blöcke tiefer gesetzt (`missile.sneak-offset`), Vorschau zeigt es.

**Bereit-Pflicht** – bis 2 Spieler startet die Runde nur, wenn alle „Bereit" drücken; wer nach 60 s nicht bereit ist, fliegt aus der Runde (`ready:`).

**Aufgeräumte Lobby-Hotbar** – Bereit · Team · Menü · (VIP-Start) · Verlassen. Das Menü bündelt Map-Abstimmung, Kit, Shop, Raketen, Erfolge, Statistiken und Tagesbelohnung.

**Neues Scoreboard** – größer, mit Map-Thema, symmetrischen Portal-Balken für beide Teams, Teampunkten, Event-Anzeige und Servername.

**Team-Upgrades** – Teampunkte für zerstörtes Glas der Gegner-Basis (+1), Kills (+3), abgefangene Raketen (+5), Airdrops (+5). Im Runden-Shop (Taste F) bzw. `/mw upgrades`: Nachschub, Raketen-Turbo, Verstärkte Schilde, Panzerung, Glas-Reparatur, Portal-Reparatur.

**Leistungsbewertung** – nach jeder Runde S/A/B/C/D für jeden Spieler aus Kills, Assists, Raketen, Abfangen, Portal- und Glasschaden; MVP nach Punkten.

**End-Animation** – alle schauen auf das gefallene Portal: Glas zerspringt, das Portal baut sich wieder auf und wird im Zeitraffer in der echten Reihenfolge zerstört, dann bricht der Rest zusammen.

## Neu in 2.3.0

**Neue Arena im klassischen Look** – zwei große Glas-Boxen in Teamfarbe mit Obsidian-Kanten, darin eine Obsidianwand mit dem Portal; Spawn auf dem Dach; Tageshimmel; großes „MISSILE WARS"-Schild an Ketten seitlich der Flugbahn. Generierte Maps bekommen das Design automatisch.

**Portal-HP funktioniert jetzt wirklich** – Vanilla bricht ein ganzes Netherportal ab, sobald ein Block fehlt. Das wird jetzt verhindert, Treffer ziehen nur die getroffenen Blöcke ab.

**Shop in der Runde** – Smaragd in der Hotbar oder Taste **F**: Raketen & Items gegen Coins (Preise & Kauflimit in `shop:`).

**Mod-/Admin-Modus** (`/mw mod`, Recht `missilewars.mod`) – unsichtbar, fliegen, unverwundbar; Werkzeuge: Spieler-Liste (Teleport, Einfrieren, Team setzen, Zuschauer, aus der Runde werfen), Runde starten, Runde stoppen (Admin), Unsichtbar an/aus, zur Arena.

**Start-Block für VIP/Premium** (Recht `missilewars.start`) – Smaragdblock in der Wartelobby (oder `/mw start`): startet die Runde mit 10 s Countdown, ab 2 Spielern, einmal pro Runde. Recht `missilewars.vip`: 1,5× Coins & doppelte Tagesbelohnung.

**10 neue Features**
1. **Airdrops** – alle 90 s fällt eine leuchtende Kiste bei einem Team; wer sie öffnet, bekommt seltene Items + Coins
2. **Zufalls-Events** – Raketenregen, Doppelte Coins, Turbo-Raketen, Mondschwerkraft, Item-Flut
3. **Kopfgeld** – ab 3er-Serie leuchtet der Spieler; wer ihn erledigt, kassiert
4. **Assists** – Schaden in den letzten 10 s zählt, mit Coins
5. **Rache** – erledige deinen letzten Killer für Bonus-Coins
6. **Raketen abfangen** – gegnerische Rakete per Explosion oder Abbau stoppen → Ansage + Coins
7. **Level** – XP aus allen Statistiken, Level-Abzeichen in Chat & Tab, Level-Up-Belohnung
8. **Erfolge** – 13 Erfolge mit Fortschrittsbalken (`/mw erfolge`)
9. **Tagesbelohnung** – `/mw daily`, steigt 7 Tage lang
10. **Map-Bewertung** – nach der Runde 👍/👎, Ergebnis in der Map-Abstimmung

## Neu in 2.2.0

**Coins & Shop** (`/mw shop`, Smaragd in der Wartelobby)
- Coins für Kills (10), Sieg (50), Teilnahme (15), Portal zerstört (40), Raketen (1), First Blood (5), Killstreaks (15) – alles in `coins:` einstellbar
- **Kits** dauerhaft freischalten (Angreifer/Verteidiger 400, Techniker/Sprinter 600; Standard gratis)
- **Raketen-Spuren**: Seelenfeuer, Funken, Sternenstaub, Herzen, Kirschblüten, Musik, Totem
- **Kill-Effekte**: Feuerwerk, Blitz, Herzregen, Seelen
- **Rundenboosts** (vor dem Start): Start-Tomahawk, -Schild, -Plattform, Pfeil-Bündel, Start-Juggernaut
- `/mw coins`, `/mw top coins`, Admin: `/mw givecoins <spieler> <anzahl>`

**Portal-Fortschritt im Scoreboard** – Balken + Prozent für beide Teams. Standard ist jetzt „verloren bei 25 % zerstört" (`portal.loss-percent`, 0 = klassisch ein Block).

**Fix:** „Bereit" hat den Countdown in einer Endlosschleife auf 5 s zurückgesetzt.

## Neu in 2.1.0

**Nur noch eine Runde** – Spieler kommen beim Betreten des Servers automatisch rein, `/mw join` ist nicht mehr nötig. Die Map wird in der Wartelobby per Abstimmung gewählt (Startwert: zufällige Map). Nach der Runde geht's automatisch in die nächste. (`single-game` in der Config)

**Items alle 20 Sekunden** (alte Configs werden automatisch umgestellt).

**5 neue Raketen** – jetzt 12:

| Rakete | Größe | TNT | Besonderheit |
|---|---|---|---|
| Hydra | 5×2×5 | 6 | drei Sprengköpfe nebeneinander |
| Bohrer | 1×2×7 | 3 | bohrt sich durch Blöcke (nicht Obsidian) |
| Pfeil | 1×1×5 | 2 | schnellste Rakete |
| Transporter | 5×2×6 | 0 | große Plattform nur zum Rüberfahren |
| Streubombe | 1×2×6 | 5 | verteilt 6 zusätzliche TNT |

**Raketen-Übersicht** – Buch in der Wartelobby (oder `/mw raketen`): alle Raketen mit Tempo, Ladung, Größe, Mitfahren, Besonderheit und Drop-Chance (passend zu deinem Kit) plus alle Hilfsmittel.

**10 neue Features**
1. **Kits** – Standard, Angreifer, Verteidiger, Techniker, Sprinter (ändern Item-Chancen, Boni)
2. **Rettungsplattform** – Lohenrute: Schleim-Plattform unter dir für 8 s
3. **Enderperle** im Item-Pool
4. **Respawn-Countdown** – 3 s Zuschauer nach dem Tod
5. **Zeitlimit** (15 min) – Entscheidung nach Portal-Zustand, dann Kills
6. **AFK-Erkennung** – nach 90 s Inaktivität zum Zuschauer
7. **Bereit-System** – sind alle bereit, startet es in 5 s
8. **Doppelsprung** in der Wartelobby
9. **Kill-Effekte** – Partikel in Teamfarbe + Sound
10. **Teamkompass** – zeigt auf das gegnerische Portal

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
| `/mw raketen` | Raketen-Übersicht |
| `/mw kit` / `/mw bereit` | Kit wählen / bereit melden |
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
