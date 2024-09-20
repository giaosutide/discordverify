package h4dro.me.discordverify;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class DiscordVerify extends JavaPlugin implements Listener {
    private HashMap<String, String> linkedPlayers = new HashMap<>();
    private net.dv8tion.jda.api.JDA jda;
    private File dataFile;
    private String botToken;

    @Override
    public void onEnable() {
        createDataFile();
        loadLinkedPlayers();
        loadConfig();

        try {
            JDABuilder bot = JDABuilder.createDefault(botToken);
            bot.addEventListeners(new DiscordListener());
            jda = bot.build().awaitReady();
            if (jda.getGuilds().isEmpty()) {
                getLogger().severe("The bot has not been invited to any Discord servers! Please invite bots to the server.");
                return;
            }

            registerCommands(jda.getGuilds().get(0));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
        }
        saveLinkedPlayers();
    }

    private void createDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadLinkedPlayers() {
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            linkedPlayers.put(key, dataConfig.getString(key));
        }
    }

    private void saveLinkedPlayers() {
        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        dataConfig.set("players", null);
        for (String ign : linkedPlayers.keySet()) {
            dataConfig.set("players." + ign, linkedPlayers.get(ign));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        saveDefaultConfig();
        botToken = getConfig().getString("bot.token");
    }

    private String getMessage(String path) {
        return getConfig().getString("messages." + path).replace("§", "&");
    }

    public class DiscordListener extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.getName().equals("link")) {
                String ign = event.getOption("ign").getAsString();
                String discordUserId = event.getUser().getId();

                // Check if Discord ID is already linked
                if (linkedPlayers.containsValue(discordUserId)) {
                    event.reply("❌ Your Discord account is already linked to another Minecraft account.").queue();
                    return;
                }

                // Link IGN with Discord ID
                linkedPlayers.put(ign, discordUserId);
                saveLinkedPlayers();
                event.reply(getMessage("link_success_message").replace("%s", ign)).queue(); // Add checkmark
            }
        }
    }

    public void registerCommands(Guild guild) {
        guild.updateCommands().addCommands(
                Commands.slash("link", "Link your Minecraft account to Discord")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "ign", "Tên trong Minecraft", true)
        ).queue();
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String playerName = event.getPlayer().getName();

        if (!linkedPlayers.containsKey(playerName)) {
            String kickMessage = String.join("\n",
                    getMessage("access_denied_title"),
                    getMessage("access_denied_message"),
                    getMessage("link_command_message"),
                    getMessage("discord_info_message"),
                    getMessage("discord_invite_link"),
                    getMessage("thank_you_message")
            ).replace("§", "&"); // Ensure colors are applied

            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("discordverify")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfig();
                sender.sendMessage(getMessage("reload_success_message"));
                return true;
            }
        }
        return false;
    }
}