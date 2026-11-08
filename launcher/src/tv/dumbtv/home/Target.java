package tv.dumbtv.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Which home screen should sit on top after Amazon's launcher shows.
 *
 * <p>The owner picks one in Options, or hand edits {@code home.conf}:
 * <ul>
 *   <li>{@code home_target=aerial} — Aerial TV, animated wallpapers, heavier</li>
 *   <li>{@code home_target=gathin} — cleanyourtv's launcher, plain and light</li>
 *   <li>{@code home_target=custom} — any launcher named in {@code home_target_custom} as
 *       {@code package/activity} (FLauncher, Projectivy, …)</li>
 *   <li>{@code home_target=dumbtv} — this app's own home, i.e. upstream behaviour</li>
 *   <li>{@code home_target=auto} (default) — Aerial when it is installed, else CleanYourTV
 *       when it is, else this app's own home</li>
 * </ul>
 *
 * <p>Why a trampoline: {@code ActivityManager.getAppTasks()} only ever returns this process's
 * own tasks, so {@code moveTaskToFront} cannot raise another app's task, and starting another
 * app straight after a HOME press sits through Fire OS's five-second app-switch delay. So the
 * Keeper raises <em>this</em> app's Home (which needs no activity launch), arms itself here, and
 * Home's {@code onResume} starts the target as a foreground activity, which is exempt.
 * {@code Guard}'s accessibility path on Fire OS 7 and 8 arms the same way.
 */
final class Target {
    /** Aerial TV (github.com/pengchujin/aerial-tv). */
    private static final String AERIAL = "app.orchard.tv";
    private static final String AERIAL_HOME = "app.orchard.tv.MainActivity";
    /** cleanyourtv's launcher (github.com/Gathin23/cleanyourtv). */
    private static final String GATHIN = "com.gathin.tvhome";
    private static final String GATHIN_HOME = "com.gathin.tvhome.MainActivity";

    private static volatile boolean armed;

    private Target() {}

    /** The Keeper or Guard is about to raise Home: Home should pass the front on. */
    static void arm() {
        armed = true;
    }

    /** True once per arming; Home calls it from {@code onResume}. */
    static boolean consume() {
        boolean was = armed;
        armed = false;
        return was;
    }

    /**
     * The launcher to keep on top, or null when Home itself should stay. A target that is not
     * installed is reported once per bounce and treated as null, so a bad hand edit never
     * loops the Keeper.
     */
    static Intent resolve(Context c) {
        Config cfg = Config.get(c);
        cfg.refresh(); // a hand edit of home.conf takes effect without a reboot
        String want = cfg.get(Config.HOME_TARGET);
        String pkg;
        String act;
        if ("aerial".equals(want)) {
            pkg = AERIAL;
            act = AERIAL_HOME;
        } else if ("gathin".equals(want)) {
            pkg = GATHIN;
            act = GATHIN_HOME;
        } else if ("custom".equals(want)) {
            String raw = cfg.get(Config.HOME_TARGET_CUSTOM, "");
            int slash = raw.indexOf('/');
            if (slash <= 0 || slash == raw.length() - 1) {
                Keeper.note("home_target_custom wants package/activity, got \"" + raw + "\"");
                return null;
            }
            pkg = raw.substring(0, slash);
            act = raw.substring(slash + 1);
        } else if ("auto".equals(want)) {
            if (installed(c, AERIAL)) {
                pkg = AERIAL;
                act = AERIAL_HOME;
            } else if (installed(c, GATHIN)) {
                pkg = GATHIN;
                act = GATHIN_HOME;
            } else {
                return null;
            }
        } else {
            return null; // "dumbtv": upstream behaviour, this app's own home screen
        }
        if (!installed(c, pkg)) {
            Keeper.note("target " + pkg + " is not installed; keeping dumbtv home");
            return null;
        }
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(pkg, act))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
