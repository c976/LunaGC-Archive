package emu.grasscutter.server.http.dispatch;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.ResVersionConfigOuterClass.ResVersionConfig;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads client-specific region hotfix files from the configured data directory. */
final class RegionVersionConfigLoader {
    private static final Pattern CLIENT_VERSION_PATTERN =
            Pattern.compile("^([A-Za-z]+)(\\d+\\.\\d+\\.\\d+)$");

    private RegionVersionConfigLoader() {}

    /**
     * Finds and converts the region file for a client version.
     *
     * <p>Both the normal Grasscutter layout ({@code data/version/7.0.0/<client>.json}) and a
     * flat {@code data/version/<client>.json} layout are accepted. A malformed or unknown version
     * is treated as a miss so that the route can keep its existing fallback response.
     */
    static Optional<LoadedRegion> load(String versionName) {
        // The data folder wins, then the copy shipped in the jar. Checking only getDataPath would
        // stop at the data folder whenever it has a version directory at all, so a hotfix shipped
        // in the jar for a newer client would never be read.
        return load(FileUtils.getDataPath("version"), versionName)
                .or(() -> load(FileUtils.getBundledDataPath("version"), versionName));
    }

    /** Package-private overload used by tests without depending on the global server config. */
    static Optional<LoadedRegion> load(Path versionRoot, String versionName) {
        if (versionRoot == null || versionName == null) {
            return Optional.empty();
        }

        String normalizedName = versionName.trim();
        Matcher matcher = CLIENT_VERSION_PATTERN.matcher(normalizedName);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String semanticVersion = matcher.group(2);
        String expectedFileName = normalizedName + ".json";
        Path normalizedRoot = versionRoot.toAbsolutePath().normalize();

        Path file = findFile(normalizedRoot.resolve(semanticVersion), expectedFileName);
        if (file == null) {
            file = findFile(normalizedRoot, expectedFileName);
        }
        if (file == null) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(file);
            HotfixDocument document = JsonUtils.decode(json, HotfixDocument.class);
            RegionInfoDocument regionDocument =
                    document == null ? null : document.regionInfo;

            // Some captures contain RegionInfo directly instead of the usual wrapper.
            if (regionDocument == null) {
                regionDocument = JsonUtils.decode(json, RegionInfoDocument.class);
            }
            if (regionDocument == null) {
                return Optional.empty();
            }

            RegionInfo regionInfo = regionDocument.toProto();
            if (regionInfo.equals(RegionInfo.getDefaultInstance())) {
                return Optional.empty();
            }
            return Optional.of(new LoadedRegion(file, regionInfo));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Path findFile(Path directory, String expectedFileName) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }

        Path exact = directory.resolve(expectedFileName).normalize();
        if (exact.startsWith(directory) && Files.isRegularFile(exact)) {
            return exact;
        }

        // Windows deployments occasionally differ only in filename casing.
        try (var files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .equalsIgnoreCase(expectedFileName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    record LoadedRegion(Path source, RegionInfo regionInfo) {}

    private static final class HotfixDocument {
        @SerializedName(value = "RegionInfo", alternate = {"region_info", "regionInfo"})
        private RegionInfoDocument regionInfo;
    }

    private static final class RegionInfoDocument {
        @SerializedName(value = "gateserver_ip", alternate = {"GateserverIp", "gateserverIp"})
        private String gateserverIp;

        @SerializedName(value = "gateserver_port", alternate = {"GateserverPort", "gateserverPort"})
        private Integer gateserverPort;

        @SerializedName(value = "pay_callback_url", alternate = {"PayCallbackUrl", "payCallbackUrl"})
        private String payCallbackUrl;

        @SerializedName(value = "area_type", alternate = {"AreaType", "areaType"})
        private String areaType;

        @SerializedName(value = "resource_url", alternate = {"ResourceUrl", "resourceUrl"})
        private String resourceUrl;

        @SerializedName(value = "data_url", alternate = {"DataUrl", "dataUrl"})
        private String dataUrl;

        @SerializedName(value = "feedback_url", alternate = {"FeedbackUrl", "feedbackUrl"})
        private String feedbackUrl;

        @SerializedName(value = "bulletin_url", alternate = {"BulletinUrl", "bulletinUrl"})
        private String bulletinUrl;

        @SerializedName(
                value = "resource_url_bak",
                alternate = {"ResourceUrlBak", "ResourceUrlBackup", "resourceUrlBak"})
        private String resourceUrlBak;

        @SerializedName(
                value = "data_url_bak",
                alternate = {"DataUrlBak", "DataUrlBackup", "dataUrlBak"})
        private String dataUrlBak;

        @SerializedName(
                value = "client_data_version",
                alternate = {"ClientDataVersion", "clientDataVersion"})
        private Integer clientDataVersion;

        @SerializedName(value = "handbook_url", alternate = {"HandbookUrl", "handbookUrl"})
        private String handbookUrl;

        @SerializedName(
                value = "client_silence_data_version",
                alternate = {"ClientSilenceDataVersion", "clientSilenceDataVersion"})
        private Integer clientSilenceDataVersion;

        @SerializedName(
                value = "client_data_md5",
                alternate = {"ClientDataMd5", "clientDataMd5"})
        private String clientDataMd5;

        @SerializedName(
                value = "client_silence_data_md5",
                alternate = {"ClientSilenceDataMd5", "clientSilenceDataMd5"})
        private String clientSilenceDataMd5;

        @SerializedName(
                value = "res_version_config",
                alternate = {"ResVersionConfig", "resVersionConfig"})
        private ResVersionConfigDocument resVersionConfig;

        @SerializedName(value = "secret_key", alternate = {"SecretKey", "secretKey"})
        private byte[] secretKey;

        @SerializedName(
                value = "official_community_url",
                alternate = {"OfficialCommunityUrl", "officialCommunityUrl"})
        private String officialCommunityUrl;

        @SerializedName(
                value = "client_version_suffix",
                alternate = {"ClientVersionSuffix", "clientVersionSuffix"})
        private String clientVersionSuffix;

        @SerializedName(
                value = "client_silence_version_suffix",
                alternate = {"ClientSilenceVersionSuffix", "clientSilenceVersionSuffix"})
        private String clientSilenceVersionSuffix;

        @SerializedName(
                value = "use_gateserver_domain_name",
                alternate = {"UseGateserverDomainName", "useGateserverDomainName"})
        private Boolean useGateserverDomainName;

        @SerializedName(
                value = "gateserver_domain_name",
                alternate = {"GateserverDomainName", "gateserverDomainName"})
        private String gateserverDomainName;

        @SerializedName(value = "user_center_url", alternate = {"UserCenterUrl", "userCenterUrl"})
        private String userCenterUrl;

        @SerializedName(
                value = "account_bind_url",
                alternate = {"AccountBindUrl", "accountBindUrl"})
        private String accountBindUrl;

        @SerializedName(value = "cdkey_url", alternate = {"CdkeyUrl", "cdkeyUrl"})
        private String cdkeyUrl;

        @SerializedName(
                value = "privacy_policy_url",
                alternate = {"PrivacyPolicyUrl", "privacyPolicyUrl"})
        private String privacyPolicyUrl;

        @SerializedName(
                value = "next_resource_url",
                alternate = {"NextResourceUrl", "nextResourceUrl"})
        private String nextResourceUrl;

        @SerializedName(
                value = "next_res_version_config",
                alternate = {"NextResVersionConfig", "nextResVersionConfig"})
        private ResVersionConfigDocument nextResVersionConfig;

        @SerializedName(value = "game_biz", alternate = {"GameBiz", "gameBiz"})
        private String gameBiz;

        @SerializedName(
                value = "gateserver_ipv6_ip",
                alternate = {"GateserverIpv6Ip", "gateserverIpv6Ip"})
        private String gateserverIpv6Ip;

        @SerializedName(value = "LMIPNFIMJNA")
        private String lmipnfimjna;

        @SerializedName(value = "PEPKNNPODEB")
        private String pepknnpodeb;

        @SerializedName(value = "GPLMEKCGBIL")
        private String gplmekcgbil;

        @SerializedName(value = "BHHDFKBGHIL")
        private String bhhdfkbghil;

        @SerializedName(value = "KNPIODMJIID")
        private String knpiodmjiid;

        @SerializedName(value = "GEFKKPHEPJE")
        private String gefkkphepje;

        @SerializedName(value = "NKEJHLNPODC")
        private String nkejhlnpodc;

        @SerializedName(value = "GEHCCAFMAML")
        private String gehccafmaml;

        @SerializedName(value = "KJNKNEHJMDA")
        private String kjnknehjmda;

        @SerializedName(value = "IJJPBBCJFKN")
        private String ijjpbbcjfkn;

        private RegionInfo toProto() {
            RegionInfo.Builder builder = RegionInfo.newBuilder();
            setString(gateserverIp, builder::setGateserverIp);
            if (gateserverPort != null) builder.setGateserverPort(gateserverPort);
            setString(payCallbackUrl, builder::setPayCallbackUrl);
            setString(areaType, builder::setAreaType);
            setString(resourceUrl, builder::setResourceUrl);
            setString(dataUrl, builder::setDataUrl);
            setString(feedbackUrl, builder::setFeedbackUrl);
            setString(bulletinUrl, builder::setBulletinUrl);
            setString(resourceUrlBak, builder::setResourceUrlBak);
            setString(dataUrlBak, builder::setDataUrlBak);
            if (clientDataVersion != null) builder.setClientDataVersion(clientDataVersion);
            setString(handbookUrl, builder::setHandbookUrl);
            if (clientSilenceDataVersion != null) {
                builder.setClientSilenceDataVersion(clientSilenceDataVersion);
            }
            setString(clientDataMd5, builder::setClientDataMd5);
            setString(clientSilenceDataMd5, builder::setClientSilenceDataMd5);
            if (resVersionConfig != null) {
                builder.setResVersionConfig(resVersionConfig.toProto());
            }
            if (secretKey != null) builder.setSecretKey(com.google.protobuf.ByteString.copyFrom(secretKey));
            setString(officialCommunityUrl, builder::setOfficialCommunityUrl);
            setString(clientVersionSuffix, builder::setClientVersionSuffix);
            setString(clientSilenceVersionSuffix, builder::setClientSilenceVersionSuffix);
            if (useGateserverDomainName != null) {
                builder.setUseGateserverDomainName(useGateserverDomainName);
            }
            setString(gateserverDomainName, builder::setGateserverDomainName);
            setString(userCenterUrl, builder::setUserCenterUrl);
            setString(accountBindUrl, builder::setAccountBindUrl);
            setString(cdkeyUrl, builder::setCdkeyUrl);
            setString(privacyPolicyUrl, builder::setPrivacyPolicyUrl);
            setString(nextResourceUrl, builder::setNextResourceUrl);
            if (nextResVersionConfig != null) {
                builder.setNextResVersionConfig(nextResVersionConfig.toProto());
            }
            setString(gameBiz, builder::setGameBiz);
            // gateserver_ipv6_ip is field 37 in 7.1 as in 7.0, still unnamed there.
            setString(gateserverIpv6Ip, builder::setPNMHGJPJPPF);
            setString(lmipnfimjna, builder::setLMIPNFIMJNA);
            setString(pepknnpodeb, builder::setPEPKNNPODEB);
            setString(gplmekcgbil, builder::setGPLMEKCGBIL);
            setString(bhhdfkbghil, builder::setBHHDFKBGHIL);
            setString(knpiodmjiid, builder::setKNPIODMJIID);
            setString(gefkkphepje, builder::setGEFKKPHEPJE);
            setString(nkejhlnpodc, builder::setNKEJHLNPODC);
            setString(gehccafmaml, builder::setGEHCCAFMAML);
            setString(kjnknehjmda, builder::setKJNKNEHJMDA);
            setString(ijjpbbcjfkn, builder::setIJJPBBCJFKN);
            return builder.build();
        }

        private static void setString(String value, java.util.function.Consumer<String> setter) {
            if (value != null) setter.accept(value);
        }
    }

    private static final class ResVersionConfigDocument {
        @SerializedName(value = "version", alternate = {"Version"})
        private Integer version;

        @SerializedName(value = "relogin", alternate = {"Relogin"})
        private Boolean relogin;

        @SerializedName(value = "md5", alternate = {"Md5"})
        private String md5;

        @SerializedName(value = "release_total_size", alternate = {"ReleaseTotalSize"})
        private String releaseTotalSize;

        @SerializedName(value = "version_suffix", alternate = {"VersionSuffix"})
        private String versionSuffix;

        @SerializedName(value = "branch", alternate = {"Branch"})
        private String branch;

        @SerializedName(value = "next_script_version", alternate = {"NextScriptVersion"})
        private String nextScriptVersion;

        private ResVersionConfig toProto() {
            ResVersionConfig.Builder builder = ResVersionConfig.newBuilder();
            if (version != null) builder.setVersion(version);
            if (relogin != null) builder.setRelogin(relogin);
            if (md5 != null) builder.setMd5(md5);
            if (releaseTotalSize != null) builder.setReleaseTotalSize(releaseTotalSize);
            if (versionSuffix != null) builder.setVersionSuffix(versionSuffix);
            if (branch != null) builder.setBranch(branch);
            if (nextScriptVersion != null) builder.setNextScriptVersion(nextScriptVersion);
            return builder.build();
        }
    }
}
