package emu.grasscutter.server.http.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionVersionConfigLoaderTest {
    @TempDir Path tempDir;

    @Test
    void loadsPascalCaseRegionInfoFromVersionDirectory() throws Exception {
        Path versionDirectory = Files.createDirectories(tempDir.resolve("7.0.0"));
        Files.writeString(
                versionDirectory.resolve("OSRELWin7.0.0.json"),
                """
                {
                  "RegionInfo": {
                    "GateserverIp": "211.154.22.140",
                    "GateserverPort": 22101,
                    "ClientDataVersion": 47985349,
                    "ResVersionConfig": {
                      "Version": 47805902,
                      "VersionSuffix": "d533609003",
                      "Branch": "7.0_live"
                    },
                    "GameBiz": "hk4e_global"
                  },
                  "Retcode": 0
                }
                """);

        var loaded = RegionVersionConfigLoader.load(tempDir, "OSRELWin7.0.0");

        assertTrue(loaded.isPresent());
        assertEquals("211.154.22.140", loaded.get().regionInfo().getGateserverIp());
        assertEquals(47985349, loaded.get().regionInfo().getClientDataVersion());
        assertEquals(47805902, loaded.get().regionInfo().getResVersionConfig().getVersion());
        assertEquals("d533609003", loaded.get().regionInfo().getResVersionConfig().getVersionSuffix());
    }

    @Test
    void loadsSnakeCaseRegionInfoFromFlatVersionDirectory() throws Exception {
        Files.writeString(
                tempDir.resolve("OSRELiOS7.0.0.json"),
                """
                {
                  "region_info": {
                    "gateserver_ip": "47.253.130.114",
                    "gateserver_port": 22102,
                    "client_data_version": 47985349,
                    "res_version_config": {
                      "version": 47805902,
                      "version_suffix": "d533609003",
                      "branch": "7.0_live"
                    },
                    "game_biz": "hk4e_global"
                  }
                }
                """);

        var loaded = RegionVersionConfigLoader.load(tempDir, "OSRELiOS7.0.0");

        assertTrue(loaded.isPresent());
        assertEquals("47.253.130.114", loaded.get().regionInfo().getGateserverIp());
        assertEquals(22102, loaded.get().regionInfo().getGateserverPort());
        assertEquals("hk4e_global", loaded.get().regionInfo().getGameBiz());
    }
}
