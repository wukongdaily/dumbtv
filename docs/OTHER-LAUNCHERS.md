# Keeping another launcher on top

dumbtv home can bounce a *different* launcher back to the front instead of its own home
screen. Amazon's launcher stays installed and protected either way; dumbtv home stays
installed too, because on Fire OS 6 it is the engine, not the picture.

Measured on an AFTMM Fire TV Stick 4K (Fire OS 6.7.1.1, Android 7.1, SDK 25, 1.25 GB)
with the HOME key sent by a real remote.

## Why it needs a trampoline

Two facts about Fire OS 6, both measured on that stick:

1. `ActivityManager.getAppTasks()` only ever returns **this process's own** tasks, so
   `moveTaskToFront`, the call that escapes the 5 s app-switch delay, cannot raise another
   app's task.
2. Starting another app straight after a HOME press does sit through that delay: an
   `AlarmManager` start of the other launcher took about 5 s on screen.

An alarm start of *dumbtv's own* Home is exempt, and once Home is in the foreground it may
start anything it likes without the delay. So the bounce is two hops:

**Keeper notices Amazon's home → raises Home → Home's `onResume` starts the chosen
launcher and finishes nothing.**

Amazon's home shows for a fraction of a second, and dumbtv's own home shows for a frame or
two as it passes the front on. Timings from HOME to the other launcher being resumed:
29–48 ms across the usual cases, 198 ms on the first bounce after a settings edit (that
round re-reads the settings file), and the boot case goes through the alarm because no task
exists yet.

`Target` holds the choice and the presets; `Keeper` (Fire OS 6) and `Guard` (Fire OS 7 and
8) both arm it before they raise Home.

## Choosing it

Options has one row, **Home to return to**, and the same value is a line in
[the settings file](../launcher/README.md#the-settings-file):

| `home_target` | What comes back after HOME |
|---|---|
| `auto` (default) | Aerial if it is installed, else cleanyourtv's launcher if it is, else dumbtv home |
| `aerial` | Aerial TV (`app.orchard.tv/.MainActivity`) — animated wallpapers, heavier |
| `gathin` | cleanyourtv's launcher (`com.gathin.tvhome/.MainActivity`) — plain, light |
| `custom` | whatever `home_target_custom` names, as `package/activity` |
| `dumbtv` | dumbtv home itself: the behaviour this repository ships |

A hand edit is picked up at the next bounce, with no reboot and no restart of the app:

```sh
adb connect <tv-ip>:5555
adb shell 'echo home_target=gathin > /sdcard/Android/data/tv.dumbtv.home/files/home.conf'
adb shell input keyevent KEYCODE_HOME        # gathin is in front
```

On Fire OS 8, where adb cannot write that folder, use the media folder as described in
[Fire OS 8: where adb can write](../launcher/README.md#fire-os-8-where-adb-can-write).

A target that is not installed is not a failure: the Keeper logs it once per bounce and
keeps dumbtv home on top, so a typo cannot put the two into a fight.

## What the other launcher needs on the TV

- **Aerial.** Its own release installs on Android 7.1 (minSdk 21). On Android 7 it has to be
  compiled once by hand after every install or upgrade, or the first scrolls are slow:
  `adb shell cmd package compile -m speed -f app.orchard.tv`, which takes tens of seconds.
  Its settings button needs a build with a Fire TV branch in its settings lookup: plain
  `android.settings.SETTINGS` resolves to a compliance stub
  (`com.amazon.tv.settings.v2/.compliance.CTSDummySettingsHandler`) that closes as it opens.
- **cleanyourtv's launcher.** The upstream APK is built `minSdk 26`, so on Fire OS 6 it needs a rebuild at
  API 25: core-library desugaring for the `java.time` the lunar row uses, plus version branches around the
  five calls newer than the floor — `PackageManager.getChangedPackages`, `Resources.getFont` (without this
  one, opening its app library kills the launcher), `Files.readAllLines(file.toPath())`, and the
  `Bitmap.Config.HARDWARE` field, whose `NoSuchFieldError` the surrounding `catch (Exception ...)` does not
  hold. The last two are what `lintRelease` reports once the floor drops, so run it rather than trusting a
  smoke test: the launcher boots and draws fine with all of them still in place. That build is published
  here: [wukongdaily/cleanyourtv](https://github.com/wukongdaily/cleanyourtv/tree/android-71-launcher-build),
  with [its own notes](https://github.com/wukongdaily/cleanyourtv/blob/android-71-launcher-build/docs/android-71.md)
  (the APK grows to about 700 KB, so upstream's size gate fails it on purpose).
- **Anything else**, through `home_target_custom`: name the activity HOME should land on.

One takeover engine at a time. If the other launcher also runs an accessibility or watchdog
takeover, turn that one off, or the two will trade the front.

## Checking it

```sh
adb logcat -d | grep dumbtvtest
```

```
keeper: moveToFront (for ComponentInfo{app.orchard.tv/app.orchard.tv.MainActivity})
home: handed front to ComponentInfo{app.orchard.tv/app.orchard.tv.MainActivity}
keeper: alarm set (home trampoline, for ComponentInfo{...})   # no own task yet, e.g. after boot
keeper: moveToFront                                           # home_target=dumbtv
home_target_custom wants package/activity, got ""             # bad line; dumbtv home stays
```

`Keeper` is not exported, so `adb shell am startservice tv.dumbtv.home/.Keeper` is refused.
It starts with the home screen and at boot; to test it alone, reboot.

## Fire OS 7 and 8

Those use `Guard`, and `Guard.home()` arms the same hand-off. **Not verified on a Fire OS 7
or 8 device**: on the Fire OS 8 stick we have, the other launcher takes HOME through its own
accessibility service, which that firmware does bind, so the trampoline was never needed.
`./scripts/verify.sh` still checks the guard binding as before.

## Giving it back

`home_target=dumbtv` keeps dumbtv home. To hand HOME back to Amazon completely, uninstall
the engine: `adb uninstall tv.dumbtv.home`.
