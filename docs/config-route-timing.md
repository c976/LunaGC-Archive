# Ordinary configured platform routes

Ordinary configured routes, including Seelie routes, use distance and positive target velocity to determine their timeline. Route-point callbacks must follow the scene clock advertised to the client, rather than the number or iteration order of scheduler polls.

## Start and approach

The authored source point is the timeline origin. Starting at point zero preserves the gadget's existing position when publishing the start. The approach to that source occurs before the advertised origin, with a lead of `max(300 ms, ceil(distance / source velocity * 1000))`. For example, a 7.875-unit approach at 4.5 units per second needs 1,750 ms. A resume at an already-reached source retains the usual 300 ms lead. Neither approach duration nor lead is added again to subsequent authored-point travel durations.

The complete ordinary route plan is validated before resetting a completed index, dispatching the initial event, installing tasks, or publishing state. Missing points or positions, nonpositive or nonfinite velocity, and durations exceeding the signed scene-clock comparison horizon cause the start to fail without those mutations.

## Deadlines and cancellation

Each point has a cancellable repeating scheduler task. Polling checks the advertised scene-clock deadline, including signed 32-bit clock wrap, and never advances early. Due points advance in route order even if scheduler iteration encounters a later task first. Stop, restart, and route replacement invalidate the old run under the gadget monitor, so retained callbacks cannot advance a replacement run. Each task advances at most once.

The first `reachStop` marker after the source ends the active segment. Starting again resumes from the reached point without stopping immediately at that same marker. Reaching the final point closes the route state and cancels its tasks without a stop broadcast: 7.1 has no stop opcode, so a stop there would refresh the entity under anyone standing on it. Routes whose points carry no target velocity (they are timed by `speedLevel`, about a quarter of 7.1 routes) and the Amber water platforms keep the legacy start path. Liyue dungeon platforms retain their existing wait-time and rotation handling instead of using the ordinary distance/velocity plan.

## Resource and protocol compatibility

Route resources accept canonical field names and underscore-prefixed route and point fields, including `_reachStop`. Scene-route containers accept both supported resource key families. Serialization continues to use canonical names. Start, stop and route changes go out as the 7.1 `PlatformStartRouteNotify` (29891), `PlatformStopRouteNotify` (4699) and `PlatformChangeRouteNotify` (6989); the entity-refresh fallback only remains for a build that unmaps them. An avatar-near-platform event with no parameters takes the same source-entity config-id workaround as a zero first parameter, and is dropped only when that entity is gone.

## Verification

Regression tests exercise the production gadget, script library, and scheduler without a live server. They cover multiple scheduler rates, frozen and wrapping clocks, overdue ordering, cancellation and concurrent callbacks, atomic invalid-start handling, initial approach, resume, stop markers, resource aliases, packet fallback, and existing Liyue behavior.

The final initial-approach behavior was also confirmed in live play before preparing this change. Automated regression tests do not substitute for that visual check.
