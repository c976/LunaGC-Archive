# Nearby daily-commission encounters

Commission groups should not be simulated kilometres away from a player. Remote enemies can receive movement updates and die without legitimate nearby combat; rejecting a distant kill does not prevent the encounter from being lost.

## Lifecycle

The world host owns the board. Periodic scene polling runs at most once per second; explicit load, progress and save boundaries remain immediate. Each unfinished task's authored `centerPosition` is resolved through the loaded scene-3 JSON dummy points, with a fallback to `Scripts/Scene/3/scene3_dummy_points.lua` for points omitted from that JSON. The fallback evaluates the authored table in an isolated Lua environment without libraries or shared script globals, and caches coordinates (including a missing-file result) for the scene's lifetime. Resource changes require a new scene/server restart. Its `enterDistance`/`exitDistance` define three-dimensional server-policy boundaries. Any fully loaded visitor in the same scene/world can retain the host's encounter. Coordinates missing or invalid in both sources retain the board and fail activation closed rather than substituting an origin, eager-loading remote encounters or rerolling tasks.

Only groups activated by this lifecycle are owned. Existing quest/challenge or unrelated preloaded groups are not adopted or removed. Leaving the range removes the owned population nonlethally: it does not call monster death handlers, increment tasks or grant rewards. Delayed creation and wave-repair callbacks cannot recreate a dormant owned encounter. The existing minimum-rank and nearby-death guards remain in place.

A bounded, versioned snapshot on the existing group instance records surviving entity configurations, poses, monster HP, gadget states, active triggers/invocation counts and regions. Snapshots are taken on activation, post-Lua commission progress, suspension and existing scene/world group saves, not on every nearby poll. Resume restores that population without replaying initial-suite/group-load scripts, including later-wave enemies created directly by Lua. Restored survivors are inserted without `onCreate()`: specifically, `EVENT_GADGET_CREATE` and monster-live actions are not emitted again. Scripts must not rely on those creation callbacks to reconstruct additional unsaved state; such encounter families still require qualification. Existing scene/world group saves persist the snapshot; proximity adds no board/quota/player save or cross-document crash-atomicity guarantee.

Tide registration is separate from first-turn publication, which uses scene-before-script lock order and respects cancellation/departure. Snapshots distinguish an interrupted, not-yet-published first turn from an already-started tide. Only an explicitly unstarted tide publishes its initial population on resume; older snapshots retain their already-started semantics.

Malformed, oversized, incompatible or invalid snapshots are cleared from active/cached aliases before one ordinary suite refresh, so an issued encounter does not become permanently unplayable. This recovery does not reroll the board, change task progress or grant rewards. Validation precedes installation of restored state; failures during entity construction or script execution are not blindly retried as a second refresh over partial state.

Explicit completion/reset invalidates both active and cached snapshots before unregister can queue persistence. A refused or throwing unregister restores the previous snapshot and ownership. Ordinary proximity suspension retains its snapshot instead. Zero-HP dying monsters are excluded.

This is not complete serialization of native AI, ability modifiers or arbitrary script timers, nor a claim about exact proprietary streaming boundaries. Other timer-driven encounter families require qualification.

## Validation

`DailyTaskGroupLifecycleTest` exercises real manager/scene loader and unloader paths with in-memory entity-construction, inventory, persistence and transport boundaries. It covers distance hysteresis, vertical distance, delayed callbacks, later waves, trigger/HP preservation, recoverable malformed snapshots, new-day isolation, world/guest/quest ownership and one-time completion rewards. Additional tests cover Lua center fallback and isolation, per-scene polling throttles, absence of periodic serialization, corrupt snapshot variants/cache aliases, and concurrent tide tracking. `DailyTaskUnregisterStateTest` covers snapshot-before-save ordering, aliasing and failure rollback. Eligibility regressions preserve upstream's Adventure Rank 12 guard and explicit stored Random preference.

Local client checks include distant no-input noncompletion, legitimate protected combat, cold partial-wave restoration, and a completed task's single payment/cold persistence. These checks used a composed local deployment with other safeguards; they are not an upstream clean-checkout client acceptance claim. Tests on this PR use synthetic in-memory state and require no accounts, database, resource pack or client files.

The passive cooldown lookup fix is a companion change: cooldown bookkeeping must not reload a group just unloaded by completion. Region-selector semantics and natural quest eligibility are separate, unresolved concerns.
