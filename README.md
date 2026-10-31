# AstaPS

English · [简体中文](README_zh-CN.md) · [繁體中文](README_zh-TW.md)

A private server for Genshin Impact **7.1.0**, built on Grasscutter.

> This is a research and preservation project. It is not affiliated with, endorsed by, or connected to HoYoverse / miHoYo in any way, and it is not for commercial use.

If you can fix a bug, please help me.

## Requirements

|             |                                                                                                                                                                                                                                   |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java        | **JDK 21** to build and **Java 21** to run. AstaPS uses Java 21 APIs such as virtual threads.                                                                                                                                     |
| MongoDB     | Community Server. Must be running before the server starts.                                                                                                                                                                       |
| Game client | Genshin Impact 7.1.0. The official client checks the region's signature, so it needs a client patch to connect to a private server, such as [hk4e-patch-universal](https://github.com/KitsukiDev/hk4e-patch-universal). AstaPS does not ship one. |
| Resources   | A 7.1.0 resource pack, extracted to `resources/` in the server directory. If you don't have Resources, you can download it [here](https://github.com/MeChen618/AstaPS-Resource).                                                  |

## Docker

To use a local development environment instead, see [Building](#building) and [Running](#running).

Docker Compose provides the build tools, Java runtime, and MongoDB; Java, MongoDB, and Node.js do not need to be installed on the host. These steps use Docker Compose v2 and work on Windows, macOS, and Linux. Refer to the [Docker documentation](https://docs.docker.com/get-started/) for installation instructions.

> [!NOTE]
> Node.js is only needed to build the in-game handbook. The handbook is not required to run the server. The handbook build process is also currently broken.

Create the Compose environment file:

```sh
cp .env.example .env
```

Edit `.env` to choose the Gradle/JDK, Node.js, Java runtime, and MongoDB image tags, or to change published ports. Compose reads `.env` automatically.

On Linux, the containers run as `ASTAPS_UID`:`ASTAPS_GID` (default `1000:1000`) and write to bind-mounted host directories. Set both to your own IDs (`id -u` and `id -g`) and create the directories before the first start, otherwise Docker creates them as root and the server cannot write its config or logs:

```sh
mkdir -p docker_volumes/astaps-run docker_volumes/astaps-dev/gradle-cache docker_volumes/astaps-dev/handbook-node-modules
```

Docker Desktop on Windows and macOS works with the defaults.

MongoDB is published on `127.0.0.1` only: port `27017` for the run deployment and `27018` for the dev one (`MONGO_RUN_PORT`, `MONGO_DEV_PORT`). Containers reach it by service name, so these ports are only for database tools on the host.

### Development

Open a development shell with the checkout bind-mounted. The dev profile starts a separate MongoDB service and keeps its database storage separate from the run deployment. MongoDB files, Gradle's user-home cache, and generated handbook dependencies live under `docker_volumes/astaps-dev/`.

```sh
docker compose --profile dev run --service-ports --rm astaps-dev
```

Inside the container, run Gradle as mentioned in [Building](#building). The checkout is mounted at `/workspace`, and the working directory is set there.

Gradle's user-home cache persists under `docker_volumes/astaps-dev/gradle-cache`, while project outputs remain in the bind-mounted checkout. The “incompatible/stopped Daemons could not be reused” warning is harmless if Gradle recovers. Keep `config.json` under the checkout root, not in the container filesystem, when specifying local development paths.

When running from this shell, set both `databaseInfo.server.connectionUri` and `databaseInfo.game.connectionUri` in the project-root `config.json` to `mongodb://mongodb-dev:27017`. The development profile uses a network alias matching the service name, so the host ports remain available for the run deployment.

If Gradle fails in `generateBuildMetadata` because `git rev-parse` exits with code 128, rebuild the development image and start a fresh shell. The container trusts only the mounted checkout at `/workspace`; rebuild ensures the Git metadata is present there.

```sh
docker compose --profile dev build
docker compose --profile dev run --service-ports --rm astaps-dev
```

### Compile and run

Build the server image (compiling the current checkout) and start it with its own MongoDB:

```sh
docker compose up --build
```

The server's working files, including `config.json`, are stored in `docker_volumes/astaps-run/`. The `resources/` and writable `data/` directories are mounted from the checkout. Protocol definitions and generated files remain in the repository.

To start the run deployment without rebuilding, use:

```sh
docker compose up -d
```

To compile without starting services, use `docker compose build astaps-run`; later, `docker compose up -d` starts the compiled image. HTTP is published on port `8088` and the game server on both the host and the Docker network; the exposed ports are defined in `docker-compose.yml`.

The server image runs Java as its foreground process. With `docker compose up`, the command prompt is interactive; for a detached container, run `docker compose attach astaps-run` to enter command mode.

## Building

Check that both Java commands resolve to version 21 before building:

```
java -version
javac -version
```

Then build with:

```
./gradlew jar -PskipHandbook=1
```

`grasscutter-7.1.0.jar` lands in the project root. Drop `-PskipHandbook=1` to build the in-game handbook as well; that step needs NodeJS and fails without it.

On Windows use `./gradlew.bat`.

## Running

1. Start MongoDB.
2. Put a 7.1.0 resource pack in `resources/`.
3. Run the jar once. It writes a `config.json` and stops if anything essential is missing.
4. Start it again. The dispatch server listens on `8088` and the game server on `22101` by default.
5. Point the client at the dispatch server: [GrasscutterTools](https://github.com/jie65535/GrasscutterCommandGenerator) has a built-in proxy, or use Fiddler, mitmproxy or a client patch.

## Commands

In-game commands use `/`; the server console omits it. Use `help` to list commands, or `help <command> [subcommand...]` for syntax and aliases. The console supports Tab completion; invalid arguments show the matching syntax.

Player selectors: `@UID`, `username@`, or `username@UID`. Account management is console-only.

```text
help teleport pos
give 202 --amount 3 @10001
```

See the [CLI migration guide](docs/cli-picocli-migration.md) for details and the [command API v5 guide](docs/plugin-command-api-v5.md) for plugins.

## TPS shooting (7.1)

Snezhnaya's third-person shooter mode works. See [docs/tps/README.md](docs/tps/README.md) for the commands, the TPS domains, and what is not settled yet.

## Licence

Released under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE).

`LICENSE-ClassGraph.txt` is not this project's licence. ClassGraph is an MIT-licensed dependency whose compiled classes ship inside `grasscutter-7.1.0.jar`, and MIT asks only that its notice remain with the distribution.

## Credits

This server is based on **Grasscutter**. Reference projects: **LunaGC**, **HunkyMeow**.

The client patch linked under Requirements, [hk4e-patch-universal](https://github.com/KitsukiDev/hk4e-patch-universal), is a copy of [capyb2222/animegamepatch](https://github.com/capyb2222/animegamepatch), maintained by **capyb2222** and based on the original hk4e-patch by [xeondev](https://git.xeondev.com/reversedrooms/hk4e-patch) and the original hk4e-patch-universal by [oureveryday](https://github.com/oureveryday/).

The import commit at the root of this repository credits by name the authors whose work it carries.

## Proto Sources

Protocol definitions sourced from [genshin-protocol](https://gitlab.com/kitkat-multiverse/genshin-protocol).
