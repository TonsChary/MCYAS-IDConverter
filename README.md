# MCYAS-IDConverter v26.1
<p align="center">
  <img src="https://img.shields.io/badge/version-26.1-blue" alt="App version 26.1">
  <img src="https://img.shields.io/badge/minecraft-26.2-green" alt="Minecraft version 26.2">
  <img src="https://img.shields.io/badge/java-17-red" alt="Java version 17">
</p>

Ever started playing on one of your Minecraft servers and a few hours later that one friend that didn't buy the game wants to join?
Now you have to start a lengthy process of turning your server to offline mode and making sure everyone has their inventory and achievements after the switch... 

This happened to me way to often, and now I automated the whole process.
The Java applicatin will convert all player related files to be offline or online compatible.

# Supporting

| Server Type | Min. MC Version | Max. MC Version |
|:------------|:----------------|:----------------|
| Vanilla     | 1.7.6           | 26.2            |
| Bukkit      | 1.7.6           | 26.2            |
| Forge       | 1.7.10          | 26.2            |
| Fabric      | 1.14            | 26.2            |
| Paper       | 1.7.10          | 26.2            |
| Spigot      | 1.8             | 26.2            |

Mojang introduced UUIDs in Minecraft 1.7.6 (2014). Converting between pre 1.7.6 and post 1.7.6 is currently not being
pursued and will not be any time soon.  
If you find any bugs or edge cases, please report them to this repo!

# Usage

First look at [Disclaimer](#disclaimer) and make sure you understand the "risks" of using this tool.

- Download the [most current jar](https://github.com/TonsChary/MCYAS-IDConverter/releases/latest)
- Place it in your server's main folder (not mandatory, just makes things easier)
- Execute the jar through your terminal with the following command:

```bash
java -jar MCYAS-IDConverter.jar <arguments>
```

- `-offline` to convert your server to offline files
- `-online` to convert your server to online files
- `-uuidMap "path/to/mapping.json"` the mapping file exported by your auth server; **required** for `-offline` and `-online`
- If necessary `-p "path/to/server/folder/"` (if the jar is not in the server's main folder)
- `-copy` to copy player data from one world to another
- `-properties` to directly edit values in server.properties
- `-verbose` for verbose console output (for debugging and error reporting)
- `-v` print MCYAS-IDConverter version
- `-h` for help

## Known Issues

- Paper servers (when converted to offline), sometimes create `<Online UUID>.dat.offline-read`) files
- No confirmed support for Sponge servers
- Entity relations tied to players (e.g. pet ownership) is not
  transferred. [Here is a workaround](https://www.reddit.com/r/Minecraft/comments/9bmthx/change_pet_ownership_with_command_using_uuid_in/)
  that works for now

## Building

Executing the command
```bash
./gradlew clean shadowJar
```

builds a runnable JAR with all dependencies.

Building requires a JDK 26 toolchain, but the produced JAR targets Java 17, so it runs on the JDK shipped with Minecraft 1.18+ servers.

# Disclaimer

Please always make a backup of your game files before using this tool.
Whilst it was thoroughly tested on my own servers, there is always the chance that a bug might occur!

# License

This project is licensed under the [MIT License](LICENSE).

# Remark

Minecraft is a registered trademark of Mojang AB.
