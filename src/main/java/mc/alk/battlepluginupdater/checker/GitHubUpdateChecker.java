package mc.alk.battlepluginupdater.checker;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class to assist in checking for updates for plugins uploaded to
 * <a href="https://github.com">GitHub</a>. Before any members of this
 * class are accessed, {@link #init(Plugin, String, String)} must be invoked by the plugin,
 * preferrably in its {@link JavaPlugin#onEnable()} method, though that is not a
 * requirement.
 * <p>
 * This class performs asynchronous queries to <a href="https://api.github.com/repos/">GitHub</a>,
 * an REST server which is updated periodically. If the results of {@link #requestUpdateCheck()}
 * are inconsistent with what is published on GitHub, it may be due to GitHub's cache.
 * Results will be updated in due time.
 *
 * This is a modified version of Choco's {@link SpigotUpdateChecker} to work with
 * GitHub releases - primarly used for the BattlePlugins.
 *
 * @author Parker Hawke - 2008Choco, Redned
 */
public final class GitHubUpdateChecker {

    public static final VersionScheme VERSION_SCHEME_DECIMAL = (first, second) -> {
        String[] firstSplit = splitVersionInfo(first), secondSplit = splitVersionInfo(second);
        if (firstSplit == null || secondSplit == null) return null;

        for (int i = 0; i < Math.min(firstSplit.length, secondSplit.length); i++) {
            int currentValue = NumberUtils.toInt(firstSplit[i]), newestValue = NumberUtils.toInt(secondSplit[i]);

            if (newestValue > currentValue) {
                return second;
            } else if (newestValue < currentValue) {
                return first;
            }
        }

        return (secondSplit.length > firstSplit.length) ? second : first;
    };

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0";
    private static final String UPDATE_URL = "https://api.github.com/repos/%s/%s/releases/latest";
    private static final Pattern DECIMAL_SCHEME_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)*");

    private static GitHubUpdateChecker instance;

    private UpdateResult lastResult = null;

    private final Plugin plugin;
    private final String owner;
    private final String repo;
    private final VersionScheme versionScheme;

    private GitHubUpdateChecker(Plugin plugin, String owner, String repo, VersionScheme versionScheme) {
        this.plugin = plugin;
        this.owner = owner;
        this.repo = repo;
        this.versionScheme = versionScheme;
    }

    /**
     * Request an update check to GitHub. This request is asynchronous and may not complete
     * immediately as an HTTP GET request is published to the GitHub API.
     *
     * @return a future update result
     */
    public CompletableFuture<UpdateResult> requestUpdateCheck() {
        return CompletableFuture.supplyAsync(() -> {
            int responseCode = -1;
            try {
                URL url = new URL(String.format(UPDATE_URL, owner, repo));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.addRequestProperty("User-Agent", USER_AGENT);

                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                responseCode = connection.getResponseCode();

                JsonElement element = new JsonParser().parse(reader);
                if (!element.isJsonObject()) {
                    return new UpdateResult(UpdateReason.INVALID_JSON);
                }

                reader.close();

                JsonObject versionObject = element.getAsJsonObject();
                String current = plugin.getDescription().getVersion(), newest = versionObject.get("tag_name").getAsString();
                String latest = versionScheme.compareVersions(current, newest);
                boolean prerelease = versionObject.get("prerelease").getAsBoolean();

                if (latest == null) {
                    return new UpdateResult(UpdateReason.UNSUPPORTED_VERSION_SCHEME);
                } else if (latest.equals(current)) {
                    return new UpdateResult(current.equals(newest) ? UpdateReason.UP_TO_DATE : UpdateReason.UNRELEASED_VERSION);
                } else if (latest.equals(newest) && prerelease) {
                    return new UpdateResult(UpdateReason.NEW_PRELEASE, latest);
                } else if (latest.equals(newest))
                    return new UpdateResult(UpdateReason.NEW_UPDATE, latest);
            } catch (IOException e) {
                return new UpdateResult(UpdateReason.COULD_NOT_CONNECT);
            } catch (JsonSyntaxException e) {
                return new UpdateResult(UpdateReason.INVALID_JSON);
            }

            return new UpdateResult(responseCode == 401 ? UpdateReason.UNAUTHORIZED_QUERY : UpdateReason.UNKNOWN_ERROR);
        });
    }

    /**
     * Get the last update result that was queried by {@link #requestUpdateCheck()}. If no update
     * check was performed since this class' initialization, this method will return null.
     *
     * @return the last update check result. null if none.
     */
    public UpdateResult getLastResult() {
        return lastResult;
    }

    private static String[] splitVersionInfo(String version) {
        Matcher matcher = DECIMAL_SCHEME_PATTERN.matcher(version);
        if (!matcher.find()) return null;

        return matcher.group().split("\\.");
    }

    /**
     * Initialize this update checker with the specified values and return its instance. If an instance
     * of UpdateChecker has already been initialized, this method will act similarly to {@link #get()}
     * (which is recommended after initialization).
     *
     * @param plugin the plugin for which to check updates. Cannot be null
     * @param owner the owner of the repo. Cannot be null
     * @param repo the name of the repo. Cannot be null
     * @param versionScheme a custom version scheme parser. Cannot be null
     *
     * @return the UpdateChecker instance
     */
    public static GitHubUpdateChecker init(Plugin plugin, String owner, String repo, VersionScheme versionScheme) {
        Preconditions.checkArgument(plugin != null, "Plugin cannot be null");
        Preconditions.checkArgument(owner != null, "Repo owner cannot be null");
        Preconditions.checkArgument(repo != null, "Repo cannot be null");
        Preconditions.checkArgument(versionScheme != null, "null version schemes are unsupported");

        return (instance == null) ? instance = new GitHubUpdateChecker(plugin, owner, repo, versionScheme) : instance;
    }

    /**
     * Initialize this update checker with the specified values and return its instance. If an instance
     * of UpdateChecker has already been initialized, this method will act similarly to {@link #get()}
     * (which is recommended after initialization).
     *
     * @param plugin the plugin for which to check updates. Cannot be null
     * @param owner the owner of the repo. Cannot be null
     * @param repo the name of the repo. Cannot be null
     *
     * @return the UpdateChecker instance
     */
    public static GitHubUpdateChecker init(Plugin plugin, String owner, String repo) {
        return init(plugin, owner, repo, VERSION_SCHEME_DECIMAL);
    }

    /**
     * Get the initialized instance of UpdateChecker. If {@link #init(Plugin, String, String)} has not yet been
     * invoked, this method will throw an exception.
     *
     * @return the UpdateChecker instance
     */
    public static GitHubUpdateChecker get() {
        Preconditions.checkState(instance != null, "Instance has not yet been initialized. Be sure #init() has been invoked");
        return instance;
    }

    /**
     * Check whether the UpdateChecker has been initialized or not (if {@link #init(Plugin, String, String)}
     * has been invoked) and {@link #get()} is safe to use.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return instance != null;
    }


    /**
     * A functional interface to compare two version Strings with similar version schemes.
     */
    @FunctionalInterface
    public static interface VersionScheme {

        /**
         * Compare two versions and return the higher of the two. If null is returned, it is assumed
         * that at least one of the two versions are unsupported by this version scheme parser.
         *
         * @param first the first version to check
         * @param second the second version to check
         *
         * @return the greater of the two versions. null if unsupported version schemes
         */
        public String compareVersions(String first, String second);

    }

    /**
     * A constant reason for the result of {@link UpdateResult}.
     */
    public static enum UpdateReason {

        /**
         * A new update is available for download on GitHub.
         */
        NEW_UPDATE, // The only reason that requires an update

        /**
         * A new prerelease update is available.
         */
        NEW_PRELEASE,

        /**
         * A successful connection to the GitHub API could not be established.
         */
        COULD_NOT_CONNECT,

        /**
         * The JSON retrieved from GitHub was invalid or malformed.
         */
        INVALID_JSON,

        /**
         * A 401 error was returned by the GitHub API.
         */
        UNAUTHORIZED_QUERY,

        /**
         * The version of the plugin installed on the server is greater than the one uploaded
         * to GitHub's releases section.
         */
        UNRELEASED_VERSION,

        /**
         * An unknown error occurred.
         */
        UNKNOWN_ERROR,

        /**
         * The plugin uses an unsupported version scheme, therefore a proper comparison between
         * versions could not be made.
         */
        UNSUPPORTED_VERSION_SCHEME,

        /**
         * The plugin is up to date with the version released on GitHub's releases section.
         */
        UP_TO_DATE;

    }

    /**
     * Represents a result for an update query performed by {@link GitHubUpdateChecker#requestUpdateCheck()}.
     */
    public final class UpdateResult {

        private final UpdateReason reason;
        private final String newestVersion;

        { // An actual use for initializer blocks. This is madness!
            GitHubUpdateChecker.this.lastResult = this;
        }

        private UpdateResult(UpdateReason reason, String newestVersion) {
            this.reason = reason;
            this.newestVersion = newestVersion;
        }

        private UpdateResult(UpdateReason reason) {
            Preconditions.checkArgument(reason != UpdateReason.NEW_UPDATE, "Reasons that require updates must also provide the latest version String");
            this.reason = reason;
            this.newestVersion = plugin.getDescription().getVersion();
        }

        /**
         * Get the constant reason of this result.
         *
         * @return the reason
         */
        public UpdateReason getReason() {
            return reason;
        }

        /**
         * Check whether or not this result requires the user to update.
         *
         * @return true if requires update, false otherwise
         */
        public boolean requiresUpdate() {
            return reason == UpdateReason.NEW_UPDATE;
        }

        /**
         * Get the latest version of the plugin. This may be the currently installed version, it
         * may not be. This depends entirely on the result of the update.
         *
         * @return the newest version of the plugin
         */
        public String getNewestVersion() {
            return newestVersion;
        }

    }

}