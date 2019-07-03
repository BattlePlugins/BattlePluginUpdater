package mc.alk.battlepluginupdater;

import java.io.File;
import java.util.HashSet;
import java.util.logging.Level;

import mc.euro.version.Version;
import mc.euro.version.VersionFactory;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * @deprecated this class is known for causing issues with plugin downloads.
 * Instead, use {@link mc.alk.battlepluginupdater.SpigotUpdater}
 */
public class PluginUpdater {

    public static String getNameAndVersion(final Plugin plugin) {
        return plugin.getDescription().getName() + "_v" + plugin.getDescription().getVersion();
    }

    /**
     * UpdateOption: NONE, RELEASE, BETA, ALL.
     *
     * NONE : Get no releases. RELEASE : Get only release updates, ignore
     * alpha/beta builds. BETA : Get beta & release updates, but ignore alpha
     * builds. ALL : Get all updates: Alpha, Beta, & Release builds.
     *
     */
    public enum UpdateOption {

        /**
         * Get no releases.
         */
        NONE,
        /**
         * Get only release updates: Ignore alpha & beta builds.
         */
        RELEASE,
        /**
         * Get beta+release updates, but ignore alpha builds.
         */
        BETA,
        /**
         * Get all updates: Alpha, Beta, & Release builds.
         */
        ALL;

        public static UpdateOption fromString(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (Exception e) {
                if (name.equalsIgnoreCase("ALPHA")) {
                    return ALL;
                }
                return null;
            }
        }
    }

    /**
     * NONE don't show new versions CONSOLE show only to console log on startup
     * OPS announce to ops on join, will only show this message once per server
     * start
     */
    public enum AnnounceUpdateOption {

        /**
         * don't show new versions
         */
        NONE,
        /**
         * show only to console log on startup
         */
        CONSOLE,
        /**
         * announce to ops on join, will only show this message once per server
         * start
         */
        OPS;

        public static AnnounceUpdateOption fromString(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Check for updates for a given plugin. If there are updates then it will
     * announce the newer version to the console. It will download the newer jar
     * if the "update" variable is true. This happens in an asynchronous manner
     * to not lag the server while checking for the update
     *
     * @param plugin JavaPlugin
     * @param file File from the bukkit plugin, use this.getFile()
     * @param bukkitId the bukkit id of this plugin
     * @param updateOption when should we update the plugin
     * @param announceOption who should recieve announcements about a newer
     * version
     *
     * @deprecated use
     */
    @Deprecated
    public static void update(final Plugin plugin, final int bukkitId, final File file,
            final UpdateOption updateOption,
            final AnnounceUpdateOption announceOption) {
        if (updateOption == null || announceOption == null
                || updateOption == UpdateOption.NONE && announceOption == AnnounceUpdateOption.NONE) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateOption update = updateOption;
                Updater up = new Updater(plugin, bukkitId, file, Updater.UpdateType.NO_DOWNLOAD, false);

                String failedMessage = null;
                switch (up.getResult()) {
                    case DISABLED:/* admin doesn't want any updates */

                        update = UpdateOption.NONE;
                    // drop down
                    case SUCCESS:
                    /* good to go */

                    case UPDATE_AVAILABLE:
                    case FAIL_NOVERSION:
                    /* couldn't find a version */

                    case NO_UPDATE:
                        /* no newer version found, but his check is fairly naive, keep going*/

                        break;
                    case FAIL_DOWNLOAD:
                        /* shouldn't happen yet */ break;

                    case FAIL_DBO:
                        /* problem with accessing bukkit */

                        failedMessage = "Couldn't connect to bukkit website";
                        break;
                    case FAIL_BADID:
                        /* bad id */

                        failedMessage = "The id provided was invalid or doesn't exist on DBO";
                        break;
                    case FAIL_APIKEY:
                        failedMessage = "You have set a bad API key in the configuration";
                        break;
                }
                if (failedMessage != null) {
                    err("&4[" + getNameAndVersion(plugin) + "] &c" + failedMessage);
                    return;
                }
                Version curVersion = VersionFactory.getPluginVersion(plugin.getName());
                String remote = up.getLatestName();
                String delim = "^v|[\\s_-]v";
                UpdateOption remoteReleaseType = up.getLatestType() != null
                        ? UpdateOption.fromString(up.getLatestType().name()) : null;

                if (remoteReleaseType == null || remote == null || remote.split(delim).length != 2) {
                    err("&4[" + getNameAndVersion(plugin) + "] &ccan't find a version for the plugin result was &f"
                            + up.getResult() + " &creleaseType: " + up.getLatestType());
                    return;
                }

                if (curVersion.isLessThan(remote)) { /// We have found a newer version
                    /// Check to see if we want this release type
                    if (update.ordinal() >= remoteReleaseType.ordinal()) {
                        info("&2[" + getNameAndVersion(plugin)
                                + "] &ebeginning download of newer version: &2"
                                + remote
                                + " &e" + up.getLatestType().name());
                        up = new Updater(plugin, bukkitId, file, Updater.UpdateType.DEFAULT, false);
                        if (up.getResult() == Updater.UpdateResult.SUCCESS) {
                            info("&2[" + getNameAndVersion(plugin)
                                    + "] &edownloaded &2"
                                    + remote);
                        }
                    } else if (announceOption != AnnounceUpdateOption.NONE) {
                        String[] announce = new String[]{colorChat("&2[" + getNameAndVersion(plugin) + "] &ehas a newer &f"
                            + up.getLatestType().name() + " &eversion &f" + remote),
                            colorChat("&2[" + getNameAndVersion(plugin) + "]&5 " + up.getLatestFileLink())};
                        for (String msg : announce) {
                            info(msg);
                        }

                        if (announceOption == AnnounceUpdateOption.OPS) {
                            Listener ll = new AnnounceOpListener(announce, plugin);
                            Bukkit.getPluginManager().registerEvents(ll, plugin);
                        }
                    }
                }
            }
        });
        t.start();
    }

    static class AnnounceOpListener implements Listener {

        final String[] announce;
        HashSet<String> alreadyAnnounced = new HashSet<String>();

        AnnounceOpListener(String[] announce, Plugin plugin) {
            this.announce = announce;
        }

        @EventHandler
        public void onPlayerJoinEvent(PlayerJoinEvent event) {
            if (event.getPlayer().isOp() && !alreadyAnnounced.contains(event.getPlayer().getName())) {
                for (String s : announce) {
                    event.getPlayer().sendMessage(s);
                }
            }
        }
    }

    public static String colorChat(String msg) {
        return msg.replace('&', (char) 167);
    }

    public static void info(String msg) {
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch (Exception e) {
            System.out.println(colorChat(msg));
        }
    }

    public static void warn(String msg) {
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch (Exception e) {
            System.out.println(colorChat(msg));
        }
    }

    public static void err(String msg) {
        try {
            Bukkit.getConsoleSender().sendMessage(colorChat(msg));
        } catch (Exception e) {
            System.err.println(colorChat(msg));
        }
    }

}
