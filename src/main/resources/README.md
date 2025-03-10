# JudgementDay

An advanced punishment and moderation system for Minecraft servers running Bukkit/Spigot/Paper.

## Features

- **Multiple Punishment Types**: Warnings, Mutes, Kicks, and Bans
- **Progressive Punishment System**: Automatically escalates punishments for repeat offenders
- **Punishment History**: Keep track of all punishments for each player
- **Customizable Durations**: Configure punishment durations for each offense and level
- **Player Reporting**: Allow players to report rule-breakers
- **Appeals System**: Let players appeal their punishments
- **Proof Requirements**: Require staff to provide evidence for punishments
- **Database Support**: Store data in YAML or MySQL/MariaDB
- **Discord Integration**: Send punishment notifications to Discord
- **GUI Interfaces**: User-friendly interfaces for staff

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/punish <player>` | Open punishment GUI for a player | `judgementday.punish` |
| `/warn <player> [reason]` | Warn a player | `judgementday.warn` |
| `/mute <player> [reason]` | Mute a player | `judgementday.mute` |
| `/ban <player> [reason]` | Ban a player | `judgementday.ban` |
| `/kick <player> [reason]` | Kick a player | `judgementday.kick` |
| `/revoke <id> [reason]` | Revoke a punishment | `judgementday.revoke` |
| `/history [player] [page]` | View a player's punishment history | `judgementday.history.self` / `judgementday.history.others` |
| `/report <player> <reason>` | Report a player | `judgementday.report` |
| `/reports [page]` | View and manage reports | `judgementday.reports` |
| `/appeal <id> <reason>` | Appeal a punishment | `judgementday.appeal` |
| `/appeals [page]` | View and manage appeals | `judgementday.appeals` |
| `/jdreload` | Reload plugin configuration | `judgementday.reload` |

## Permissions

See `plugin.yml` for a full list of permissions. Key permissions:

- `judgementday.admin`: Grants all JudgementDay permissions
- `judgementday.staff`: Marks a player as staff for notifications
- `judgementday.exempt`: Exempts a player from being punished
- `judgementday.exempt.report`: Exempts a player from being reported
- `judgementday.reason.custom`: Allows using custom punishment reasons

## Configuration

- `config.yml`: Main plugin configuration
- `messages.yml`: All plugin messages
- `punishments.yml`: Punishment types, reasons, and durations

## Database Setup

By default, JudgementDay uses YAML files to store data. To use MySQL/MariaDB:

1. Set `storage.type` to `mysql` in `config.yml`
2. Configure database connection details in `storage.mysql` section
3. Restart the server

## Discord Integration

To enable Discord integration:

1. Set `discord.enabled` to `true` in `config.yml`
2. Set `discord.webhook-url` to your Discord webhook URL
3. Configure which events to send in `discord.notifications` section
4. Customize embed colors in `discord.colors` section

## For Developers

JudgementDay provides an API for other plugins to interact with its systems. Example:

```java
// Get the JudgementDay plugin instance
JudgementDay judgementDay = (JudgementDay) Bukkit.getPluginManager().getPlugin("JudgementDay");

// Get a player's active punishments
UUID playerUuid = player.getUniqueId();
judgementDay.getPlayerManager().getActivePlayerPunishments(playerUuid)
    .thenAccept(punishments -> {
        // Do something with the punishments
        for (Punishment punishment : punishments) {
        System.out.println(punishment.getType() + " - " + punishment.getReason());
        }
        });
```

## Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin to your liking

## Issues and Contributions

Please report bugs and suggest features using the GitHub issue tracker. Pull requests are welcome!

## License

This project is licensed under the MIT License - see the LICENSE file for details.