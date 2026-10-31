package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.utils.*;
import io.netty.buffer.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import lombok.*;

public class GameSession implements GameSessionManager.KcpChannel {
    private final GameServer server;
    private GameSessionManager.KcpTunnel tunnel;

    @Getter
    @Setter
    private Account account;
    @Getter
    private Player player;

    // Session-scoped GM playback completions must not drive gameplay state machines.
    private final ManualCutsceneTracker manualCutscenes = new ManualCutsceneTracker();

    public boolean registerManualCutscene(int cutsceneId) {
        return manualCutscenes.register(cutsceneId);
    }

    public void cancelManualCutscene(int cutsceneId) {
        manualCutscenes.cancel(cutsceneId);
    }

    public boolean consumeManualCutscene(int cutsceneId) {
        return manualCutscenes.consume(cutsceneId);
    }

    @Getter
    private long encryptSeed = Crypto.ENCRYPT_SEED;
    private byte[] encryptKey = Crypto.ENCRYPT_KEY;

    @Setter
    private boolean useSecretKey;

    // Whether this session has already reported a frame that would not decrypt.
    private boolean reportedBadMagic;

    // Both shutdown and the transport's close callback close a session; only the first counts.
    private boolean closeHandled;

    // Packet classes already reported as having no 7.0 CmdId, so each is said once.
    private static final java.util.Set<String> missingCmdIdReported =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    @Getter
    @Setter
    private SessionState state;
    // The last MOTION_LAND_SPEED sample; a landing time of 0 means no fall is pending.
    @Getter
    @Setter
    private float landingSpeed;
    @Getter
    @Setter
    private long landingTimeMillis;

    @Getter
    private int clientTime;
    @Getter
    private long lastPingTime;
    private int lastClientSeq = 10;

    public GameSession(GameServer server) {
        this.server = server;
        this.state = SessionState.WAITING_FOR_TOKEN;
        this.lastPingTime = System.currentTimeMillis();

        if (GAME_INFO.useUniquePacketKey) {
            this.encryptKey = new byte[4096];
            this.encryptSeed = Crypto.generateEncryptKeyAndSeed(this.encryptKey);
        }
    }

    public GameServer getServer() {
        return server;
    }

    public InetSocketAddress getAddress() {
        try {
            return tunnel.getAddress();
        } catch (Throwable ignore) {
            return null;
        }
    }

    public boolean useSecretKey() {
        return useSecretKey;
    }

    public String getAccountId() {
        return this.getAccount().getId();
    }

    public synchronized void setPlayer(Player player) {
        this.player = player;
        this.player.setSession(this);
        this.player.setAccount(this.getAccount());
    }

    public boolean isLoggedIn() {
        return this.getPlayer() != null;
    }

    public void updateLastPingTime(int clientTime) {
        this.clientTime = clientTime;
        this.lastPingTime = System.currentTimeMillis();
    }

    public int getNextClientSequence() {
        return ++lastClientSeq;
    }

    public void replayPacket(int opcode, String name) {
        Path filePath = FileUtils.getPluginPath(name);
        File p = filePath.toFile();

        if (!p.exists())
            return;

        byte[] packet = FileUtils.read(p);

        BasePacket basePacket = new BasePacket(opcode);
        basePacket.setData(packet);

        send(basePacket);
    }

    private static final java.util.Set<Integer> MUTED_LOG_OPCODES = java.util.Set.of(2497);

    public void send(BasePacket packet) {

        // The last word on cutscenes. The two places that build one already check this, so nothing
        // should reach here - it is logged rather than dropped silently precisely so that a
        // cutscene the server did send stops being invisible.
        if (GAME_OPTIONS.disableCutscenes
                && packet.getOpcode() == PacketOpcodes.CutSceneBeginNotify) {
            Grasscutter.getLogger()
                    .info("Suppressed a CutSceneBeginNotify: game.disableCutscenes is on.");
            return;
        }

        if (packet.getOpcode() <= 0) {
            // A non-positive opcode is a message 7.1 has no known CmdId for. Named once per packet
            // class, at debug: there are dozens of them and none is actionable in normal play.
            if (missingCmdIdReported.add(packet.getClass().getSimpleName())) {
                Grasscutter.getLogger().debug("{} has no 7.1 CmdId, so it is not being sent.",
                        packet.getClass().getSimpleName());
            }
            return;
        }

        if (packet.shouldBuildHeader()) {
            packet.buildHeader(this.getNextClientSequence());
        }

        SendPacketEvent event = new SendPacketEvent(this, packet);
        event.call();
        if (!event.isCanceled()) {
            try {
                packet = event.getPacket();
                DecodedPacketLogger.logDecodedPacket(this, "SEND", packet.getOpcode(),
                        packet.getData(), null);

                var bytes = packet.build();
                if (packet.shouldEncrypt) {
                    if (Grasscutter.getConfig().server.game.useXorEncryption) {
                        Crypto.xor(bytes,
                                packet.useDispatchKey() || !useSecretKey() ? Crypto.DISPATCH_KEY
                                        : this.encryptKey);
                    }
                }
                tunnel.writeData(bytes);
            } catch (Exception e) {
                Grasscutter.getLogger().debug("Unable to send packet {} to client: {}",
                        packet.getOpcode(), e.toString());
            }
        }
    }

    @Override
    public void onConnected(GameSessionManager.KcpTunnel tunnel) {
        this.tunnel = tunnel;
        Grasscutter.getLogger()
                .info(translate("messages.game.connect", this.getAddress().toString()));
    }

    /**
     * Decrypts a frame in place, working out which key it was sent under rather than assuming.
     *
     * <p>
     * The token exchange has a step where the two sides disagree about the key: the client moves to
     * the session key the moment it accepts the response, and the server only knows that happened
     * when a frame arrives. Guessing wrong in either direction is silent - the frame decrypts to
     * noise and is dropped - and on a version migration that is indistinguishable from a client
     * that never replied, which is exactly the false trail this cost us before.
     *
     * <p>
     * The frame magic settles it: XOR is its own inverse, so a wrong key can be undone and the
     * other one tried, and whichever produces the magic is the key the client is actually using.
     * The session then latches onto it, so this costs one extra XOR only while the two sides are
     * out of step.
     */
    private void decryptWithEitherKey(byte[] bytes) {
        var primary = useSecretKey() ? this.encryptKey : Crypto.DISPATCH_KEY;
        Crypto.xor(bytes, primary);
        if (bytes.length >= 2 && readMagic(bytes) == 17767)
            return;

        // The session key only exists once a seed has been negotiated, so before that there is
        // nothing to fall back to and the frame really is undecryptable.
        var fallback = useSecretKey() ? Crypto.DISPATCH_KEY : this.encryptKey;
        if (fallback == null || fallback == primary)
            return;

        Crypto.xor(bytes, primary); // undo
        Crypto.xor(bytes, fallback);
        if (bytes.length >= 2 && readMagic(bytes) == 17767) {
            this.setUseSecretKey(!useSecretKey());
            Grasscutter.getLogger().info("Client {} is now talking on the {} key - following it.",
                    this.getAddress(), useSecretKey() ? "session" : "dispatch");
            return;
        }
        // Neither key works, so hand the caller the primary decode and let it report the bad magic.
        Crypto.xor(bytes, fallback);
        Crypto.xor(bytes, primary);
    }

    private static int readMagic(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    @Override
    public void handleReceive(byte[] bytes) {
        if (Grasscutter.getConfig().server.game.useXorEncryption) {
            decryptWithEitherKey(bytes);
        }
        ByteBuf packet = Unpooled.wrappedBuffer(bytes);

        try {
            boolean allDebug = GAME_INFO.logPackets == ServerDebugMode.ALL;
            int prevOpcode = -1, prevHeaderLen = -1, prevPayloadLen = -1, prevStart = -1;
            while (packet.readableBytes() > 0) {
                if (packet.readableBytes() < 12) {
                    return;
                }
                int pktStart = packet.readerIndex();
                int const1 = packet.readShort();
                if (const1 != 17767) {
                    // A frame that does not start with the magic usually means the key is wrong,
                    // not the packet: a game update can ship a new dispatch key, and then every
                    // frame decrypts to noise. With packet logging off that failure was completely
                    // silent, which reads exactly like "the client never connected" - a different
                    // problem with a different fix. So say it once per session regardless, and hand
                    // over the first bytes, which is what a key has to be recovered from.
                    if (!this.reportedBadMagic) {
                        this.reportedBadMagic = true;
                        Grasscutter.getLogger().warn(
                                "A {}-byte frame from {} did not decrypt (magic {}, expected 17767) - the {} key does not match this client. First bytes: {}",
                                bytes.length, this.getAddress(), const1,
                                useSecretKey() ? "session" : "dispatch",
                                Utils.bytesToHex(java.util.Arrays.copyOf(bytes,
                                        Math.min(bytes.length, 32))));
                    }
                    if (allDebug) {
                        int badOffset = packet.readerIndex() - 2;
                        Grasscutter.getLogger().error(
                                "Bad Data Package Received: got {} ,expect 17767 (bad magic at offset {} of {}-byte frame; prev packet opcode={} headerLen={} payloadLen={} startedAt={})",
                                const1, badOffset, bytes.length, prevOpcode, prevHeaderLen,
                                prevPayloadLen, prevStart);
                        Grasscutter.getLogger().error("RAW FRAME HEX: {}", Utils.bytesToHex(bytes));
                    }
                    return; // Bad packet
                }
                int opcode = packet.readShort();
                int headerLength = packet.readShort();
                int payloadLength = packet.readInt();
                byte[] header = new byte[headerLength];
                byte[] payload = new byte[payloadLength];

                packet.readBytes(header);
                packet.readBytes(payload);
                int const2 = packet.readShort();
                if (const2 != -30293) {
                    if (allDebug) {
                        Grasscutter.getLogger()
                                .error("Bad Data Package Received: got {} ,expect -30293", const2);
                    }
                    return; // Bad packet
                }

                prevOpcode = opcode;
                prevHeaderLen = headerLength;
                prevPayloadLen = payloadLength;
                prevStart = pktStart;

                getServer().getPacketHandler().handle(this, opcode, header, payload);
            }
        } catch (Throwable e) {
            // The rest of this datagram is lost either way, but printed to the console it never
            // reached the log, so a dropped packet left the player's action unexplained.
            Grasscutter.getLogger().error("Dropped an inbound packet from {}.",
                    this.getPlayer() != null ? this.getPlayer().getUid() : this.getAddress(), e);
        } finally {

            packet.release();
        }
    }

    @Override
    public synchronized void handleClose() {
        if (closeHandled)
            return;
        closeHandled = true;
        setState(SessionState.INACTIVE);

        Grasscutter.getLogger()
                .info(translate("messages.game.disconnect", String.valueOf(this.getAddress())));

        try {
            if (this.isLoggedIn()) {
                getPlayer().onLogout();
            }
        } finally {
            try {
                send(new BasePacket(PacketOpcodes.ServerDisconnectClientNotify));
            } catch (Throwable e) {
                Grasscutter.getLogger().warn("Disconnect notification failed for {}", getAddress(),
                        e);
            } finally {
                tunnel = null;
            }
        }
    }

    public void close() {
        // Already disconnected (a second login from the same account closes the first
        // session, which may have gone away on its own).
        var tunnel = this.tunnel;
        if (tunnel != null)
            tunnel.close();
    }

    public boolean isActive() {
        return getState() == SessionState.ACTIVE;
    }

    /**
     * Whether the connection is still open, which is a different question from {@link #isActive()}
     * - that one asks whether the player has finished logging in. Anything working the handshake,
     * in front of login, has to ask this one instead.
     */
    public boolean isConnected() {
        return this.tunnel != null;
    }

    public enum SessionState {
        INACTIVE,
        WAITING_FOR_TOKEN,
        WAITING_FOR_LOGIN,
        PICKING_CHARACTER,
        ACTIVE,
        ACCOUNT_BANNED,
        // Turned away because the server is already at its player limit.
        SERVER_MAX_PLAYER_OVERFLOW,
        // Turned away because the database save queues are backed up.
        DB_OVERLOAD
    }
}
