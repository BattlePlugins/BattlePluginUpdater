package mc.alk.battlepluginupdater;

import mc.alk.battlepluginupdater.checker.UpdateChecker;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SpigotUpdater {

    private Plugin plugin;
    private int pluginId;
    private String downloadLink;

    private String updateFolder;

    public SpigotUpdater(Plugin plugin, int pluginId, String downloadLink) {
        this.plugin = plugin;
        this.pluginId = pluginId;
        this.downloadLink = downloadLink;

        this.updateFolder = plugin.getServer().getUpdateFolder();
    }

    public void update() {
        File pluginFile = plugin.getDataFolder().getParentFile();
        File updaterFile = new File(pluginFile, "Updater");
        File updaterConfigFile = new File(updaterFile, "config.yml");

        YamlConfiguration config = new YamlConfiguration(); // Config file
        config.options().header("This configuration file affects all plugins using the Updater system (version 2+ - http://forums.bukkit.org/threads/96681/ )" + '\n'
                + "If you wish to use your API key, read http://wiki.bukkit.org/ServerMods_API and place it below." + '\n'
                + "Some updating systems will not adhere to the disabled value, but these may be turned off in their plugin's configuration.");
        config.addDefault("api-key", "PUT_API_KEY_HERE");
        config.addDefault("disable", false);

        if (!updaterFile.exists()) {
            updaterFile.mkdir();
        }

        boolean createFile = !updaterConfigFile.exists();
        try {
            if (createFile) {
                updaterConfigFile.createNewFile();
                config.options().copyDefaults(true);
                config.save(updaterConfigFile);
            } else {
                config.load(updaterConfigFile);
            }
        } catch (final Exception e) {
            if (createFile) {
                plugin.getLogger().severe("The updater could not create configuration at " + updaterFile.getAbsolutePath());
            } else {
                plugin.getLogger().severe("The updater could not load configuration at " + updaterFile.getAbsolutePath());
            }
            plugin.getLogger().log(Level.SEVERE, null, e);
        }

        boolean disabled = false;
        if (config.contains("disable"))
            disabled = config.getBoolean("disable", false);

        if (disabled) {
            plugin.getLogger().warning("You have opted-out of auto updating, so you won't know for certain if you're running the latest version.");
            return;
        }

        UpdateChecker.init(plugin, pluginId).requestUpdateCheck().whenComplete((result, exception) -> {
            plugin.getLogger().info(ChatColor.GOLD + "Running " + plugin.getDescription().getName() + " v" + plugin.getDescription().getVersion() + ".");
            switch (result.getReason()) {
                case UP_TO_DATE:
                    plugin.getLogger().info(ChatColor.GREEN + "You are currently running the latest version.");
                    break;
                case UNRELEASED_VERSION:
                    plugin.getLogger().info(ChatColor.DARK_GREEN + "You are currently running a version slightly ahead from release (development build?)");
                    break;
                case INVALID_JSON:
                case UNKNOWN_ERROR:
                case COULD_NOT_CONNECT:
                case UNAUTHORIZED_QUERY:
                    plugin.getLogger().warning("An error occurred when trying to update the plugin. If this persists, please contact the BattlePlugins team!");
                    break;
                case UNSUPPORTED_VERSION_SCHEME:
                    plugin.getLogger().warning("An error occurred with the plugin version scheme, please contact the BatlePlugins team!");
                case NEW_UPDATE:
                    plugin.getLogger().info(ChatColor.AQUA + "A new update was found: " + plugin.getDescription().getName() + " " + result.getNewestVersion());

                    File folder = new File(plugin.getDataFolder().getParent(), updateFolder);
                    downloadFile(folder, plugin.getDescription().getName() + ".jar", result.getNewestVersion(), String.format(downloadLink, result.getNewestVersion()));
            }
        });
    }

    /**
     * Downloads a file from the specified URL into the server's update folder.
     *
     * @param folder the updates folder location.
     * @param file the name of the file to save it as.
     * @param link the url of the file.
     * @param version the version of the plugin.
     */
    private void downloadFile(File folder, String file, String version, String link) {
        if (!folder.exists()) {
            folder.mkdir();
        }
        BufferedInputStream in = null;
        FileOutputStream fout = null;
        try {
            // Download the file
            final URL url = new URL(link);
            final int fileLength = url.openConnection().getContentLength();
            in = new BufferedInputStream(url.openStream());
            fout = new FileOutputStream(folder.getAbsolutePath() + File.separator + file);

            final byte[] data = new byte[1024];
            int count;
            this.plugin.getLogger().info("About to download a new update: " + version);
            long downloaded = 0;
            while ((count = in.read(data, 0, 1024)) != -1) {
                downloaded += count;
                fout.write(data, 0, count);
            }
            //Just a quick check to make sure we didn't leave any files from last time...
            for (final File xFile : new File(this.plugin.getDataFolder().getParent(), this.updateFolder).listFiles()) {
                if (xFile.getName().endsWith(".zip")) {
                    xFile.delete();
                }
            }
            // Check to see if it's a zip file, if it is, unzip it.
            final File dFile = new File(folder.getAbsolutePath() + File.separator + file);
            if (dFile.getName().endsWith(".zip")) {
                // Unzip
                this.unzip(dFile.getCanonicalPath());
            }
            this.plugin.getLogger().info("Finished updating.");
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("The auto-updater tried to download a new update, but was unsuccessful.");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (fout != null) {
                    fout.close();
                }
            } catch (final Exception ex) {
            }
        }
    }

    /**
     * Part of Zip-File-Extractor, modified by Gravity for use with Updater.
     *
     * @param file the location of the file to extract.
     */
    private void unzip(String file) {
        try {
            final File fSourceZip = new File(file);
            final String zipPath = file.substring(0, file.length() - 4);
            ZipFile zipFile = new ZipFile(fSourceZip);
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                File destinationFilePath = new File(zipPath, entry.getName());
                destinationFilePath.getParentFile().mkdirs();
                if (entry.isDirectory()) {
                    continue;
                } else {
                    final BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
                    int b;
                    final byte buffer[] = new byte[1024];
                    final FileOutputStream fos = new FileOutputStream(destinationFilePath);
                    final BufferedOutputStream bos = new BufferedOutputStream(fos, 1024);
                    while ((b = bis.read(buffer, 0, 1024)) != -1) {
                        bos.write(buffer, 0, b);
                    }
                    bos.flush();
                    bos.close();
                    bis.close();
                    final String name = destinationFilePath.getName();
                    if (name.endsWith(".jar") && this.pluginFile(name)) {
                        destinationFilePath.renameTo(new File(this.plugin.getDataFolder().getParent(), this.updateFolder + File.separator + name));
                    }
                }
                entry = null;
                destinationFilePath = null;
            }
            e = null;
            zipFile.close();
            zipFile = null;

            // Move any plugin data folders that were included to the right place, Bukkit won't do this for us.
            for (final File dFile : new File(zipPath).listFiles()) {
                if (dFile.isDirectory()) {
                    if (this.pluginFile(dFile.getName())) {
                        final File oFile = new File(this.plugin.getDataFolder().getParent(), dFile.getName()); // Get current dir
                        final File[] contents = oFile.listFiles(); // List of existing files in the current dir
                        for (final File cFile : dFile.listFiles()) // Loop through all the files in the new dir
                        {
                            boolean found = false;
                            for (final File xFile : contents) // Loop through contents to see if it exists
                            {
                                if (xFile.getName().equals(cFile.getName())) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                // Move the new file into the current dir
                                cFile.renameTo(new File(oFile.getCanonicalFile() + File.separator + cFile.getName()));
                            } else {
                                // This file already exists, so we don't need it anymore.
                                cFile.delete();
                            }
                        }
                    }
                }
                dFile.delete();
            }
            new File(zipPath).delete();
            fSourceZip.delete();
        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "The auto-updater tried to unzip a new update file, but was unsuccessful.", e);
        }
        new File(file).delete();
    }

    /**
     * Check if the name of a jar is one of the plugins currently installed,
     * used for extracting the correct files out of a zip.
     *
     * @param name a name to check for inside the plugins folder.
     * @return true if a file inside the plugins folder is named this.
     */
    private boolean pluginFile(String name) {
        for (final File file : new File("plugins").listFiles()) {
            if (file.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}