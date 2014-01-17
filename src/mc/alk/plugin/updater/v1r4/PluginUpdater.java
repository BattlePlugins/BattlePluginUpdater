package mc.alk.plugin.updater.v1r4;

import net.gravitydevelopment.updater.Updater;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Originally a class that downloaded and updated bukkit plugins.  Since the new bukkit rules
 * this class has been converted into a wrapper around Gravity's Updater class
 */
public class PluginUpdater {

    public static String getNameAndVersion(final JavaPlugin plugin) {
        return plugin.getDescription().getName() + "_v"+plugin.getDescription().getVersion();
    }

    /**
     * Check for updates for a given plugin.
     * If there are updates then it will announce the newer version to the console. It will download the newer
     * jar if the "update" variable is true.
     * This happens in an asynchronous manner to not lag the server while checking for the update
     * @param plugin JavaPlugin
     * @param file File from the bukkit plugin, use this.getFile()
     * @param bukkitId the bukkit id of this plugin
     * @param update whether we should update the plugin or simply announce that there is a newer version
     */
    @SuppressWarnings("deprecation")
    public static void announceNewerAndDownloadIfNeeded(final JavaPlugin plugin, final int bukkitId, final File file, final boolean update) {

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Updater up = new Updater(plugin, bukkitId, file, Updater.UpdateType.NO_DOWNLOAD, false);
                Version curVersion = new Version(plugin.getDescription().getVersion());
                Version remoteVersion = new Version(up.getLatestName().split("_v")[1]);
                if (curVersion.compareTo(remoteVersion) < 0) {
                    info("&4[" + getNameAndVersion(plugin) + "] &ehas a newer version &f" + remoteVersion);
                    if (update) {
                        up = new Updater(plugin, bukkitId, file, Updater.UpdateType.DEFAULT, false);
                        if (up.getResult() == Updater.UpdateResult.SUCCESS)
                            info("&4" + getNameAndVersion(plugin) + " &ehas downloaded &f" + up.getLatestName().split("_v")[1]);
                    }
                }
            }
        });
        t.start();
    }

    public static String colorChat(String msg) {return msg.replace('&', (char) 167);}

    public static void info(String msg){
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch(Exception e) {
            System.out.println(colorChat(msg));
        }
    }
    public static void warn(String msg){
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch(Exception e) {
            System.out.println(colorChat(msg));
        }
    }
    public static void err(String msg){
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch(Exception e) {
            System.err.println(colorChat(msg));
        }
    }

}
