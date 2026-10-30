# hk4e-patch-universal

A version-agnostic client patch for Genshin Impact, built for use with private game servers.

The original [hk4e-patch](https://git.xeondev.com/reversedrooms/hk4e-patch) hard-coded memory offsets for one specific game version per release. This fork instead locates all of its patch points at runtime by scanning byte patterns, so a single build keeps working across game updates. The current development target is Genshin Impact 7.1 and later; older versions (this fork descends from a 6.5-era codebase) may work but are untested.

## How it works

The project builds to a single Rust cdylib, `ext.dll`, that runs inside the game process, loaded in place of the game's own `Astrolabe.dll` (see Build for deployment).

On `DLL_PROCESS_ATTACH` the patch verifies it is running inside `GenshinImpact.exe` / `YuanShen.exe`, allocates a debug console for logging, then brings up the modules described below.

Two pieces of plumbing make the rest possible:

- **Byte-pattern scanning** — patch points are found by scanning the `.text` PE section (native code) or the `il2cpp` section (compiled Unity C# code) for byte signatures with wildcards. Signatures are chosen around stable instruction sequences, which is what makes the patch survive game updates without per-version offsets.
- **`Marshal.PtrToStringAnsi` bridge** — the game is an IL2CPP (managed C#) application. To hand it new strings from Rust — replacement URLs, replacement keys — the patch pattern-scans and calls IL2CPP's own `Marshal.PtrToStringAnsi`, so the strings are allocated on the managed heap and owned by the game.

Before any module runs, the patch restores the original `NtProtectVirtualMemory` pointer that VMProtect has hooked to prevent changing the page protection of executable segments (the original address is recovered from address bits leaked by a neighbouring ntdll routine; a Wine variant is handled too). Without this, its own inline hooks could not be applied. Hooks are installed with [ilhook](https://crates.io/crates/ilhook): `JmpBack` hooks to observe and adjust, `Retn` hooks to fully replace a function.

### Modules

**Security** — makes the client trust a private server's cryptography:

- hooks `MhyRsaPerformCryptoAction` and swaps the server RSA public key in memory for the bundled `server_public_key.bin` whenever the game processes its 268-byte RSA key blob;
- hooks the return of the key signature check and forces it to succeed;
- hooks `SdkUtilRsaEncrypt` (IL2CPP) and swaps the RSA key parameter for the bundled `sdk_public_key.xml`, so the miHoYo SDK login flow encrypts to the private server's key as well.

**Http** — redirects the game's web traffic:

- hooks `WebRequestUtils.MakeInitialUrl` (IL2CPP) and rewrites every URL to the local dispatch/proxy at `http://127.0.0.1:8088`, keeping the request path. `/query_cur_region` is deliberately left untouched (its address is supplied by the dispatch response);
- hooks `Browser::LoadURL` (IL2CPP) with the same rewrite for the in-game WebView.

**WinHttp** — catches what Http can't. The game's main traffic runs over raw sockets, but the native login SDK (`HYPass` / ma-passport) issues its requests through WinHTTP directly:

- hooks `WinHttpConnect` and retargets any connection to a `hoyoverse.com` / `mihoyo.com` / `yuanshen.com` / `hoyolab.com` host (or subdomain) to `127.0.0.1:8088`, tracking the redirected connection handles;
- hooks `WinHttpOpenRequest` and strips `WINHTTP_FLAG_SECURE` from requests on those handles, so they are sent as plain HTTP to the local proxy (which does not terminate TLS);
- hooks `WinHttpCloseHandle` to garbage-collect the tracked handles.

**CcpBlocker** — hooks `ws2_32!getaddrinfo` and resolves the official global dispatch endpoints (`dispatchosglobal.yuanshen.com`, `dispatchcnglobal.yuanshen.com`) to `0.0.0.0`, so the client cannot fall back to official dispatch. This module is enabled first, before every other one.

**Misc** — hooks Unity's `SetCustomPropertyFloat` and no-ops it (used by the game's dither transition effect).

## Configuration

The redirect target (`127.0.0.1:8088`) and the redirected domains are hard-coded constants in `src/modules/http.rs` and `src/modules/winhttp.rs`. The replacement RSA public keys live in `sdk_public_key.xml` and `server_public_key.bin` and must match the keys your server actually uses.

## Build

```
cargo build --release
```

produces `target/release/ext.dll`. To deploy it, copy the file next to `GenshinImpact.exe` and rename it to `Astrolabe.dll`, replacing the original (keep a backup) — the game then loads the patch at startup.

## Credits

This project is a continuation of several generations of the same patch:

- [xeondev / reversedrooms](https://git.xeondev.com/reversedrooms/hk4e-patch) — original hk4e-patch (version-specific)
- [oureveryday](https://github.com/oureveryday) — original "universal" rewrite (repo no longer available)
- [slxyyds](https://github.com/slxyyds/hk4e-patch-universal) — update for 6.5+, base of this fork
- [KitsukiDev](https://github.com/KitsukiDev) — WinHttp module, current maintenance

## License

GPL-3.0 — see [LICENSE](LICENSE). Derived from hk4e-patch, © 2024 reversedrooms.

This project is not affiliated with or endorsed by miHoYo / HoYoverse. Intended for private-server development and research.
