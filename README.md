# Bob

*[Deutsch](README.de.md)*

Bob is a companion for Minecraft (Fabric, 26.3). He plays like a player: he walks, digs, builds,
fights and eats the same way you do. You can talk to him in chat, click through his screen, or use
`/bob` commands, and he will:

- chop wood and replant the saplings;
- strip-mine and dig out areas;
- farm, smelt, hunt and fish;
- sort your chests the way you already sort them;
- fetch things from storage;
- build a Litematica or WorldEdit schematic from the materials you put into a chest.

**Bob never breaks what players built.**

## Getting started

1. Install Fabric Loader 0.19.5+ and Fabric API for Minecraft 26.3 (Java 25), then drop the Bob jar
   into `mods/`. The mod has to be on the server. On the client it only adds the `B` key and the
   translations.
2. Use `/bob spawn` to make your Bob, or `/bob spawn Alice` for one with another name.
3. Press **B** (or use `/bob panel`, or right-click Bob) to open his control panel.
4. Or just talk to him: **`Bob, fell 5 trees`**.

## Talking to him

Any chat line that starts with his name (or just `bob`) is for him. He understands English and
German:

| You say | He does |
| --- | --- |
| `bob come here` / `bob komm` | comes to you |
| `bob follow me` / `bob folge mir` | follows you |
| `bob stay` / `bob warte` | waits where he is |
| `bob guard me` / `bob beschütze mich` | stays close and fights for you |
| `bob fell 32 logs` / `bob fäll 5 bäume` | chops wood (5 trees ≈ 25 logs) and replants |
| `bob mine for iron` / `bob geh erze minen` | branch-mines at the right level for the ore |
| `bob dig this out` / `bob heb das aus` | digs out the box marked with the wand |
| `bob harvest` / `bob ernte das feld` | harvests ripe crops and replants (`always` keeps him at it) |
| `bob smelt the iron` / `bob schmelz das eisen` | smelts it in the nearest furnace |
| `bob bring me 16 iron` / `bob hol mir 16 eisen` | fetches it from your chests and gathers whatever is missing |
| `bob sort the chests` / `bob räum auf` | sorts loose items into your storage |
| `bob empty your pack` / `bob pack aus` | empties his pack into the drop-off chest |
| `bob light this up` / `bob beleuchte das` | places torches where it is dark |
| `bob pick everything up` / `bob sammel alles ein` | collects the items lying around |
| `bob build house` / `bob bau haus` | shows the blueprint `house` in front of you |
| `bob build it` / `bob bau los` | starts building it (`bob turn it` / `bob dreh es` rotates it first) |
| `bob protect this house` / `bob schütz das haus` | never touches anything around here |
| `bob what are you doing?` / `bob was machst du?` | tells you his job and how far along it is |
| `bob stop` · `bob pause` · `bob weiter` · `bob skip` | stop, pause, resume, next job |

Orders queue up: when you give an order while Bob is busy, he finishes his current job first. To
make him start a queued job right away, use one of these:

- click **Now** in his reply;
- run `/bob queue now <n>`;
- click the job in the panel.

## The control panel (B)

The panel is a chest-like screen with one icon per action:

| Row | Contents |
| --- | --- |
| Top | Bob's state |
| Orders | come, follow, guard, stay, home (right-click sets his home), pause, skip, stop, status |
| Work | wood (left-click: 64, right-click: endless), mine (left-click: deep, right-click: iron level), dig, farm, smelt, fish, light, guard here, collect |
| Things | sort, empty pack, give me everything, his pack, wand, blueprints, protect here, areas, get… |
| Settings | click to switch to the next value |
| Queue | left-click: do it now; right-click: remove it |

Two indicators also show what Bob is doing:

- a line over his head;
- a bar at the top of your screen with his progress.

You can turn either off in the settings.

**Giving him things:**

| Action | Effect |
| --- | --- |
| Sneak + right-click with an item | hands it to him (he puts armor on right away and uses the best tool for each job) |
| Sneak + right-click with an empty hand | opens his pack |

## Chests, areas and the wand

`/bob wand` gives you his wand. Left-click marks one corner and right-click marks the other. The
marked box can then be used for:

- digging out;
- a work area;
- a protected area;
- a storage room.

```
/bob area add <work|protected|storage> <name>
/bob area list | show <name> | remove <name>
```

Chests have roles. Use `/bob chest add <role>` while you look at the chest, or
`/bob chest set <role> <x y z>`:

| Role | Use |
| --- | --- |
| `dropoff` | Where he empties his pack after a job or when it is full (e.g. while strip-mining). |
| `input` | Materials for building. He only takes from it. |
| `storage` | Part of your sorted storage. Every chest in a storage area counts too. |

**Sorting the way you sort:**

- An item goes into the chest that already holds the same item.
- Otherwise it goes into the chest holding the most similar things (logs to logs, ores to ores,
  food to food).
- Otherwise into an empty storage chest.

Use `/bob sort`, `/bob sort pack` or `/bob sort dropoff` to start sorting, and
`/bob fetch "<item>" [count]` to bring things.

## Building (like Litematica)

1. Put the schematics into one of these folders:
   - `config/bobbot/blueprints/`
   - `schematics/` (Litematica)
   - `config/worldedit/schematics/`

   Bob reads `.litematic`, `.nbt` (structure block) and `.schem` (Sponge v2/v3).
2. Put the materials into chests marked as `input`. `/bob build materials` lists what the build
   needs and what is still missing.
3. Use `/bob build <name>` and he shows its outline where it will go (in front of you).
   `/bob build turn` rotates it and `/bob build here` moves it. `/bob build go` starts the build.

While building:

- He builds bottom-up and takes the materials from the input chests.
- When something runs out, he tells you what is missing and waits until it arrives.
- Afterwards he removes his own scaffolding.

`/bob copy <name>` saves the box marked with the wand as a new blueprint.

## Never breaking what players built

Bob tracks every block a player places. While working he never breaks or replaces:

- blocks placed by players;
- containers;
- crafted blocks (planks, glass, bricks, concrete…);
- generated structures (villages, temples…);
- anything in a protected area.

When he fells trees he only takes natural trees, and he never takes a log that is part of a house.

```
/bob protect [here [radius]] | list | remove <name> | off
/bob protect check
```

`/bob protect check` tells you whether he would break the block you look at, and why.

## Settings

Use `/bob set <key> <value>`, or click them in the panel:

| Key | Values | Default | Meaning |
| --- | --- | --- | --- |
| `combat` | passive, defensive, aggressive | defensive | Runs away, fights back, or also clears monsters near you. |
| `night` | continue, shelter, sleep | continue | What he does at night. |
| `talk` | quiet, normal, chatty | normal | How much he reports. |
| `followDistance` | 1–16 | 3 | Blocks he keeps from you. |
| `teleport` | true/false | true | Catches up like a tamed wolf. |
| `keepInventory` | true/false | true | Keeps his pack and experience when he dies. |
| `workWhileAway` | true/false | true | Keeps working while you are offline. |
| `followWhenIdle` | true/false | true | Follows you when there is nothing to do. |
| `replant` | true/false | true | Replants saplings. |
| `torches` | true/false | true | Lights dark tunnels. |
| `protectStructures` | true/false | true | Leaves generated structures alone. |
| `deliverWhenFull` | true/false | true | Goes to the drop-off chest when his pack is full. |
| `toolReserve` | 0–200 | 10 | Stops using a tool before it breaks. |
| `nameTag` | true/false | true | Shows the line over his head. |
| `bossBar` | true/false | true | Shows the progress bar for you. |

## More commands

```
/bob help | status | queue | clear | list | select <name> | dismiss | forget <name>
/bob come | follow | guard | stay | home [set] | pause | resume | skip | stop
/bob wood [count | in <area>]     /bob mine [y [length]]     /bob dig [area]
/bob farm [loop]   /bob smelt ["item"]   /bob hunt "<mob>" [count]   /bob fish [count]
/bob patrol [radius]   /bob light [radius]   /bob collect
/bob inventory | giveall | deposit
```

Bob keeps his jobs, areas, chests and settings over restarts. Several players can each have their
own Bob, and one player can have more than one.

## For developers

- Build the mod with `./gradlew build`.
- Run the game tests with `./gradlew runGameTest`. Select tests with
  `-PbotTest="bobbot-gametest:panel_tests*"`.
- The bot engine (movement, A* pathfinder, actions) is the same one that plays Lockout in
  [Challenge Craft](https://github.com/Kasax007/Kasax_Challenge_Craft), so Bob can later join a
  challenge as a team-mate.

License: CC-BY-4.0
