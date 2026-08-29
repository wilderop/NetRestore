package com.wilder0p.netrestore;

import org.bukkit.plugin.java.JavaPlugin;

public final class NetRestore extends JavaPlugin {

    private IncidentMonitor incidents;
    private OfferStore offers;
    private CombatTracker combat;
    private SessionTracker sessions;
    private AuditLog audit;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.audit = new AuditLog(this);
        this.combat = new CombatTracker(this);
        this.sessions = new SessionTracker(this);
        this.offers = new OfferStore(this);
        this.incidents = new IncidentMonitor(this);
        this.sessions.seedOnline();

        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(sessions, this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);

        RestoreCommand restore = new RestoreCommand(this);
        AdminCommand admin = new AdminCommand(this);
        if (getCommand("restore") != null) {
            getCommand("restore").setExecutor(restore);
            getCommand("restore").setTabCompleter(restore);
        }
        if (getCommand("netrestore") != null) {
            getCommand("netrestore").setExecutor(admin);
            getCommand("netrestore").setTabCompleter(admin);
        }

        incidents.start();
        getLogger().info("NetRestore enabled.");
    }

    @Override
    public void onDisable() {
        if (incidents != null) {
            incidents.stop();
        }
        if (offers != null) {
            offers.purgeExpired();
        }
        getLogger().info("NetRestore disabled.");
    }

    public IncidentMonitor incidents() {
        return incidents;
    }

    public OfferStore offers() {
        return offers;
    }

    public CombatTracker combat() {
        return combat;
    }

    public SessionTracker sessions() {
        return sessions;
    }

    public AuditLog audit() {
        return audit;
    }

    public void reloadAll() {
        reloadConfig();
        incidents.reload();
        getLogger().info("NetRestore config reloaded.");
    }
}
