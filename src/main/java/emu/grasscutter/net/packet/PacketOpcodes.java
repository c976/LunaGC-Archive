package emu.grasscutter.net.packet;

public final class PacketOpcodes {
    // Empty
    public static final int NONE = 0; //oldVersion | //6.3

    public static final int GetPlayerTokenReq = 21186; ////6.3
    public static final int GetPlayerTokenRsp = 4454; ////6.3
    public static final int PingReq = 25068; ////6.3
    public static final int PingRsp = 29024; ////6.3
    public static final int PlayerLoginReq = 671; ////6.3
    public static final int PlayerLoginRsp = 27285; ////6.3
    public static final int PlayerEnterSceneNotify = 7025; ////6.3
    public static final int EnterSceneReadyReq = 7883; ////6.3
    public static final int EnterScenePeerNotify = 5036; ////6.3
    public static final int EnterSceneReadyRsp = 25524; ////6.3
    public static final int AvatarWeaponSkinDataNotify = 5069; ////6.3
    public static final int AvatarWeaponSkinChangeReq = 663;
    public static final int AvatarWeaponSkinChangeRsp = 21128;
    public static final int MapAreaChangeNotify = 1355; ////6.3
    public static final int SceneInitFinishReq = 7111; ////6.3
    public static final int EntityAnimHashReq = 21188;
    public static final int ChangeLeylineDifficultyReq = 5750;
    public static final int EnterLeyLineChallengeReq = 2578;
    public static final int EnterLeyLineChallengeRsp = 2578;
    public static final int SeeMonsterReq = 28263;
    public static final int SeeMonsterRsp = 4438;
    public static final int EntityAnimHashRsp = 5519;
    public static final int PBJBBLJMCOG = 3235;
    public static final int LeyLineChallengeDetailInfoReq = 5150;
    public static final int PlayerEnterSceneInfoNotify = 5444; ////6.3
    public static final int SceneInitFinishRsp = 24866; ////6.3
    public static final int EnterSceneDoneReq = 8247; ////6.3
    public static final int EnterSceneDoneRsp = 9415; ////6.3
    public static final int PostEnterSceneReq = 21216; ////6.3
    public static final int PostEnterSceneRsp = 2980; ////6.3
    public static final int GetSceneAreaReq = 26337; ////6.3
    public static final int GetSceneAreaRsp = 160; ////6.3
    public static final int GetScenePointReq = 23947; ////6.3
    public static final int GetScenePointRsp = 4531; ////6.3
    public static final int CombatInvocationsNotify = 21302; ////6.3
    public static final int SceneTeamUpdateNotify = 9158; ////6.3
    public static final int AvatarDataNotify = 26236; ////6.3
    public static final int SceneEntityAppearNotify = 20896; ////6.3
    public static final int SceneEntityDisappearNotify = 21953; ////6.3
    public static final int OpenStateUpdateNotify = 6691; ////6.3
    public static final int AddQuestContentProgressReq = 1; //难解 | //6.3
    public static final int AddQuestContentProgressRsp = 1; //难解 | //6.3
    public static final int AvatarEquipChangeNotify = 20439; ////6.3
    public static final int AvatarTeamUpdateNotify = 22933; ////6.3
    public static final int AvatarTeamAllDataNotify = 9394; ////6.3
    public static final int CutSceneBeginNotify = 29753; ////6.3
    public static final int PathfindingEnterSceneReq = 2327; ////6.3
    public static final int ScenePlayerInfoNotify = 6341; ////6.3
    public static final int SyncScenePlayTeamEntityNotify = 1820; ////6.3
    public static final int SyncTeamEntityNotify = 28420; ////6.3
    public static final int ChangeAvatarReq = 23380; ////6.3
    public static final int ChangeAvatarRsp = 549; ////6.3
    public static final int ClientAbilityChangeNotify = 2681; ////6.3
    public static final int ClientAbilityInitFinishNotify = 27610; ////6.3
    public static final int EntityFightPropChangeReasonNotify = 726; ////6.3  // or ??
    public static final int EntityFightPropNotify = 9676; ////6.3
    public static final int EntityFightPropUpdateNotify = 23594; ////6.3
    public static final int EvtBeingHitNotify = 3290; ////6.3
    public static final int EvtBeingHealedNotify = 25228; //oldVersion // no proto | //6.3
       public static final int FurinaLoseHPNotify = 1;
      public static final int GetGameplayRecommendationReq = 23111; ////6.3
            public static final int GetGameplayRecommendationRsp = 23563; ////6.3
      public static final int PlayerDataNotify = 22169; ////6.3
    public static final int EvtBeingHitsCombineNotify = 5585; ////6.3
    public static final int EvtCreateGadgetNotify = 7819; ////6.3
    public static final int EvtDoSkillSuccNotify = 1423; ////6.3
    public static final int FinishedParentQuestNotify = 1; //难解 | //6.3
    public static final int FinishedParentQuestUpdateNotify = 1; //难解 | //6.3
    public static final int GadgetInteractReq = 27351; ////6.3
    public static final int GadgetInteractRsp = 22427; ////6.3
    // 玩家信息
    public static final int GetPlayerSocialDetailReq = 5379; ////6.3
    public static final int GetPlayerSocialDetailRsp = 25639; ////6.3

    public static final int HostPlayerNotify = 28495; ////6.3
    public static final int LifeStateChangeNotify = 5605; ////6.3
    public static final int MarkMapReq = 25820; ////6.3
    public static final int MarkMapRsp = 989; //不解也行 | //6.3
    public static final int NpcTalkReq = 29498; ////6.3
    public static final int NpcTalkRsp = 8431; ////6.3
    public static final int PlayerGameTimeNotify = 2108; ////6.3
    public static final int PlayerSetPauseReq = 3370; ////6.3
    public static final int PlayerSetPauseRsp = 25253; //难解 | //6.3
    public static final int PlayerStoreNotify = 22171; ////6.3
    public static final int PlayerWorldSceneInfoListNotify = 532; ////6.3     // 场景标签
    public static final int QuestDestroyNpcReq = 1; ////6.3
    public static final int QuestDestroyNpcRsp = 1; ////6.3
    public static final int QuestListNotify = 1; ////6.3
    public static final int QuestListUpdateNotify = 1; ////6.3
    public static final int QuestProgressUpdateNotify = 1; ////6.3
    public static final int SceneAreaWeatherNotify = 29909; ////6.3
    public static final int SceneDataNotify = 29201; ////6.3
    public static final int SceneTimeNotify = 2950; ////6.3
    public static final int SceneTransToPointReq = 25962; ////6.3
    public static final int SceneTransToPointRsp = 6535; ////6.3
    public static final int ServerTimeNotify = 20075; ////6.3
    public static final int SetUpAvatarTeamReq = 5636; ////6.3
    public static final int SetUpAvatarTeamRsp = 26686; ////6.3
    public static final int StoreItemChangeNotify = 25402; ////6.3
    public static final int StoreWeightLimitNotify = 9580; ////6.3
    public static final int ToTheMoonEnterSceneReq = 6109; ////6.3
    public static final int UnionCmdNotify = 20832; ////6.3
    public static final int WearEquipReq = 6926; ////6.3
    public static final int WearEquipRsp = 2013; ////6.3
    public static final int WindSeedType1Notify = 6498; ////6.3
    public static final int WorldDataNotify = 22378; ////6.3
    public static final int EvtAvatarLockChairRsp = 5225; ////6.3
    public static final int EvtAvatarStandUpNotify = 27345; ////6.3
    public static final int AvatarLifeStateChangeNotify = 2200; ////6.3
    public static final int AvatarPropNotify = 7643; ////6.3
    public static final int QuestTransmitReq = 1; ////6.3
    public static final int QuestTransmitRsp = 1; ////6.3
    public static final int PlayerTimeNotify = 7279; ////6.3
    public static final int QuestUpdateQuestVarNotify = 1; ////6.3
    public static final int ServerGlobalValueChangeNotify = 22760; ////6.3
    public static final int AvatarDieAnimationEndReq = 3962; ////6.3
    public static final int AvatarDieAnimationEndRsp = 8477; ////6.3
    public static final int PersonalSceneJumpReq = 29327; ////6.3
    public static final int GetWidgetSlotRsp = 29686; ////6.3
    public static final int GetParentQuestVideoKeyRsp = 1; //oldVersion
    public static final int VehicleStaminaNotify = 23490; ////6.3
    public static final int ServerBuffChangeNotify = 5859; ////6.3
    public static final int VehicleInteractReq = 9955; ////6.3
    public static final int VehicleInteractRsp = 24188; ////6.3
    public static final int PlayerPropChangeNotify = 22355; ////6.3
    public static final int PlayerPropChangeReasonNotify = 21396; ////6.3
    public static final int PlayerPropNotify = 643; ////6.3
    public static final int AchievementAllDataNotify = 25831; ////6.3
    public static final int GetActivityInfoReq = 1; //难解 | //6.3
    public static final int GetActivityInfoRsp = 1; //难解 | //6.3
    public static final int SetWidgetSlotReq = 5498; ////6.3
    public static final int SetWidgetSlotRsp = 27773; ////6.3
    public static final int ChangeGameTimeReq = 25341; ////6.3
    public static final int ChangeGameTimeRsp = 867; ////6.3
    public static final int WidgetSlotChangeNotify = 7490; ////6.3
    public static final int UseItemReq = 21895; ////6.3
    public static final int UseItemRsp = 25636; ////6.3
    public static final int EvtAiSyncSkillCdNotify = 3331; ////6.3
    public static final int WorktopOptionNotify = 9974; ////6.3
    public static final int GadgetStateNotify = 895; ////6.3
    public static final int AvatarChangeAnimHashReq = 5701;
    public static final int AvatarChangeAnimHashRsp = 7764;
    public static final int WorldPlayerDieNotify = 20914; ////6.3
    public static final int WorldPlayerReviveReq = 2271; ////6.3
    public static final int AvatarFetterDataNotify = 24320; ////6.3
    public static final int ItemAddHintNotify = 7984; ////6.3
    public static final int GetAreaExplorePointReq = 20685; ////6.3
    public static final int GetAreaExplorePointRsp = 23241; ////6.3
    public static final int ActivityScheduleInfoNotify = 1; ////6.3
    public static final int ActivityInfoNotify = 1; ////6.3
    // 头像
    public static final int GetProfilePictureDataReq = 28253; ////6.3
    public static final int GetProfilePictureDataRsp = 3529; ////6.3
    //祈愿
    public static final int AddNoGachaAvatarCardNotify = 1; ////6.3
    public static final int GachaWishReq = 1; ////6.3
    public static final int GachaWishRsp = 1; ////6.3
    public static final int GetGachaInfoReq = 1; ////6.3
    public static final int GetGachaInfoRsp = 1; ////6.3
    public static final int DoGachaReq = 1; ////6.3
    public static final int DoGachaRsp = 1; ////6.3
    // 名片
    public static final int UnlockNameCardNotify = 27774; ////6.3
    public static final int GetAllUnlockNameCardReq = 0; ////6.3
    public static final int GetAllUnlockNameCardRsp = 2897; ////6.3
    public static final int SetNameCardReq = 3145; ////6.3
    public static final int SetNameCardRsp = 24227; ////6.3
    public static final int UpdatePlayerShowNameCardListReq = 3730; ////6.3
    public static final int UpdatePlayerShowNameCardListRsp = 20032; ////6.3
    // 角色服装
    public static final int AvatarChangeCostumeNotify = 5373; ////6.3
    public static final int AvatarGainCostumeNotify = 6064; ////6.3
    public static final int AvatarChangeCostumeReq = 8017; ////6.3
    public static final int AvatarChangeCostumeRsp = 6019; ////6.3
    // 风之翼
    public static final int AvatarFlycloakChangeNotify = 7898; ////6.3
    public static final int AvatarWearFlycloakReq = 21345; ////6.3
    public static final int AvatarWearFlycloakRsp = 4632; ////6.3
    public static final int AvatarGainFlycloakNotify = 24118; ////6.3
    // 显示角色列表
    public static final int UpdatePlayerShowAvatarListReq = 23333; ////6.3
    public static final int UpdatePlayerShowAvatarListRsp = 21844; ////6.3
    // 游迹
    public static final int AvatarGainTraceEffectNotify = 29281; ////6.3
    public static final int AvatarTraceEffectChangeNotify = 8116; ////6.3
    public static final int AvatarChangeTraceEffectReq = 4397; ////6.3
    public static final int AvatarChangeTraceEffectRsp = 6457; ////6.3

    public static final int EvtDestroyGadgetNotify = 20148; ////6.3

    public static final int GetWidgetSlotReq = 1; //难解 | //6.3
    public static final int EvtAvatarLockChairReq = 3371; ////6.3
    public static final int ObstacleModifyNotify = 2320; ////6.3
    public static final int GetMapAreaReq = 1; //oldVersion | //6.3
    public static final int GetMapAreaRsp = 4773; ////6.3
    public static final int EntityRefreshNotify = 1;
    public static final int EnterWorldAreaReq = 2803; ////6.3
    public static final int EnterWorldAreaRsp = 24303; ////6.3
    public static final int AbilityChangeNotify = 4887; ////6.3
    public static final int SceneAreaUnlockNotify = 20867; ////6.3
    public static final int ScenePointUnlockNotify = 8937; ////6.3
    public static final int AbilityInvocationsNotify = 8579; ////6.3
    public static final int AvatarSkillUpgradeReq = 6802; ////6.3
    public static final int AvatarSkillUpgradeRsp = 9485; ////6.3
    public static final int SkipPlayerGameTimeRsp = 5425; ////6.3
    public static final int SkipPlayerGameTimeReq = 5468; ////6.3
    
    public static final int AbilityInvocationFailNotify = 23722; ////6.3
    public static final int AbilityInvocationFixedNotify = 25109; //oldVersion | //6.3
    public static final int AchievementUpdateNotify = 24153; ////6.3
    public static final int AllWidgetDataNotify = 22052; ////6.3
    public static final int AreaPlayInfoNotify = 29054; //oldVersion | //6.3
    public static final int AvatarAddNotify = 3215; ////6.3
    public static final int AvatarFetterLevelRewardReq = 8503; ////6.3
    public static final int AvatarFetterLevelRewardRsp = 6377; ////6.3
    public static final int AvatarFightPropNotify = 25931; ////6.3
    public static final int AvatarFightPropUpdateNotify = 29697; ////6.3
    public static final int AvatarPropChangeReasonNotify = 6640; ////6.3
    public static final int AvatarSkillChangeNotify = 28800; ////6.3
    public static final int AvatarSkillInfoNotify = 3751; ////6.3
    public static final int AvatarSkillMaxChargeCountNotify = 27723; ////6.3
    public static final int ClientAbilitiesInitFinishCombineNotify = 6050; ////6.3
    public static final int ClientReconnectNotify = 27973; //oldVersion | //6.3
    public static final int DoSetPlayerBornDataNotify = 1; //难解 | //6.3
    public static final int EntityAiSyncNotify = 2229; ////6.3
    public static final int EntityTagChangeNotify = 8431; //oldVersion | //6.3
    public static final int EvtAiSyncCombatThreatInfoNotify = 6689; ////6.3
    public static final int EvtAvatarSitDownNotify = 28307; ////6.3
    public static final int EvtEntityRenderersChangedNotify = 26796; ////6.3
    public static final int LevelTagDataNotify = 1; //oldVersion | //6.3
    public static final int ServerCondMeetQuestListUpdateNotify = 1; ////6.3
    public static final int ParentQuestInferenceDataNotify = 1; //oldVersion
    public static final int PersonalSceneJumpRsp = 28277; ////6.3
    public static final int QuestCreateEntityReq = 1; ////6.3
    public static final int QuestCreateEntityRsp = 1; ////6.3
    public static final int QuestDelNotify = 1; //不好解 | //6.3
    public static final int QuestDestroyEntityReq = 1; ////6.3
    public static final int QuestDestroyEntityRsp = 1; ////6.3
    public static final int QuestGlobalVarNotify = 1; ////6.3
    public static final int QuestUpdateQuestTimeVarNotify = 1; //oldVersion
    public static final int QuestUpdateQuestVarReq = 1; ////6.3
    public static final int QuestUpdateQuestVarRsp = 1; ////6.3
    public static final int QuickUseWidgetReq = 21189; ////6.3
    public static final int QuickUseWidgetRsp = 3015; ////6.3
    public static final int SceneEntityDrownReq = 27964; ////6.3
    public static final int SceneForceLockNotify = 7886; ////6.3
    public static final int SceneForceUnlockNotify = 4064; ////6.3
    public static final int ServerDisconnectClientNotify = 9354; ////6.3
    public static final int SetPlayerBornDataReq = 24588; ////6.3
    public static final int SetPlayerBornDataRsp = 1; ////6.3
    public static final int SetUpLunchBoxWidgetReq = 1; ////6.3
    public static final int SetUpLunchBoxWidgetRsp = 1; ////6.3
    // 奶龙大王666
    public static final int SetEntityClientDataNotify = 7177; ////6.3
    public static final int AvatarSkillDepotChangeNotify = 2749; ////6.3
    public static final int ClientAIStateNotify = 3043; ////6.3
    public static final int GetQuickswapWidgetsReq = 1; //oldVersion | //6.3
    public static final int SetPlayerPropReq = 5863; ////6.3
    public static final int OpenStateChangeNotify = 1811; ////6.3
    public static final int ScenePlayerLocationNotify = 3284; ////6.3
    public static final int WorldPlayerLocationNotify = 8568; ////6.3
    // 好友列表
    public static final int GetPlayerFriendListReq = 815; ////6.3
    public static final int GetPlayerFriendListRsp = 1892; ////6.3
    public static final int GetPlayerBlacklistReq = 26742; ////6.3
    public static final int GetPlayerBlacklistRsp = 3586; ////6.3
    public static final int GetPlayerAskFriendListReq = 3659; ////6.3
    public static final int GetPlayerAskFriendListRsp = 24322; ////6.3
    // 聊天
    public static final int PrivateChatNotify = 880; ////6.3
    public static final int PrivateChatReq = 2030; ////6.3
    public static final int GetRecentMpPlayerListReq = 1;
    public static final int PullPrivateChatReq = 28516; //OK | //6.3
    public static final int PullRecentChatReq = 2985; //OK | //6.3
    // PullPrivateChatRsp|PullRecentChatRsp 23999 or 4408
    public static final int PullPrivateChatRsp = 5150; ////6.3
    public static final int PullRecentChatRsp = 4464; ////6.3
    public static final int ReadPrivateChatReq = 1; //oldVersion
    public static final int SetOpenStateReq = 26892; ////6.3
    public static final int SetOpenStateRsp = 22804; ////6.3
    // 进入传送点区域通知(C->S), 女神像传送点
    public static final int EnterTransPointRegionNotify = 4078; //难解 | //6.3
    // 离开传送点区域通知(C->S), 女神像传送点
    public static final int ExitTransPointRegionNotify = 28036; //难解 | //6.3
    public static final int CombineFormulaDataNotify = 26997; ////6.3
    public static final int GetQuickswapWidgetsRsp = 1; //难解 | //6.3
    public static final int VehiclePhlogistonPointsNotify = 8373; ////6.3
    public static final int EntityAnimatorPairValueInfoNotify = 1;

    public static final int WeaponAwakenReq = 7065; ////6.3
    public static final int WeaponAwakenRsp = 29983; ////6.3
    public static final int GetShopmallDataRsp = 4877; ////6.3
    // 角色突破
    public static final int AvatarPromoteReq = 21336; ////6.3
    public static final int AvatarPromoteRsp = 1123; //oldVersion   // 这玩意就算不对也能正常工作 | //6.3




    public static final int AntiAddictNotify = 3292; ////6.3
    public static final int HitClientTrivialNotify = 740; //oldVersion // | //6.3
    public static final int StoreItemDelNotify = 27184; ////6.3
    public static final int ClientScriptEventNotify = 22749; ////6.3
    public static final int PersonalLineAllDataRsp = 866; ////6.3
    public static final int AskAddFriendRsp = 1; //难解 | //6.3
    public static final int AvatarDelNotify = 26286; //难解 | //6.3
    public static final int WorldChestOpenNotify = 28721; //难解 | //6.3
    public static final int WidgetCoolDownNotify = 4265; ////6.3
    public static final int ForgeFormulaDataNotify = 1832; ////6.3
    public static final int ForgeStartReq = 2468; ////6.3
    public static final int ForgeStartRsp = 21857; //oldVersion // 单个retcode 不用核对cmdid | //6.3
    public static final int AvatarSatiationDataNotify = 25944; ////6.3
    public static final int BlossomBriefInfoNotify = 26535; ////6.3
    public static final int BuyGoodsReq = 22973; ////6.3
    public static final int BuyGoodsRsp = 28869; ////6.3
    public static final int BuyResinReq = 1; // 等待抓取
    public static final int BuyResinRsp = 2479; ////6.3
    public static final int CardProductRewardNotify = 2653; ////6.3
    public static final int CombineReq = 7602; ////6.3
    public static final int CombineRsp = 7351; ////6.3
    public static final int TakeCompoundOutputReq = 9110; ////6.3
    public static final int TakeCompoundOutputRsp = 9261; //难解 | //6.3
    public static final int PlayerCompoundMaterialReq = 24322; ////6.3
    public static final int PlayerCompoundMaterialRsp = 9595; //难解 | //6.3
    public static final int CompoundDataNotify = 123; ////6.3
    public static final int CoopDataNotify = 26875; ////6.3
    public static final int DelTeamEntityNotify = 28837; ////6.3
    public static final int UpdateAbilityCreatedMovingPlatformNotify = 195; ////6.3
    public static final int DropHintNotify = 6325; ////6.3
    public static final int EnterTrialAvatarActivityDungeonRsp = 1; //难解 | //6.3
    public static final int EvtAvatarEnterFocusNotify = 8296; ////6.3
    public static final int EvtAvatarExitFocusNotify = 26824; ////6.3
    public static final int EvtAvatarUpdateFocusNotify = 7989; ////6.3
    public static final int ForgeDataNotify = 7563; //oldVersion // | //6.3
    public static final int ForgeGetQueueDataRsp = 21502; //oldVersion // | //6.3
    public static final int ForgeQueueDataNotify = 21515; //oldVersion // | //6.3
    public static final int FurnitureMakeStartRsp = 7447; //oldVersion // // 29740 or 24667 | //6.3
    public static final int GetAuthkeyReq = 2810; //oldVersion // | //6.3
    public static final int GetAuthkeyRsp = 1602; //oldVersion // | //6.3
    public static final int GetCompoundDataReq = 1944; //oldVersion // // 在例如打开烹饪界面时出现 | //6.3
    public static final int GetCompoundDataRsp = 24441; //oldVersion // | //6.3
    public static final int GivingRecordNotify = 21427; //oldVersion // | //6.3
    public static final int GmTalkRsp = 7926; ////6.3
    public static final int GadgetAutoPickDropInfoNotify = 174; ////6.3
    public static final int ExecuteGadgetLuaReq = 20934; //oldVersion | //6.3
    public static final int ExecuteGadgetLuaRsp = 1; //oldVersion // 单个retcode不用解 | //6.3
    public static final int LevelupCityReq = 22859; //oldVersion // | //6.3
    public static final int LevelupCityRsp = 24390; //oldVersion // | //6.3
    public static final int MassiveEntityElementOpBatchNotify = 6429; ////6.3
    public static final int McoinExchangeHcoinReq = 4933; //没更新proto | //6.3
    public static final int McoinExchangeHcoinRsp = 3665; //没更新proto | //6.3
    public static final int MonsterAIConfigHashNotify = 4739; //oldVersion // | //6.3
    public static final int MonsterAlertChangeNotify = 5583; //oldVersion // | //6.3
    public static final int MonsterSummonTagNotify = 9194; ////6.3
    public static final int MusicGameSettleReq = 1; //oldVersion // | //6.3
    public static final int MusicGameSettleRsp = 1; //oldVersion // // 25598 or 6575 | //6.3
    public static final int MusicGameStartReq = 1; //oldVersion // // 3045 or 20846 | //6.3
    public static final int MusicGameStartRsp = 1; //oldVersion // // 27757 or 20172 or 28729 | //6.3
    public static final int PathfindingEnterSceneRsp = 1; //oldVersion // 单个retcode不用解 | //6.3
    public static final int PlayerApplyEnterHomeNotify = 1; //oldVersion // // 8132 or 38 | //6.3
    public static final int PlayerApplyEnterHomeResultNotify = 4364; //oldVersion // | //6.3
    public static final int PlayerApplyEnterHomeResultReq = 1; //oldVersion // // 378 or 1860 | //6.3
    public static final int PlayerApplyEnterHomeResultRsp = 1; //oldVersion // | //6.3
    public static final int ProudSkillChangeNotify = 9040; ////6.3
    public static final int ProudSkillExtraLevelNotify = 9120; ////6.3
    public static final int ReceivedTrialAvatarActivityRewardReq = 1; //oldVersion // // 5449 or 5667 | //6.3
    public static final int ReceivedTrialAvatarActivityRewardRsp = 1; //oldVersion // // 244 or 1551 | //6.3
    public static final int ReliquaryDecomposeReq = 27757; //oldVersion // | //6.3
    public static final int ReliquaryDecomposeRsp = 4476; //oldVersion // | //6.3
    public static final int ReliquaryUpgradeReq = 21985; ////6.3
    public static final int ReliquaryUpgradeRsp = 2623; ////6.3
    public static final int SceneAudioNotify = 23418; //oldVersion // | //6.3
    public static final int SceneEntityMoveNotify = 26047; ////6.3
    public static final int SceneEntityUpdateNotify = 1; //oldVersion // // 1936 or 6409 | //6.3
    public static final int ScenePlayerSoundNotify = 488; //oldVersion // | //6.3
    public static final int SceneWeatherForcastReq = 20910; //oldVersion // | //6.3
    public static final int SetCoopChapterViewedRsp = 1; //oldVersion // // 2246 or 29751 or 22019 | //6.3
    public static final int ShowCommonTipsNotify = 22175; //oldVersion // | //6.3
    public static final int StartCoopPointReq = 21757; //oldVersion // | //6.3
    public static final int StartCoopPointRsp = 25917; //oldVersion // | //6.3
    public static final int TakeAchievementGoalRewardReq = 1; //oldVersion // // 4455 or 24332 | //6.3
    public static final int TakeAchievementGoalRewardRsp = 1; //oldVersion // // 26805 or 1595 | //6.3
    public static final int TakeAchievementRewardReq = 1; //oldVersion // // 4455 or 24332 | //6.3
    public static final int TakeAchievementRewardRsp = 1; //oldVersion // // 26805 or 1595 | //6.3
    public static final int TakeBattlePassMissionPointReq = 1; //oldVersion // | //6.3
    public static final int TakeBattlePassRewardReq = 21829; //oldVersion | //6.3
    public static final int TakeBattlePassRewardRsp = 22084; //oldVersion | //6.3
    public static final int TakeFurnitureMakeReq = 7364; //oldVersion // | //6.3
    public static final int TakeFurnitureMakeRsp = 8144; //oldVersion // | //6.3
    public static final int TakePlayerLevelRewardReq = 1; ////6.3
    public static final int TakePlayerLevelRewardRsp = 2041; ////6.3
    public static final int UnlockPersonalLineReq = 1; //oldVersion // // 2554 or 727 | //6.3
    public static final int UnlockPersonalLineRsp = 21030; //oldVersion // | //6.3
    // 做饭
    public static final int PlayerCookArgsReq = 1; //oldVersion // | //6.3
    public static final int PlayerCookArgsRsp = 23222; //oldVersion // 主要字段没有特征 先不解 | //6.3
    public static final int PlayerCookReq = 1; //oldVersion // | //6.3
    public static final int PlayerCookRsp = 2728; //oldVersion // | //6.3
    // 销毁材料
    public static final int DestroyMaterialReq = 23822; //oldVersion | //6.3
    public static final int DestroyMaterialRsp = 5760; //oldVersion | //6.3
    // 解锁命座
    public static final int UnlockAvatarTalentReq = 6438; ////6.3
    public static final int UnlockAvatarTalentRsp = 29685; ////6.3
    // 房主踢人
    public static final int SceneKickPlayerReq = 1; ////6.3
    public static final int SceneKickPlayerRsp = 1; //oldVersion // // 7966 or 20150 or 9301 or 24287 or 5497 or 21021 or 7700 | //6.3
    // 自己退房间
    public static final int PlayerGetForceQuitBanInfoReq = 1;
    // 返回单人世界 作用未知
    public static final int BackMyWorldReq = 1; //oldVersion // // 虚假的值
    public static final int BackMyWorldRsp = 1; //oldVersion // | //6.3
    // 客户端退出游戏
    public static final int PlayerForceExitReq = 1;
    public static final int PlayerForceExitRsp = 1; //oldVersion // | //6.3
    // 切换配队
    public static final int ChooseCurAvatarTeamReq = 3886; ////6.3
    public static final int ChooseCurAvatarTeamRsp = 1; //oldVersion // | //6.3
    // 添加配队容量
    public static final int AddBackupAvatarTeamReq = 1; //oldVersion // | //6.3
    public static final int AddBackupAvatarTeamRsp = 1; //oldVersion // | //6.3
    // 角色在秘境死亡
    public static final int DungeonPlayerDieReq = 23442; //oldVersion // | //6.3
    public static final int DungeonPlayerDieRsp = 1; //oldVersion // | //6.3
    // 机关相关
    public static final int PlatformChangeRouteNotify = 1; //oldVersion // | //6.3
    public static final int PlatformStartRouteNotify = 1; //oldVersion // | //6.3
    public static final int PlatformStopRouteNotify = 1; //oldVersion // | //6.3
    // 可能是主角切换元素
    public static final int AvatarChangeElementTypeReq = 28416; //oldVersion | //6.3
    public static final int AvatarChangeElementTypeRsp = 1; //oldVersion // 单个retcode不用解 | //6.3
    // 联机配队
    public static final int ChangeMpTeamAvatarReq = 7524; ////6.3
    public static final int ChangeMpTeamAvatarRsp = 27695; ////6.3
    // 装备卸下
    public static final int TakeoffEquipReq = 4574; ////6.3
    public static final int TakeoffEquipRsp = 1833; ////6.3
    // 纪行
    public static final int SetBattlePassViewedReq = 22541; //oldVersion // | //6.3
    public static final int SetBattlePassViewedRsp = 8733; //oldVersion // // 可能是 22048 533 26489 902 28919 9890 23101 21184 9036 目前实测都无效 | //6.3
    // 深渊
public static final int TowerAllDataReq = 1; //5.7 | //6.3
    public static final int TowerAllDataRsp = 1; ////6.3
    public static final int TowerCurLevelRecordChangeNotify = 1; ////6.3   //
    public static final int EnterTrialAvatarActivityDungeonReq = 1; ////6.3   //
    public static final int TowerEnterLevelReq = 1; ////6.3   //
    public static final int TowerEnterLevelRsp = 1; ////6.3   //
    public static final int TowerFloorRecordChangeNotify = 1; ////6.3
    public static final int TowerLevelStarCondNotify = 1; ////6.3   //
    public static final int TowerTeamSelectReq = 1; ////6.3   //
    public static final int TowerTeamSelectRsp = 1; ////6.3   // 单个retcode不用解
    public static final int TowerLevelEndNotify = 1;
    public static final int TowerRecordHandbookRsp = 1;
    public static final int TowerDailyRewardProgressChangeNotify = 1; //oldVersion // 单个retcode不用解
    // 神像对话
    public static final int GroupSuiteNotify = 26484; ////6.3

    public static final int DelBackupAvatarTeamReq = 1; //oldVersion // | //6.3
    public static final int DelBackupAvatarTeamRsp = 1; //oldVersion // | //6.3
    public static final int GetAllMailResultNotify = 9782; ////6.3
    public static final int BattlePassAllDataNotify = 5955; ////6.3
    public static final int TakeBattlePassMissionPointRsp = 5043; //oldVersion // | //6.3
    public static final int EvtBulletHitNotify = 9880; ////6.3
    public static final int BattlePassCurScheduleUpdateNotify = 23229; ////6.3
    public static final int SceneEntityDrownRsp = 1; //oldVersion // | //6.3
public static final int GetInvestigationMonsterReq = 3760; ////6.3
    public static final int GetInvestigationMonsterRsp = 21358; ////6.3
    public static final int SetPlayerNameReq = 1; ////6.3
    public static final int SetPlayerNameRsp = 21817; ////6.3
    public static final int SetPlayerSignatureReq = 1; ////6.3
    public static final int SetPlayerSignatureRsp = 1816; ////6.3
    public static final int SetPlayerHeadImageReq = 1; ////6.3
    public static final int SetPlayerHeadImageRsp = 1; ////6.3
    public static final int SetPlayerBirthdayReq = 3675; ////6.3
    public static final int SetPlayerBirthdayRsp = 27511; ////6.3
    public static final int AvatarExpeditionDataNotify = 21409; //oldVersion | //6.3
    public static final int BattlePassMissionUpdateNotify = 22447; ////6.3
    public static final int DealAddFriendRsp = 3364; ////6.3
    public static final int CodexDataUpdateNotify = 20299; ////6.3
    public static final int ChapterStateNotify = 28098; //oldVersion // | //6.3
    public static final int OtherPlayerEnterHomeNotify = 20957; //oldVersion // | //6.3
    public static final int PlayerChatNotify = 9857; //oldVersion // | //6.3
    public static final int CreateVehicleReq = 4863; //oldVersion // | //6.3
    public static final int CreateVehicleRsp = 3213; //oldVersion // | //6.3
    // 秘境
    public static final int DungeonEntryInfoReq = 20817; ////6.3
    public static final int DungeonEntryInfoRsp = 26934; ////6.3
    public static final int PlayerEnterDungeonReq = 26234; ////6.3
    public static final int PlayerEnterDungeonRsp = 20476; //这玩意就算不对也能正常工作 | //6.3
    public static final int PlayerQuitDungeonReq = 1114; ////6.3
    public static final int PlayerQuitDungeonRsp = 4676; ////6.3
    public static final int DungeonPlayerDieNotify = 26642; ////6.3
    public static final int ChallengeDataNotify = 1427; ////6.3
    public static final int DungeonChallengeBeginNotify = 27414; ////6.3
    public static final int DungeonChallengeFinishNotify = 6380; ////6.3
    public static final int DungeonSettleNotify = 25419; ////6.3
    public static final int DungeonDieOptionReq = 24018; ////6.3
    public static final int DungeonDieOptionRsp = 1036; ////6.3
    public static final int DungeonEntryToBeExploreNotify = 1; //难解 | //6.3
    public static final int DungeonRestartReq = 1; ////6.3
    public static final int DungeonRestartRsp = 1; //难解 | //6.3
    public static final int DungeonWayPointNotify = 1; ////6.3
    public static final int GetDailyDungeonEntryInfoReq = 1; ////6.3
    public static final int GetDailyDungeonEntryInfoRsp = 26656; ////6.3
    public static final int GetDungeonEntryExploreConditionReq = 1; ////6.3
    public static final int GetDungeonEntryExploreConditionRsp = 3063; ////6.3   // 不好解
    public static final int DungeonDataNotify = 28987; ////6.3
    public static final int DungeonShowReminderNotify = 1; ////6.3   // 不好解
    public static final int DungeonSlipRevivePointActivateReq = 1; ////6.3   // 不好解
    public static final int DungeonSlipRevivePointActivateRsp = 1; ////6.3   // 不好解
    public static final int DungeonWayPointActivateReq = 1; ////6.3   // 不好解
    public static final int DungeonWayPointActivateRsp = 1; ////6.3 

    public static final int SelectWorktopOptionReq = 22523; ////6.3
    public static final int SelectWorktopOptionRsp = 5927; ////6.3
    public static final int WorldPlayerReviveRsp = 1; //oldVersion // | //6.3
    public static final int GetFriendShowAvatarInfoRsp = 3985; //oldVersion // | //6.3
    public static final int WidgetDoBagReq = 21373; //oldVersion | //6.3
    public static final int WidgetDoBagRsp = 1; //oldVersion // | //6.3
    public static final int WidgetGadgetDataNotify = 20772; //oldVersion // | //6.3
    public static final int HomeChooseModuleReq = 1; //oldVersion // | //6.3
    public static final int HomeChooseModuleRsp = 2491; //oldVersion // | //6.3
    public static final int TryEnterHomeReq = 3308; //oldVersion // | //6.3
    public static final int TryEnterHomeRsp = 27962; //oldVersion // | //6.3
    public static final int HomeBasicInfoNotify = 29403; //oldVersion // | //6.3
    public static final int PlayerQuitFromHomeNotify = 9560; //oldVersion // | //6.3
    public static final int HomeModuleSeenReq = 1; //oldVersion | //6.3
    public static final int HomeModuleSeenRsp = 1; //oldVersion | //6.3
    public static final int PlayerHomeCompInfoNotify = 24854; //oldVersion | //6.3
    public static final int HomeComfortInfoNotify = 29761; //oldVersion // | //6.3
    public static final int HomeModuleUnlockNotify = 1; //oldVersion // | //6.3
    public static final int FurnitureCurModuleArrangeCountNotify = 1; //oldVersion // | //6.3
    public static final int HomeMarkPointNotify = 9013; //oldVersion // | //6.3
    public static final int HomeAllUnlockedBgmIdListNotify = 1; //oldVersion // | //6.3
    public static final int HomeAvatarAllFinishRewardNotify = 1; //oldVersion // | //6.3
    public static final int HomeGetBasicInfoReq = 1; //oldVersion //
    public static final int HomePreChangeEditModeNotify = 1; //oldVersion // | //6.3
    public static final int HomeChangeEditModeReq = 1; //oldVersion // | //6.3
    public static final int HomeChangeEditModeRsp = 1; //oldVersion // | //6.3
    public static final int HomeEnterEditModeFinishReq = 1; //oldVersion //
    public static final int HomeEnterEditModeFinishRsp = 1; //oldVersion // | //6.3
    public static final int HomeChangeBgmNotify = 1; //oldVersion // | //6.3
    public static final int HomeChangeBgmReq = 1; //oldVersion // | //6.3
    public static final int HomeGetArrangementInfoReq = 1; //oldVersion // | //6.3
    public static final int HomeGetArrangementInfoRsp = 22694; //oldVersion // | //6.3
    public static final int HomeUpdateArrangementInfoRsp = 1; //oldVersion // | //6.3
    public static final int HomeUpdateArrangementInfoReq = 25393; //oldVersion // | //6.3
    public static final int GetShopReq = 25227; ////6.3
    public static final int GetShopRsp = 24324; ////6.3
    public static final int PersonalLineAllDataReq = 7502; //oldVersion // | //6.3
    public static final int CodexDataFullNotify = 7489; ////6.3
    public static final int HomeAvatarSummonAllEventNotify = 28479; //oldVersion // | //6.3
    public static final int HomeAvatarSummonEventReq = 2966; //oldVersion // | //6.3
    public static final int HomeAvatarRewardEventNotify = 23306; //oldVersion // | //6.3
    public static final int HomeAvatarTalkFinishInfoNotify = 3064; //oldVersion // | //6.3
    public static final int HomeAvatarTalkRsp = 3595; //oldVersion // | //6.3
    public static final int HomeSceneJumpReq = 1; //oldVersion // | //6.3
    public static final int HomeSceneJumpRsp = 1; //oldVersion // | //6.3
    public static final int HomeTransferReq = 26094; //oldVersion // | //6.3
    public static final int GetHomeLevelUpRewardReq = 1; //oldVersion // // proto | //6.3
    public static final int HomeAvatarRewardEventGetReq = 1; //oldVersion // | //6.3
    public static final int HomeAvatarRewardEventGetRsp = 1; //oldVersion // | //6.3
    public static final int GetAllMailNotify = 1; ////6.3
    public static final int HomeAvatarCostumeChangeNotify = 1; //oldVersion // | //6.3
    public static final int PlayerApplyEnterMpResultNotify = 8682; ////6.3
    public static final int PlayerApplyEnterMpNotify = 8573; ////6.3
    public static final int PlayerApplyEnterMpReq = 1; ////6.3
    public static final int PlayerApplyEnterMpRsp = 1; ////6.3
    public static final int PlayerApplyEnterMpResultReq = 1; ////6.3
    public static final int AskAddFriendNotify = 1; //oldVersion // | //6.3
    public static final int AskAddFriendReq = 1; ////6.3
    public static final int DealAddFriendReq = 8380; ////6.3
    public static final int GetFriendShowAvatarInfoReq = 1; //oldVersion // | //6.3
    public static final int GetFriendShowNameCardInfoRsp = 22781; //oldVersion | //6.3
    public static final int SetFriendEnterHomeOptionReq = 5188; //oldVersion // | //6.3
    public static final int GetOnlinePlayerListReq = 1;
    public static final int GetOnlinePlayerListRsp = 3408; ////6.3
    public static final int GetShopmallDataReq = 1; ////6.3
    public static final int GetActivityShopSheetInfoReq = 1; //oldVersion // // proto | //6.3
    public static final int GetActivityShopSheetInfoRsp = 1; //oldVersion // // proto | //6.3
    public static final int ServerAnnounceNotify = 4045; ////6.3
    public static final int GetWorldMpInfoReq = 1;
    public static final int GetWorldMpInfoRsp = 1; //oldVersion // // proto | //6.3
    public static final int ResinChangeNotify = 1; ////6.3
    public static final int GetMailItemReq = 1; ////6.3
    public static final int GetMailItemRsp = 2010; //oldVersion | //6.3
    public static final int MailChangeNotify = 28792; ////6.3
    public static final int ReadMailNotify = 1; //oldVersion // | //6.3
    public static final int ChangeMailStarNotify = 4020; //oldVersion | //6.3
    public static final int DelMailReq = 1; //oldVersion // | //6.3
    public static final int DelMailRsp = 4157; //oldVersion | //6.3
    public static final int HitTreeNotify = 7060; ////6.3
    public static final int TheaterLobbySceneJumpReq = 1; //oldVersion // // code | //6.3
    public static final int TheaterLobbySceneJumpRsp = 1; //oldVersion // // code | //6.3
    public static final int UnfreezeGroupLimitNotify = 1; //oldVersion // | //6.3
    public static final int QueryCodexMonsterBeKilledNumReq = 21207; ////6.3
    public static final int QueryCodexMonsterBeKilledNumRsp = 22755; ////6.3
    public static final int GetRegionSearchReq = 1; //oldVersion //
    public static final int PlayerChatReq = 7710; ////6.3
    public static final int GetPlayerHomeCompInfoReq = 1; //oldVersion // | //6.3
    public static final int GetFurnitureCurModuleArrangeCountReq = 1; //oldVersion // | //6.3
    public static final int FurnitureMakeReq = 1; //oldVersion // | //6.3
    public static final int ChangeTeamNameReq = 23846; ////6.3
    public static final int ChangeTeamNameRsp = 9326; //oldVersion | //6.3
    public static final int WorldPlayerRTTNotify = 1; ////6.3
    public static final int PlayerApplyEnterMpResultRsp = 1; ////6.3
    public static final int PlayerGetForceQuitBanInfoRsp = 8197; //oldVersion | //6.3
    public static final int PlayerPreEnterMpNotify = 26767; ////6.3
    public static final int WorldPlayerInfoNotify = 20951; ////6.3
    public static final int WeaponPromoteReq = 8763; ////6.3
    public static final int WeaponPromoteRsp = 4092; ////6.3
    public static final int WeaponUpgradeReq = 1; ////6.3
    public static final int WeaponUpgradeRsp = 24127; ////6.3
    public static final int CalcWeaponUpgradeReturnItemsReq = 1; ////6.3
    public static final int CalcWeaponUpgradeReturnItemsRsp = 7369; ////6.3
    public static final int SetEquipLockStateReq = 23529; ////6.3
    public static final int SetEquipLockStateRsp = 29807; ////6.3
    public static final int AvatarUpgradeReq = 22502; ////6.3
    public static final int AvatarUpgradeRsp = 1; ////6.3
    public static final int AvatarUnlockTalentNotify = 2893; ////6.3
    public static final int HomeAvatarTalkReq = 1; //oldVersion // | //6.3
    public static final int GetQuestLackingResourceRsp = 1; //oldVersion //
    public static final int QueryPathReq = 2318; ////6.3
    public static final int QueryPathRsp = 2391; ////6.3
    public static final int UnlockTransPointReq = 1; ////6.3
    public static final int UnlockTransPointRsp = 1; //oldVersion // // 可以忽略 | //6.3


    // Packet Names
    public static final int PlayerEnterChildMapLayerNotify = 1; //oldVersion // 没有作用
    public static final int PlayerEnterMapLayerNotify = 1; //oldVersion // 没有作用
    public static final int ServerAnnounceRevokeNotify = 1; //oldVersion // 没有作用 | //6.3
    public static final int PlayerChatRsp = 1; //oldVersion // 可以忽略 | //6.3
    public static final int FurnitureMakeRsp = 5478; //oldVersion // | //6.3

    public static final int ActivityTakeWatcherRewardReq = 1; //oldVersion | //6.3
    public static final int ActivityTakeWatcherRewardRsp = 1; //oldVersion | //6.3
    public static final int ActivityUpdateWatcherNotify = 1; //oldVersion | //6.3
    // 可能是角色派遣
    public static final int AvatarExpeditionAllDataReq = 1; //oldVersion // | //6.3
    public static final int AvatarExpeditionAllDataRsp = 22989; //oldVersion | //6.3
    public static final int AvatarExpeditionCallBackReq = 22524; //oldVersion // | //6.3
    public static final int AvatarExpeditionCallBackRsp = 24610; //oldVersion // | //6.3
    public static final int AvatarExpeditionGetRewardReq = 27015; //oldVersion // | //6.3
    public static final int AvatarExpeditionGetRewardRsp = 20793; //oldVersion // | //6.3
    public static final int AvatarExpeditionStartReq = 20463; //oldVersion // | //6.3
    public static final int AvatarExpeditionStartRsp = 28616; //oldVersion // | //6.3

    public static final int BargainOfferPriceReq = 26322; //oldVersion // | //6.3
    public static final int BargainOfferPriceRsp = 24803; //oldVersion // | //6.3
    public static final int BargainStartNotify = 9216; //oldVersion // | //6.3
    public static final int BargainTerminateNotify = 1; //oldVersion // | //6.3
    public static final int BeginCameraSceneLookNotify = 2551; ////6.3
    public static final int BuyBattlePassLevelReq = 1; //oldVersion //  可能纪行有关 | //6.3
    public static final int BuyBattlePassLevelRsp = 1; //oldVersion // | //6.3

    public static final int CanUseSkillNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int CancelCoopTaskReq = 1; //oldVersion // 未实现的数据包 | //6.3
    //  上面的可以不解
    public static final int CheckUgcStateReq = 1; //oldVersion //
    public static final int CheckUgcStateRsp = 1; //oldVersion //
    public static final int CheckUgcUpdateReq = 1; //oldVersion // | //6.3
    public static final int CheckUgcUpdateRsp = 23144; //oldVersion // | //6.3
    public static final int ClientLockGameTimeNotify = 1; //oldVersion // 我觉得不用解 | //6.3
    public static final int CloseCommonTipsNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int CombineDataNotify = 1; //oldVersion // 不好解 | //6.3

    public static final int CookDataNotify = 26141; //oldVersion // | //6.3
    public static final int CookRecipeDataNotify = 5257; //oldVersion // | //6.3

    public static final int DeleteFriendNotify = 1; //oldVersion // 等待抓取 | //6.3
    public static final int DeleteFriendReq = 1; //oldVersion // | //6.3
    public static final int DeleteFriendRsp = 1; //oldVersion // | //6.3


    public static final int EndCameraSceneLookNotify = 1; //oldVersion // 等待抓取 | //6.3

    public static final int EntityAiKillSelfNotify = 1; ////6.3

    public static final int EvtBulletDeactiveNotify = 1; //oldVersion // 懒得解了反正解不解都没事 | //6.3
    public static final int EvtBulletMoveNotify = 25624; //oldVersion // 懒得解了反正解不解都没事 | //6.3

    public static final int FireworksLaunchDataNotify = 21203; //oldVersion // 套娃太多了 | //6.3
    public static final int FireworksReformDataNotify = 23002; //oldVersion // 套娃太多了 | //6.3

    public static final int ForgeGetQueueDataReq = 1; //oldVersion // 等待抓取
    public static final int ForgeQueueManipulateReq = 23279; ////6.3
    public static final int ForgeQueueManipulateRsp = 23860; ////6.3

    public static final int FurnitureMakeStartReq = 4254; //oldVersion // | //6.3

    public static final int GetAllActivatedBargainDataReq = 1; //oldVersion // 等待抓取 | //6.3
    public static final int GetAllActivatedBargainDataRsp = 1660; //oldVersion // | //6.3

    public static final int GetAllH5ActivityInfoReq = 1; //oldVersion // 等待抓取

    public static final int GetBargainDataReq = 1; //oldVersion // 不好解 | //6.3
    public static final int GetBargainDataRsp = 20438; //oldVersion // | //6.3

    public static final int GetChatEmojiCollectionReq = 1; //oldVersion // 等待抓取 | //6.3
    public static final int GetChatEmojiCollectionRsp = 27111; //oldVersion // 不好解 | //6.3

    public static final int GetFriendShowNameCardInfoReq = 1; //oldVersion // 等待抓取 | //6.3
    public static final int GetHomeLevelUpRewardRsp = 1; //oldVersion // 不好解 | //6.3
    public static final int GetUgcBriefInfoReq = 1; //oldVersion // 跳过 | //6.3
    public static final int GetUgcBriefInfoRsp = 1140; //oldVersion // 跳过 | //6.3
    public static final int GetUgcReq = 7776; //oldVersion // | //6.3
    public static final int GetUgcRsp = 5243; //oldVersion // | //6.3

    public static final int GmTalkReq = 1; //oldVersion // 等待抓取 | //6.3
    public static final int GroupUnloadNotify = 20068; //oldVersion // 不好解 | //6.3
    public static final int H5ActivityIdsNotify = 1; //oldVersion // 等待抓取 | //6.3

    // 家园
    public static final int HomeAvatarSummonEventRsp = 1; //oldVersion // | //6.3
    public static final int HomeAvatarSummonFinishReq = 1; //oldVersion // | //6.3
    public static final int HomeAvatarSummonFinishRsp = 1; //oldVersion // | //6.3
    public static final int HomeChangeBgmRsp = 1; //oldVersion // | //6.3
    public static final int HomeChangeModuleReq = 1; //oldVersion // | //6.3
    public static final int HomeChangeModuleRsp = 1; //oldVersion // | //6.3
    public static final int HomeGetOnlineStatusReq = 1; //oldVersion //
    public static final int HomeGetOnlineStatusRsp = 1; //oldVersion | //6.3
    public static final int HomeKickPlayerReq = 22611; //oldVersion // | //6.3
    public static final int HomeKickPlayerRsp = 3229; //oldVersion // | //6.3
    public static final int HomeNewUnlockedBgmIdListNotify = 1; //oldVersion // | //6.3
    public static final int HomeResourceNotify = 25355; //oldVersion // | //6.3
    public static final int HomeResourceTakeFetterExpReq = 1; //oldVersion //
    public static final int HomeResourceTakeFetterExpRsp = 1; //oldVersion // | //6.3
    public static final int HomeResourceTakeHomeCoinReq = 1; //oldVersion //
    public static final int HomeResourceTakeHomeCoinRsp = 1; //oldVersion // | //6.3
    public static final int HomeSaveArrangementNoChangeReq = 1; //oldVersion // | //6.3
    public static final int HomeSaveArrangementNoChangeRsp = 28960; //oldVersion // | //6.3
    public static final int HomeSceneInitFinishReq = 1; //oldVersion //
    public static final int HomeSceneInitFinishRsp = 1; //oldVersion // | //6.3
    public static final int HomeTransferRsp = 1; //oldVersion // | //6.3

    // 好像用不到这俩了
    public static final int ItemGivingReq = 20468; //oldVersion // | //6.3
    public static final int ItemGivingRsp = 23109; //oldVersion // | //6.3

    public static final int LaunchFireworksReq = 3946; //oldVersion // 不好解 | //6.3
    public static final int LaunchFireworksRsp = 1; //oldVersion // 单个retcode 不用解 | //6.3

    public static final int MarkNewNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int MonsterForceAlertNotify = 941; //oldVersion // 不好解 | //6.3
    public static final int PlayerLevelRewardUpdateNotify = 1922; //oldVersion // 不好解 | //6.3
    public static final int ReformFireworksReq = 4784; //oldVersion // 太麻烦了 先不搞 | //6.3
    public static final int ReformFireworksRsp = 1; //oldVersion // 单个retcode 不用解 | //6.3
    public static final int SetChatEmojiCollectionReq = 890; //oldVersion // 不好解 而且看起来没什么用 跳过 | //6.3
    public static final int SetChatEmojiCollectionRsp = 1; //oldVersion // 单个retcode不用解 | //6.3

    public static final int SetFriendEnterHomeOptionRsp = 1; //oldVersion // 单个retcode 不用解 | //6.3
    public static final int SetPlayerPropRsp = 3365; //oldVersion // 单个retcode不用解 | //6.3

    public static final int ShowClientGuideNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int TowerMiddleLevelChangeTeamNotify = 1; //oldVersion // 待抓取 | //6.3
    public static final int UnlockedFurnitureFormulaDataNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int UnlockedFurnitureSuiteDataNotify = 1; //oldVersion // 不好解 | //6.3
    public static final int SetReliquaryFavouriteReq = 1;
    public static final int SetReliquaryFavouriteRsp = 1;
}
