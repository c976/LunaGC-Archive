# Scene-point activation rewards (Global 7.1)

The server loads configurable first-activation rewards from
`rewards/transpoints.lcss`. On first setup, the packaged
`src/main/resources/rewards/transpoints.lcss` is copied into the external
`rewards/` directory. Subsequent reloads preserve operator edits.

Rules use `transpoint` selectors with a string `type` (`waypoint`,
`statue`, or `domain`) and integer `scene` and `point` attributes.
All reward contents, quantities, and probabilities are operator-defined.
The bundled example is **not** verified official Global 7.1 payout data;
see `docs/rewards/source-provenance.md`.

## Activation and settlement are separate

- **Waypoints and Statues of the Seven:** permanent scene-point state, quest
  triggers, map notifications, and saving proceed regardless of reward
  configuration or inventory admission. Eligible first unlocks record a pending
  reward. Releasing a quest-imposed lock does not create another first unlock.
- **Dungeon entrances:** the client's `UnlockTransPointReq` is the reward
  activation. A handbook-supplied map unlock cannot initiate payment. The
  successful activation receipt is tracked in `rewardedDungeonEntries`.
  Reward errors do not suppress `UnlockTransPointRsp`.
- Native pre-unlocked points, hidden points, unsupported portal/tower subtypes,
  and unrelated scene points have no first-activation payout.

Pending activations are stored in each player's `pendingTransPointRewards`
map, keyed by scene and point. Progress is saved before an attempt to settle the
reward. The first activation rolls and saves an immutable reward snapshot before delivery.
An empty snapshot is a deliberate zero reward. A failed inventory admission
leaves the snapshot and pending record in place. If reward configuration cannot
be evaluated at activation, the entitlement stays pending until a valid roll
can be frozen, with the failure recorded in the log. Frozen payout contents are not recalculated after a LootCSS edit.
Pending records are retried when the player logs in
or the same activation request is received again. Already-unlocked points
**without** pending records are never granted retroactive rewards.

## Idempotence and failures

Nonzero and intentional zero-payout activations both use
`RewardGrantJournal` with identity
`transpoint:<uid>:point.<scene>.<point>`.

- The journal persists `PREPARED` before any inventory grant. Its confirmation
  precedes the first item write, and successful delivery finishes `COMPLETED`.
- Completed receipts prevent replay, even when the player-side pending flag
  has not yet been cleared or LootCSS is edited later.
- A failed preflight occurs before receipt preparation and permits a retry.
  Ambiguous `PREPARED` or `CONFIRMED` receipts block blind re-granting;
  they remain pending for operator inspection and reconciliation.
- An empty configured payout is a deliberate settled outcome with an empty
  receipt and no inventory write. It does not block activation.

The journal and player save are separate persistence operations, **not** an
atomic transaction. An unresolved `CONFIRMED` record cannot prove whether
every reward item was delivered. Inspect such entries in
`reward_grant_receipts` before deciding how to reconcile them.

## Other reward sources

World chests, resin-paid world-boss claims, and ley-line blossom claims keep
their existing all-or-nothing inventory admission and payment rules. A rejected
claim cannot silently consume the related chest or resin. Their receipt and
retry semantics must remain source-specific.
