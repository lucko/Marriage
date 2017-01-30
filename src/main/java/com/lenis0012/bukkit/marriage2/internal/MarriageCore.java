package com.lenis0012.bukkit.marriage2.internal;

import com.lenis0012.bukkit.marriage2.MData;
import com.lenis0012.bukkit.marriage2.MPlayer;
import com.lenis0012.bukkit.marriage2.commands.Command;
import com.lenis0012.bukkit.marriage2.config.Message;
import com.lenis0012.bukkit.marriage2.config.Permissions;
import com.lenis0012.bukkit.marriage2.config.Settings;
import com.lenis0012.bukkit.marriage2.events.PlayerMarryEvent;
import com.lenis0012.bukkit.marriage2.internal.Register.Type;
import com.lenis0012.bukkit.marriage2.internal.data.DataConverter;
import com.lenis0012.bukkit.marriage2.internal.data.DataManager;
import com.lenis0012.bukkit.marriage2.internal.data.MarriageData;
import com.lenis0012.bukkit.marriage2.internal.data.MarriagePlayer;
import com.lenis0012.bukkit.marriage2.listeners.ChatListener;
import com.lenis0012.bukkit.marriage2.listeners.DatabaseListener;
import com.lenis0012.bukkit.marriage2.listeners.KissListener;
import com.lenis0012.bukkit.marriage2.listeners.PlayerListener;
import com.lenis0012.bukkit.marriage2.listeners.PlotSquaredListener;
import com.lenis0012.bukkit.marriage2.misc.ListQuery;
import com.lenis0012.pluginutils.modules.configuration.ConfigurationModule;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MarriageCore extends MarriageBase {
    private final Map<UUID, MarriagePlayer> players = Collections.synchronizedMap(new HashMap<UUID, MarriagePlayer>());
    private DataManager dataManager;
    private Dependencies dependencies;

    public MarriageCore(MarriagePlugin plugin) {
        super(plugin);
    }

    @Register(name = "config", type = Register.Type.ENABLE, priority = 0)
    public void loadConfig() {
//		plugin.saveDefaultConfig();
        enable();

        // Settings
        ConfigurationModule module = plugin.getModule(ConfigurationModule.class);
        module.registerSettings(Settings.class);
        module.reloadSettings(Settings.class, true);

        // Messages
        Message.reloadAll(this);

        // Permissions
        if(Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            if(!Permissions.setupPermissions()) {
                getLogger().log(Level.WARNING, "Vault was found, but no permission provider was detected!");
                getLogger().log(Level.INFO, "Falling back to bukkit permissions.");
            }
        } else {
            getLogger().log(Level.INFO, "Vault was not found, if you are having permission issues, please install it!");
            getLogger().log(Level.INFO, "Falling back to bukkit permissions.");
        }
    }

    @Register(name = "dependencies", type = Type.ENABLE, priority = 1)
    public void loadDependencies() {
        this.dependencies = new Dependencies(this);
    }

    @Register(name = "database", type = Register.Type.ENABLE)
    public void loadDatabase() {
        this.dataManager = new DataManager(this);

        // Load all players
        for(Player player : Bukkit.getOnlinePlayers()) {
            MarriagePlayer mp = dataManager.loadPlayer(player.getUniqueId());
            setMPlayer(player.getUniqueId(), mp);
        }
    }

    @Register(name = "listeners", type = Register.Type.ENABLE)
    public void registerListeners() {
        register(new PlayerListener(this));
        register(new ChatListener(this));
        register(new DatabaseListener(this));
        register(new KissListener(this));
        if(Settings.PLOTSQUARED_AUTO_TRUST.value() && Bukkit.getPluginManager().isPluginEnabled("PlotSquared")) {
            Plugin plotSquared = Bukkit.getPluginManager().getPlugin("PlotSquared");
            getLogger().log(Level.INFO, "Hooking with PlotSquared v" + plotSquared.getDescription().getVersion());
            register(new PlotSquaredListener());
        }
    }

    @Register(name = "commands", type = Register.Type.ENABLE)
    public void registerCommands() {
        for(Class<? extends Command> command : findClasses("com.lenis0012.bukkit.marriage2.commands", Command.class)) {
            register(command);
        }
    }

    @Register(name = "converter", type = Register.Type.ENABLE, priority = 10)
    public void loadConverter() {
        DataConverter converter = new DataConverter(this);
        if(converter.isOutdated()) {
            converter.convert();
        }
    }

    @Register(name = "database", type = Register.Type.DISABLE)
    public void saveDatabase() {
        unloadAll();
        dataManager.close();
    }

    @Override
    public MPlayer getMPlayer(UUID uuid) {
        MarriagePlayer player = players.get(uuid);
        if(player == null) {
            player = dataManager.loadPlayer(uuid);
            players.put(uuid, player);
        }

        return player;
    }

    @Override
    public MData marry(MPlayer player1, MPlayer player2) {
        return marry(player1, player2, null);
    }

    @Override
    public MData marry(MPlayer player1, MPlayer player2, MPlayer priest) {
        PlayerMarryEvent event = new PlayerMarryEvent(player1, player2, priest);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) {
            return null;
        }

        MarriageData mdata = new MarriageData(dataManager, player1.getUniqueId(), player2.getUniqueId());
        mdata.saveAsync();
        ((MarriagePlayer) player1).addMarriage(mdata);
        ((MarriagePlayer) player2).addMarriage(mdata);
        return mdata;
    }

    @Override
    public ListQuery getMarriageList(int scale, int page) {
        return dataManager.listMarriages(scale, page);
    }

    public void setMPlayer(UUID uuid, MarriagePlayer mp) {
        players.put(uuid, mp);
    }

    public boolean isMPlayerSet(UUID uuid) {
        return players.containsKey(uuid);
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public void removeMarriage(final MData mdata) {
        new Thread() {
            @Override
            public void run() {
                dataManager.deleteMarriage(mdata.getPlayer1Id(), mdata.getPllayer2Id());
            }
        }.start();
    }

    /**
     * Unload player from the memory
     *
     * @param uuid of player
     */
    public void unloadPlayer(UUID uuid) {
        final MarriagePlayer mPlayer = players.remove(uuid);
        if(mPlayer != null) {
            new Thread() {
                @Override
                public void run() {
                    dataManager.savePlayer(mPlayer);
                }
            }.start();
        }
    }

    public void unloadAll() {
        for(MarriagePlayer mp : players.values()) {
            dataManager.savePlayer(mp);
        }
        players.clear();
    }

    @Override
    public Dependencies dependencies() {
        return dependencies;
    }
}
