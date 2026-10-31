# AstaPS

[English](README.md) · [简体中文](README_zh-CN.md) · 繁體中文

一個基於 Grasscutter 的《原神》**7.1.0** 私人伺服器。

> 這是一個研究與保存性質的專案，與 HoYoverse / miHoYo 沒有任何從屬、背書或關聯，亦不作商業用途。

如果你可以修復錯誤，請幫助我。

## 需求

| | |
|---|---|
| Java | **JDK 21** 用於編譯，**Java 21** 用於執行。AstaPS 會使用虛擬執行緒等 Java 21 API。 |
| MongoDB | Community Server，啟動伺服器前必須先跑起來。 |
| 遊戲客戶端 | 原神 7.1.0。官方客戶端會校驗 region 的簽名，要連私服需要另外打客戶端補丁，例如 [hk4e-patch-universal](https://github.com/MeChen618/patch)。AstaPS 本身不附帶補丁。 |
| 資源檔 | 7.1.0 的資源包，解壓到伺服器目錄下的 `resources/`。如果你沒有資源檔，可以透過[該連結](https://github.com/MeChen618/AstaPS-Resource)下載。 |

## 編譯

編譯前先確認兩個 Java 指令都指向 21：

```
java -version
javac -version
```

接著執行：

```
./gradlew jar -PskipHandbook=1
```

`grasscutter-7.1.0.jar` 會產生在專案根目錄。拿掉 `-PskipHandbook=1` 會一併編譯遊戲內手冊，那一步需要 NodeJS，沒有就會失敗。

Windows 用 `.\gradlew.bat`。

## 執行

1. 啟動 MongoDB。
2. 把 7.1.0 資源包放進 `resources/`。
3. 先跑一次 jar。它會寫出 `config.json`，缺少必要東西時會停下來。
4. 再跑一次。Dispatch 預設監聽 `8088`，遊戲伺服器 `22101`。
5. 把客戶端指向 dispatch：[GrasscutterTools](https://github.com/jie65535/GrasscutterCommandGenerator) 內建代理，也可以用 Fiddler、mitmproxy 或客戶端補丁。

## 指令

遊戲內指令以 `/` 開頭，伺服器主控台不需要。輸入 `help` 查看指令列表，`help <指令> [子指令...]` 查看語法及別名。主控台支援 Tab 補齊，參數錯誤時會顯示對應語法。

玩家選擇器支援 `@UID`、`username@` 和 `username@UID`。帳號管理僅限伺服器主控台。

```text
help teleport pos
give 202 --amount 3 @10001
```

詳細說明請見 [CLI 遷移文件](docs/cli-picocli-migration.md)；外掛開發者請參閱 [指令 API v5 文件](docs/plugin-command-api-v5.md)。

## TPS 射擊玩法（7.1）

至冬的第三人稱射擊玩法可用。指令、TPS 秘境與尚未確定的部分見 [docs/tps/README.md](docs/tps/README.md)。

## 授權

本專案採用 **GNU General Public License v3.0**，見 [`LICENSE`](LICENSE)。

`LICENSE-ClassGraph.txt` 不是本專案的授權條款。ClassGraph 是一個 MIT 授權的相依套件，它的 class 會被打包進 `grasscutter-7.1.0.jar`，而 MIT 只要求該聲明隨之一起帶著。

## 致謝

本伺服器基於 **Grasscutter**。參考專案：**LunaGC**、**HunkyMeow**。

「需求」中提到的客戶端補丁 [hk4e-patch-universal](https://github.com/MeChen618/patch) 是 [capyb2222/animegamepatch](https://github.com/capyb2222/animegamepatch) 的副本，由 **capyb2222** 維護，基於 [xeondev](https://git.xeondev.com/reversedrooms/hk4e-patch) 的原始 hk4e-patch 與 [oureveryday](https://github.com/oureveryday/) 的原始 hk4e-patch-universal。它是獨立專案，以自己的 GPL-3.0 授權發布。

本倉庫根部的匯入提交中，以姓名列出了它所承載的各位作者。

## Proto 來源

協議定義來自 [genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol)。
