This page provides a live overview of the current implementation status of AstaPS features and game systems.

It is intended to help contributors and users quickly understand what is already supported, what is being investigated, and what remains incomplete or unverified. Each area is tracked independently so the project can reflect both broad coverage and incremental progress as work continues.

Many sections are still empty because the project is actively being expanded and documented. Contributions are welcome from developers, players, and anyone with relevant knowledge, whether that means helping verify a feature, highlighting a missing or broken feature, filling in missing data, or improving the documentation.

For more detailed information, follow the links in each section to the dedicated feature documentation. The status list is meant to be a practical reference for the current state of the project and should be updated as new features are implemented, corrected, or re-evaluated.

Each feature can have the following statuses:

| Status                                                                | Description                                                                                                                                                            |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ![unknown](https://img.shields.io/badge/unknown-gray)                 | The feature has not been investigated sufficiently to determine its implementation state. An empty status means unknown.                                               |
| ![investigating](https://img.shields.io/badge/investigating-gray)     | Protocol analysis, reverse engineering, client inspection, or investigation of existing implementations is underway, but implementation status is not yet established. |
| ![not-implemented](https://img.shields.io/badge/not--implemented-red) | The feature is understood well enough to establish that the server does not implement it.                                                                              |
| ![in-progress](https://img.shields.io/badge/in--progress-yellow)      | Code is being developed, but the feature is not yet ready.                                                                                                             |
| ![implemented](https://img.shields.io/badge/implemented-yellow)       | The implementation is present, but it has not yet been demonstrated to satisfy the feature's expected behavior.                                                        |
| ![verified](https://img.shields.io/badge/verified-green)              | The feature has passed defined checks for its documented scope. This does not necessarily mean every edge case has been tested.                                        |
| ![complete](https://img.shields.io/badge/complete-green)              | All known requirements and acceptance criteria for the documented scope are implemented and verified. This is a stronger claim than simply working.                    |
| ![broken](https://img.shields.io/badge/broken-red)                    | The feature is present but fails a required behavior, or a regression has rendered it unusable. Use the Notes column to describe the failure.                          |
| ![unsupported](https://img.shields.io/badge/unsupported-purple)       | The feature is _intentionally_ unsupported, excluded from the project scope, or impossible to support under the current constraints. Explain the reason in Notes.      |

> [!NOTE]
> Much of the information in this document is based on the [Genshin Impact Fandom Wiki](https://genshin-impact.fandom.com/wiki/Genshin_Impact_Wiki). Data references are sourced from [Lunaris' API](https://lunaris.moe/) and/or [Project Amber's API](https://gi.yatta.moe/en/). Thank you to those contributors and sources.

# Game systems

## Exploration

See [Exploration documentation](./exploration.md) for more information.

| Feature           | Status | Notes |
| ----------------- | ------ | ----- |
| Player mechanics  |        |       |
| General mechanics |        |       |
| Obstacles         |        |       |
| Misc. mechanics   |        |       |

### Puzzles

| Feature         | Status | Notes |
| --------------- | ------ | ----- |
| Common puzzles  |        |       |
| Mondstadt       |        |       |
| Dragonspine     |        |       |
| Windrest Peak   |        |       |
| Temple of Space |        |       |
| Liyue           |        |       |
| The Chasm       |        |       |
| Chenyu Vale     |        |       |
| Inazuma         |        |       |
| Sumeru          |        |       |
| Fontaine        |        |       |
| Natlan          |        |       |
| Nod-Krai        |        |       |
| Snezhnaya       |        |       |

## Progression

See [Progression documentation](./progression.md) for more information.

| Feature                        | Status | Notes |
| ------------------------------ | ------ | ----- |
| Achievements                   |        |       |
| Adventure Rank and World Level |        |       |
| Adventurer Handbook            |        |       |
| Experience                     |        |       |
| Friendship Level               |        |       |
| Offering systems               |        |       |
| City Reputation                |        |       |
| Serenitea Pot                  |        |       |

## Quests

See [Quests documentation](./quests.md) for more information.

| Feature                           | Status | Notes |
| --------------------------------- | ------ | ----- |
| Archon quests / Main story quests |        |       |
| Interlude quests                  |        |       |
| Story quests                      |        |       |
| World quests                      |        |       |
| Event quests                      |        |       |
| Commissions                       |        |       |
| Random Events                     |        |       |

## Activities

See [Activities documentation](./activities.md) for more information.

| Feature                       | Status                                             | Notes                                                             |
| ----------------------------- | -------------------------------------------------- | ----------------------------------------------------------------- |
| Domains                       |                                                    |                                                                   |
| Exploration                   |                                                    |                                                                   |
| Fishing                       |                                                    |                                                                   |
| Genius Invocation TCG         |                                                    |                                                                   |
| Imaginarium Theater           |                                                    |                                                                   |
| Ley Line Outcrop              | ![broken](https://img.shields.io/badge/broken-red) | #174 gadgets spawned manually, but they cannot be interacted with |
| Repertoire of Myriad Melodies |                                                    |                                                                   |
| Spiral Abyss                  |                                                    |                                                                   |
| Stygian Onslaught             |                                                    |                                                                   |
| Travelers' Tales              |                                                    |                                                                   |
| Valiant Chronicles            |                                                    |                                                                   |
| Miliastra Wonderland          |                                                    |                                                                   |
| Edge of Survival              |                                                    |                                                                   |
| Reset                         |                                                    |                                                                   |

## Items, Shops and Crafting

See [Items documentation](./items.md) for more information.

| Feature        | Status | Notes |
| -------------- | ------ | ----- |
| Inventory      |        |       |
| Food & Potions |        |       |
| Gadgets        |        |       |
| Shops          |        |       |
| Crafting       |        |       |
| Forging        |        |       |
| Cooking        |        |       |
| Processing     |        |       |

## Combat

General combat only. For specific monsters, see [Enemies](#enemies). For playable characters' attack, skill, burst, talents or passives, see [Characters](#characters).

See [Combat documentation](./combat.md) for more information.

| Feature                        | Status | Notes |
| ------------------------------ | ------ | ----- |
| General Combat                 |        |       |
| Elements & Elemental Reactions |        |       |
| Team Bonus                     |        |       |
| Attributes                     |        |       |
| Mechanics                      |        |       |

## Enemies

See [Enemies documentation](./enemies.md) for more information.

| Feature         | Status | Notes |
| --------------- | ------ | ----- |
| Common Enemies  |        |       |
| Elite Enemies   |        |       |
| Normal Bosses   |        |       |
| Weekly Bosses   |        |       |
| Special Enemies |        |       |
| Wildlife        |        |       |

## Characters

See [Characters documentation](./characters.md) for more information.

| Feature                | Status | Notes |
| ---------------------- | ------ | ----- |
| Levelling & Ascensions |        |       |
| Talents                |        |       |
| Constellations         |        |       |
| Weapons                |        |       |
| Artifacts              |        |       |
| Friendship             |        |       |
| Dressing Room          |        |       |

## Account

See [Account documentation](./account.md) for more information.

| Feature              | Status | Notes |
| -------------------- | ------ | ----- |
| Login/Register       |        |       |
| Multiplaform support |        |       |
| Paimon Menu          |        |       |
| Mail                 |        |       |
| Party                |        |       |
| Photo mode           |        |       |
| Shortcut wheel       |        |       |
| Quick start          |        |       |

## Multiplayer

See [Multiplayer documentation](./multiplayer.md) for more information.

| Feature    | Status | Notes |
| ---------- | ------ | ----- |
| Co-Op Mode |        |       |
| Friends    |        |       |
| Chat       |        |       |

## Monetization

See [Monetization documentation](./monetization.md) for more information.

| Feature                         | Status | Notes |
| ------------------------------- | ------ | ----- |
| Battle Pass                     |        |       |
| Blessing of the Welkin Moon     |        |       |
| Character Outfits Shop          |        |       |
| Gift Shop                       |        |       |
| Paimon's Bargains               |        |       |
| Genesis Crystal                 |        |       |
| Primogem                        |        |       |
| Original Resin                  |        |       |
| Wishes                          |        |       |
| Time gatekeeping configurations |        |       |

# Server administration

TODO
