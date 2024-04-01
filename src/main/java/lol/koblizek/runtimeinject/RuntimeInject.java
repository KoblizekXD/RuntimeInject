package lol.koblizek.runtimeinject;

import org.bukkit.plugin.java.JavaPlugin;

public final class RuntimeInject extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().warning("===================================================");
        getLogger().warning("WARNING");
        getLogger().warning("This plugin can be VERY harmful if used incorrectly");
        getLogger().warning("NEVER EVER run code you don't know!");
        getLogger().warning("===================================================");

        getCommand("java").setExecutor(new UwUCommand());
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
