# LunaGC-5.4.0


## This is possibly the only public PS with Mob spawns in Fontaine and Natlan
## A better, more functional version of the holy Grasscutter
Old Discord for LunaGC https://discord.gg/7D5gkyJR5Y (don't ask for support there, instead create an issue in this repository)

Contribute if you want/can...


# Read the [handbook](handbook.md)!

# Setup Guide
- Read it below, its just enough to get the server up and running along with the client.

## Main Requirements

- Get [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- Get [MongoDB Community Server](https://www.mongodb.com/try/download/community)
- Get [NodeJS](https://nodejs.org/dist/v20.15.0/node-v20.15.0-x64.msi)
- Get game version REL5.4.0 (If you don't have a 5.4.0 client, you can find it here along with the audio files and hdiff if needed) :

| Download link | Package size | Decompressed package size | MD5 checksum |
| --- | --- | --- | --- |
| [GenshinImpact_5.4.0.zip.001](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.001) | 10.0 GB | 20.0 GB | d7ea7d49334e03e590db3f047cd9ea88 |
| [GenshinImpact_5.4.0.zip.002](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.002) | 10.0 GB | 20.0 GB | b4178034c1d09e889e43fd76b3fb4d3c |
| [GenshinImpact_5.4.0.zip.003](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.003) | 10.0 GB | 20.0 GB | 43b70975fcb957abaaaf7d940969679a |
| [GenshinImpact_5.4.0.zip.004](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.004) | 10.0 GB | 20.0 GB | d734b1edeb1b2b0d47d4d4bab7af6778 |
| [GenshinImpact_5.4.0.zip.005](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.005) | 10.0 GB | 20.0 GB | 95abe987ff924c21f3e5085492448760 |
| [GenshinImpact_5.4.0.zip.006](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.006) | 10.0 GB | 20.0 GB | 492510ae74ae8ac696ee59b4e831d039 |
| [GenshinImpact_5.4.0.zip.007](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.007) | 10.0 GB | 20.0 GB | 0c68334b33ee878c5beac321339b9447 |
| [GenshinImpact_5.4.0.zip.008](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/GenshinImpact_5.4.0.zip.008) | 0.97 GB | 1.9 GB | 18d44596a5f1467682f5e038c80bd92a |
| [Audio_Chinese_5.4.0.zip](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/Audio_Chinese_5.4.0.zip) | 14.11 GB | 28.40 GB | 2727087a20d630d35efe804ae683e72e |
| [Audio_English(US)_5.4.0.zip](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/Audio_English(US)_5.4.0.zip) | 16.24 GB | 32.49 GB | 76f338d1925ff39cbf73f0418e9ae354 |
| [Audio_Korean_5.4.0.zip](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/Audio_Korean_5.4.0.zip) | 13.99 GB | 28.0 GB | 6356a494c7cce397bdbb1213aa6e7298 |
| [Audio_Japanese_5.4.0.zip](https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250125201352_EiPmYLKVptWspsHf/Audio_Japanese_5.4.0.zip) | 18.43 GB | 36.88 GB | f2b1c1f217dafbcdf27aeece987256b7 |


- Make sure to install java and set the environment variables.
- Build the server (refer to "Compile the actual server" in this guide.)
- Put [Astrolabe.dll](https://github.com/pmagixc/LunaGC_5.4.0/raw/main/patch/Astrolabe.dll) in the local game root directory
- Download the [Resources](https://github.com/pmagixc/5.4-res), make a new folder called `resources` in the downloaded LunaGC folder and then extract the resources in that new folder.
- Set useEncryption, Questing and useInRouting to false (it should be false by default, if not then change it)
- Start the server and the game, make sure to also create an account in the LunaGC console!
- Have fun

### Getting started

- Clone the repository (install [Git](https://git-scm.com) first )
  
  ```
  git clone https://github.com/pmagixc/LunaGC_5.4.0.git
  ```
  
- Now you can continue with the steps below.
  

### Compile the actual Server

**Requirements**:

[Java Development Kit 17 | JDK](https://oracle.com/java/technologies/javase/jdk17-archive-downloads.html) or higher

- **Sidenote**: Handbook generation may fail on some systems. To disable handbook generation, append `-PskipHandbook=1` to the `gradlew jar` command.
  
- **For Windows**:
  
  ```shell
  .\gradlew.bat
  .\gradlew.bat jar
  ```
  
- **For Linux**:
  
  ```bash
  chmod +x gradlew
  ./gradlew
  ./gradlew jar
  ```

### You can find the output JAR in the project root folder.

### Manually compile the handbook

```shell
./gradlew generateHandbook
```

## Troubleshooting

- Make sure to set useEncryption and useInRouting both to false otherwise you might encounter errors.
- To use windy make sure that you put your luac files in C:\Windy (make the folder if it doesnt exist)
- If you get an error related to MongoDB connection timeout, check if the mongodb service is running. On windows: Press windows key and r then type `services.msc`, look for mongodb server and if it's not started then start it by right clicking on it and start. On linux, you can do `systemctl status mongod` to see if it's running, if it isn't then type `systemctl start mongod`. However, if you get error 14 on linux change the owner of the mongodb folder and the .sock file (`sudo chown -R mongodb:mongodb /var/lib/mongodb` and `sudo chown mongodb:mongodb /tmp/mongodb-27017.sock` then try to start the service again.)


# Did you skip over the text way above looking for the handbook?


## What works

- find out


## What doesn't work

- find out

### fuck yuuki, fuck pedo-luna, fuck snooshen, fuck the jewish transgender person - manu

## Credit

patch Repository [hk4e-patch-universal](https://github.com/oureveryday/hk4e-patch-universal)
