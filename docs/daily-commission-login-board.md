# Reannounce the stored commission board on login

Same-day login already sends the daily summary, but generating no new quota also means no owner-board notification. The client can consequently lose the Commissions tab after relog despite an existing stored board.

After normal initialization, send the existing self board when the player is Adventure Rank 12+ and exactly four tasks are present. Do not save, reroll, migrate or reset the board, preference, task progress or claim markers. This is a self notification, not a broadcast. Under-rank, empty and incomplete boards are not advertised as four tasks.

`DailyTaskLoginSyncTest` exercises actual player/session framing with an in-memory transport and checks the summary/board order, repeated login, persisted Random/city preferences, unchanged task identities/progress and no extra saves. Separate local two-login checks confirmed tab persistence with unchanged rewards/quota; this is not a fix for the still-unresolved preferred-region dropdown.
