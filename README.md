# Forestry Tracker

A RuneLite plugin that tracks Old School RuneScape Forestry events while you woodcut.

## What it shows

**Overlay** (top-left by default, appears once an event or bark has been seen):

- Current / last event name, and time since the last event started
- Anima-infused bark from the last event
- Bark per hour for the session
- Session bark and lifetime bark
- Events seen this session
- Leaves gathered this session, optionally broken down by type

**Side panel** (tree icon in the sidebar): the same stats, a per-event table with counts and bark,
a history of completed events, a "Reset session" button, and a lifetime section with running totals
for bark, leaves and events per type.

Everything is saved per character in your RuneLite profile. The current session survives closing the
client (it resumes where it left off, or times out as usual), and lifetime totals only reset via the
"Reset lifetime bark" toggle in the plugin settings.

Every overlay line can be toggled in the plugin settings. The session goes idle after a
configurable number of minutes without an event or bark; the next event starts a fresh session
unless "New session after timeout" is turned off.

## How it works

- **Bark** is read from the game message `You've been awarded N Anima-infused bark.`
  Awards that arrive while an event is active (or within 10 seconds of it ending) are added to
  that event; otherwise they are recorded as an "Unknown event".
- **Events** are detected from the NPCs and objects each event spawns (the same ids the built-in
  Woodcutting plugin uses). An event ends when all of its NPCs/objects are gone.
- **Leaves** are counted from changes to your inventory and forestry kit contents. Moving leaves
  between the kit and inventory, or banking them, is not counted. Leaves withdrawn from the bank
  are ignored as long as the bank container updates in the same game tick.

## Known limitations

- Only events you are near enough to render are tracked.
- If the game does not push forestry kit contents while the kit is closed, kit-stored leaves are
  only counted when you open the kit.
- Bark and leaves are only counted for the local player.

## Building

```
gradlew.bat build        # compile + unit tests
gradlew.bat run          # launch RuneLite in developer mode with the plugin loaded
gradlew.bat shadowJar    # build/libs/forestry-tracker-1.0.0-all.jar
```
