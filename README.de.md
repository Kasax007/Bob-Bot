# Bob

*[English](README.md)*

Bob ist ein Begleiter für Minecraft (Fabric, 26.3). Er spielt wie ein Spieler: Er läuft, gräbt,
baut, kämpft und isst genau wie du. Du kannst im Chat mit ihm reden, dich durch sein Fenster klicken
oder `/bob`-Befehle nutzen. Dann:

- fällt er Holz und pflanzt die Setzlinge nach;
- baut er Erz im Streifenabbau ab und hebt Bereiche aus;
- erntet er Felder, schmilzt, jagt und angelt;
- sortiert er deine Kisten so, wie du sie schon sortiert hast;
- holt er Sachen aus dem Lager;
- baut er ein Litematica- oder WorldEdit-Schematic aus dem Material, das du in eine Kiste legst.

**Bob baut nie ab, was Spieler gebaut haben.**

Bob passt auch auf sich selbst auf. Er rechnet laufend, wie lange seine Herzen gegen das reichen, was ihn gerade angreift. Würde er einen Kampf verlieren, mauert er sich ein, steigt auf eine Säule außer Reichweite oder gräbt sich ein, bevor es zu spät ist, egal was er gerade tut. Danach isst er und wartet, bis er wieder fit ist.

## Loslegen

1. Fabric Loader 0.19.5+ und Fabric API für Minecraft 26.3 (Java 25) installieren, dann die Bob-Jar
   in `mods/` legen. Der Mod muss auf dem Server sein. Auf dem Client bringt er nur die Taste `B` und
   die Übersetzungen mit.
2. Mit `/bob spawn` erscheint dein Bob, mit `/bob spawn Alice` einer mit anderem Namen.
3. Mit **B** (oder `/bob panel`, oder Rechtsklick auf Bob) öffnest du sein Steuerpult.
4. Oder rede einfach mit ihm: **`Bob, fäll 5 Bäume`**.

## Mit ihm reden

Jede Chatzeile, die mit seinem Namen (oder einfach `bob`) beginnt, ist für ihn. Er versteht Deutsch
und Englisch:

| Du sagst | Er macht |
| --- | --- |
| `bob komm` / `bob come here` | kommt zu dir |
| `bob folge mir` / `bob follow me` | folgt dir |
| `bob warte` / `bob stay` | wartet, wo er ist |
| `bob beschütze mich` / `bob guard me` | bleibt in der Nähe und kämpft für dich |
| `bob fäll 5 bäume` / `bob fell 32 logs` | fällt Holz (5 Bäume ≈ 25 Stämme) und pflanzt nach |
| `bob geh erze minen` / `bob mine for iron` | baut im Streifenabbau auf der richtigen Höhe für das Erz ab |
| `bob heb das aus` / `bob dig this out` | hebt den mit dem Zauberstab markierten Bereich aus |
| `bob ernte das feld` / `bob harvest` | erntet reife Pflanzen und sät neu (mit `immer` bleibt er dran) |
| `bob schmelz das eisen` / `bob smelt the iron` | schmilzt es im nächsten Ofen |
| `bob hol mir 16 eisen` / `bob bring me 16 iron` | holt es aus deinen Kisten und besorgt selbst, was fehlt |
| `bob räum auf` / `bob sort the chests` | sortiert Herumliegendes in dein Lager |
| `bob pack aus` / `bob empty your pack` | leert seinen Rucksack in die Ablage-Kiste |
| `bob beleuchte das` / `bob light this up` | stellt Fackeln auf, wo es dunkel ist |
| `bob sammel alles ein` / `bob pick everything up` | sammelt herumliegende Gegenstände ein |
| `bob bau haus` / `bob build house` | zeigt den Bauplan `haus` vor dir |
| `bob bau los` / `bob build it` | beginnt den Bau (`bob dreh es` / `bob turn it` dreht ihn vorher) |
| `bob schütz das haus` / `bob protect this house` | rührt hier nie etwas an |
| `bob was machst du?` / `bob what are you doing?` | sagt dir seine Aufgabe und wie weit er ist |
| `bob stop` · `bob pause` · `bob weiter` · `bob überspring` | stoppen, pausieren, weitermachen, nächste Aufgabe |

Aufträge kommen in eine Warteschlange: Gibst du einen Auftrag, während Bob beschäftigt ist, macht er
erst seine laufende Aufgabe fertig. Damit er einen wartenden Auftrag sofort beginnt, gibt es drei
Wege:

- in seiner Antwort auf **Jetzt** klicken;
- `/bob queue now <n>` eingeben;
- den Auftrag im Steuerpult anklicken.

## Das Steuerpult (B)

Das Steuerpult ist ein Fenster wie eine Kiste, mit einem Symbol pro Aktion:

| Zeile | Inhalt |
| --- | --- |
| Oben | Bobs Zustand |
| Befehle | komm, folge, beschütze, warte, nach Hause (Rechtsklick setzt sein Zuhause), Pause, überspringen, stopp, Status |
| Arbeit | Holz (Linksklick: 64, Rechtsklick: endlos), Bergbau (Linksklick: tief, Rechtsklick: Eisen-Höhe), ausheben, Feld, schmelzen, angeln, Licht, hier bewachen, einsammeln |
| Sachen | sortieren, Rucksack leeren, gib mir alles, sein Rucksack, Zauberstab, Baupläne, hier schützen, Bereiche, hol… |
| Einstellungen | Klick schaltet auf den nächsten Wert |
| Warteschlange | Linksklick: jetzt erledigen; Rechtsklick: entfernen |

Zwei Anzeigen zeigen außerdem, was Bob gerade macht:

- eine Zeile über seinem Kopf;
- ein Balken oben auf deinem Bildschirm mit seinem Fortschritt.

Beides lässt sich in den Einstellungen abschalten.

**Ihm etwas geben:**

| Aktion | Wirkung |
| --- | --- |
| Schleichen + Rechtsklick mit einem Gegenstand | gibt ihn ihm (Rüstung zieht er sofort an, für jede Arbeit nimmt er das beste Werkzeug) |
| Schleichen + Rechtsklick mit leerer Hand | öffnet seinen Rucksack |

## Kisten, Bereiche und der Zauberstab

`/bob wand` gibt dir seinen Zauberstab. Linksklick markiert die eine Ecke, Rechtsklick die andere.
Den markierten Bereich kannst du dann nutzen für:

- Ausheben;
- einen Arbeitsbereich;
- einen geschützten Bereich;
- einen Lagerraum.

```
/bob area add <work|protected|storage> <name>
/bob area list | show <name> | remove <name>
```

Kisten haben Rollen. Mit `/bob chest add <rolle>`, während du auf die Kiste schaust, oder mit
`/bob chest set <rolle> <x y z>`:

| Rolle | Zweck |
| --- | --- |
| `dropoff` | Ablage: Hier leert er seinen Rucksack nach einer Aufgabe oder wenn er voll ist (z. B. beim Streifenabbau). |
| `input` | Material zum Bauen. Er nimmt nur heraus. |
| `storage` | Teil deines sortierten Lagers. Jede Kiste in einem Lager-Bereich zählt auch dazu. |

**Sortieren, wie du sortierst:**

- Ein Gegenstand kommt in die Kiste, die schon dasselbe enthält.
- Sonst kommt er in die Kiste mit den ähnlichsten Sachen (Stämme zu Stämmen, Erze zu Erzen, Essen
  zu Essen).
- Sonst in eine leere Lager-Kiste.

Mit `/bob sort`, `/bob sort pack` oder `/bob sort dropoff` sortiert er, mit
`/bob fetch "<gegenstand>" [anzahl]` holt er dir etwas.

## Bauen (wie Litematica)

1. Die Schematics in einen dieser Ordner legen:
   - `config/bobbot/blueprints/`
   - `schematics/` (Litematica)
   - `config/worldedit/schematics/`

   Bob liest `.litematic`, `.nbt` (Konstruktionsblock) und `.schem` (Sponge v2/v3).
2. Das Material in Kisten mit der Rolle `input` legen. `/bob build materials` zeigt, was der Bau
   braucht und was noch fehlt.
3. Mit `/bob build <name>` zeigt er den Umriss dort, wo der Bau hinkommt (vor dir).
   `/bob build turn` dreht ihn, `/bob build here` verschiebt ihn. `/bob build go` startet den Bau.

Beim Bauen:

- Er baut von unten nach oben und nimmt das Material aus den Material-Kisten.
- Geht etwas aus, sagt er dir, was fehlt, und wartet, bis es da ist.
- Danach räumt er sein eigenes Gerüst weg.

`/bob copy <name>` speichert den mit dem Zauberstab markierten Bereich als neuen Bauplan.

## Nie abbauen, was Spieler gebaut haben

Bob merkt sich jeden Block, den ein Spieler setzt. Bei der Arbeit baut er nie ab oder ersetzt:

- von Spielern gesetzte Blöcke;
- Behälter;
- hergestellte Blöcke (Bretter, Glas, Ziegel, Beton …);
- generierte Strukturen (Dörfer, Tempel …);
- alles in einem geschützten Bereich.

Beim Holzfällen nimmt er nur natürliche Bäume und nie einen Stamm, der zu einem Haus gehört.

```
/bob protect [here [radius]] | list | remove <name> | off
/bob protect check
```

`/bob protect check` sagt dir, ob er den Block abbauen würde, auf den du schaust, und warum.

## Einstellungen

Mit `/bob set <schlüssel> <wert>`, oder per Klick im Steuerpult:

| Schlüssel | Werte | Standard | Bedeutung |
| --- | --- | --- | --- |
| `combat` | passive, defensive, aggressive | defensive | Läuft weg, wehrt sich, oder räumt auch Monster in deiner Nähe weg. |
| `night` | continue, shelter, sleep | continue | Was er nachts macht. |
| `talk` | quiet, normal, chatty | normal | Wie viel er erzählt. |
| `followDistance` | 1–16 | 3 | Abstand in Blöcken zu dir. |
| `teleport` | true/false | true | Holt auf wie ein zahmer Wolf. |
| `keepInventory` | true/false | true | Behält Rucksack und Erfahrung, wenn er stirbt. |
| `workWhileAway` | true/false | true | Arbeitet weiter, während du offline bist. |
| `followWhenIdle` | true/false | true | Folgt dir, wenn es nichts zu tun gibt. |
| `replant` | true/false | true | Pflanzt Setzlinge nach. |
| `torches` | true/false | true | Beleuchtet dunkle Tunnel. |
| `protectStructures` | true/false | true | Lässt generierte Strukturen in Ruhe. |
| `deliverWhenFull` | true/false | true | Geht zur Ablage-Kiste, wenn sein Rucksack voll ist. |
| `toolReserve` | 0–200 | 10 | Hört auf, ein Werkzeug zu benutzen, bevor es kaputtgeht. |
| `nameTag` | true/false | true | Zeigt die Zeile über seinem Kopf. |
| `bossBar` | true/false | true | Zeigt dir den Fortschrittsbalken. |

## Weitere Befehle

```
/bob help | status | queue | clear | list | select <name> | dismiss | forget <name>
/bob come | follow | guard | stay | home [set] | pause | resume | skip | stop
/bob wood [anzahl | in <bereich>]     /bob mine [y [länge]]     /bob dig [bereich]
/bob farm [loop]   /bob smelt ["gegenstand"]   /bob hunt "<mob>" [anzahl]   /bob fish [anzahl]
/bob patrol [radius]   /bob light [radius]   /bob collect
/bob inventory | giveall | deposit
```

Bob behält seine Aufgaben, Bereiche, Kisten und Einstellungen über Neustarts hinweg. Jeder Spieler
kann einen eigenen Bob haben, und ein Spieler auch mehrere.

## Für Entwickler

- Den Mod baust du mit `./gradlew build`.
- Die Spieltests laufen mit `./gradlew runGameTest`. Einzelne Tests wählst du mit
  `-PbotTest="bobbot-gametest:panel_tests*"`.
- Die Bot-Engine (Bewegung, A*-Wegsuche, Aktionen) ist dieselbe, die in
  [Challenge Craft](https://github.com/Kasax007/Kasax_Challenge_Craft) Lockout spielt. Bob kann so
  später als Mitspieler in einer Challenge mitmachen.

Lizenz: CC-BY-4.0
