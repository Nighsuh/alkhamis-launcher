package com.gathin.tvhome;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The home switch used by Free My TV (MIT, package io.freemytv), narrowed to the packages that
 * actually decide the Home button: the Google TV and Android TV launchers, Google TV setup when
 * it answers HOME, recommendations when the Android TV launcher is home, and the Assistant.
 * Nothing here is uninstalled. Setup is left alone when it saved the Wi-Fi.
 */
final class HomeSwitch {
    static final String FREE_MY_TV = "io.freemytv";
    static final String LAUNCHERX = "com.google.android.apps.tv.launcherx";
    static final String TV_LAUNCHER = "com.google.android.tvlauncher";
    static final String SETUP = "com.google.android.tungsten.setupwraith";
    static final String RECOMMENDATIONS = "com.google.android.tvrecommendations";
    static final String[] ASSISTANT = {
            "com.google.android.katniss",
            "com.android.hotwordenrollment.okgoogle"
    };

    static final String QUERY = "cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
            + " -c android.intent.category.HOME";
    static final String RESOLVE = "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
            + " -c android.intent.category.HOME";
    static final String HOLDERS = "cmd role get-role-holders --user 0 android.app.role.HOME";
    static final String DISABLED = "pm list packages -d --user 0";
    static final String WIFI = "dumpsys wifi | grep -o -E 'cname=[^ ]*|^Wi-Fi is [a-z]+'";
    static final String WIFI_ON = "settings get global wifi_on";

    private static final Pattern CNAME = Pattern.compile("cname=([\\w.]+)");
    private static final Set<String> KNOWN;

    static {
        Set<String> names = new HashSet<>();
        names.add(FREE_MY_TV);
        names.add(LAUNCHERX);
        names.add(TV_LAUNCHER);
        names.add(SETUP);
        names.add(RECOMMENDATIONS);
        names.addAll(Arrays.asList(ASSISTANT));
        KNOWN = Collections.unmodifiableSet(names);
    }

    private HomeSwitch() {}

    static boolean known(String pkg) {
        return KNOWN.contains(pkg);
    }

    static String setHome(String self) {
        return "cmd package set-home-activity --user 0 " + self + "/.MainActivity";
    }

    /** Older package service builds take the command without --user. */
    static String setHomeLegacy(String self) {
        return "cmd package set-home-activity " + self + "/.MainActivity";
    }

    static String addRole(String self) {
        return "cmd role add-role-holder --user 0 android.app.role.HOME " + self + " 0";
    }

    /** Android 11–14 builds that reject the flags argument. */
    static String addRoleShort(String self) {
        return "cmd role add-role-holder android.app.role.HOME " + self;
    }

    static String disable(String pkg) {
        return "pm disable-user --user 0 " + pkg;
    }

    static String enable(String pkg) {
        return "pm enable " + pkg;
    }

    static String path(String pkg) {
        return "pm path " + pkg;
    }

    /** Commands this switch runs, on top of the ones LocalAdb already allows. */
    static boolean commandAllowed(String command, String self) {
        if (command == null || command.indexOf('\n') >= 0 || command.indexOf(';') >= 0) return false;
        if (command.equals(QUERY) || command.equals(RESOLVE) || command.equals(HOLDERS)
                || command.equals(DISABLED) || command.equals(WIFI) || command.equals(WIFI_ON)) {
            return true;
        }
        if (self != null && (command.equals(setHome(self)) || command.equals(setHomeLegacy(self))
                || command.equals(addRole(self)) || command.equals(addRoleShort(self)))) return true;
        String enable = "pm enable ";
        String probe = "pm path ";
        if (command.startsWith(probe)) return PackageGuard.safeName(command.substring(probe.length()));
        if (command.startsWith(enable)) {
            String name = command.substring(enable.length());
            return PackageGuard.safeName(name) && !PackageGuard.blocked(self, name);
        }
        String off = "pm disable-user --user 0 ";
        if (command.startsWith(off)) return known(command.substring(off.length()));
        if (command.equals("settings get secure enabled_accessibility_services")) return true;
        if (command.equals("settings put secure accessibility_enabled 1")) return true;
        String services = "settings put secure enabled_accessibility_services ";
        if (self != null && command.startsWith(services)) {
            String list = command.substring(services.length());
            return list.length() <= 800
                    && list.matches("[A-Za-z0-9_./:]+")
                    && list.contains(self + "/.HomeKeyService");
        }
        return false;
    }

    /** Package of the last component line, or "" when HOME is the system chooser. */
    static String packageOfResolve(String output) {
        if (output == null || output.isEmpty()) return "";
        String component = "";
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.indexOf('/') > 0 && !trimmed.contains(" ")) component = trimmed;
        }
        int slash = component.indexOf('/');
        if (slash <= 0) return "";
        String pkg = component.substring(0, slash);
        if ("android".equals(pkg) || component.contains("ResolverActivity") || component.contains("FallbackHome")) {
            return "";
        }
        return pkg;
    }

    static boolean holds(String holders, String pkg) {
        if (holders == null || pkg == null) return false;
        for (String part : holders.split("[\\s;]+")) {
            if (pkg.equals(part.trim())) return true;
        }
        return false;
    }

    static boolean installed(String pathOutput) {
        return pathOutput != null && pathOutput.contains("package:");
    }

    static boolean disabledListed(String disabledList, String pkg) {
        return disabledList != null && disabledList.contains("package:" + pkg);
    }

    static boolean turnedOff(String disableOutput) {
        return disableOutput != null && disableOutput.contains("disabled-user");
    }

    static boolean turnedOn(String enableOutput) {
        return enableOutput != null && enableOutput.contains("new state: enabled");
    }

    static boolean homeAccepted(String output) {
        return output != null && output.contains("Success");
    }

    /** Saved-network owners, or null when the Wi-Fi service did not answer. */
    static Set<String> wifiOwners(String dump) {
        if (dump == null || (!dump.contains("cname=") && !dump.contains("Wi-Fi is"))) return null;
        Set<String> owners = new HashSet<>();
        Matcher matcher = CNAME.matcher(dump);
        while (matcher.find()) owners.add(matcher.group(1));
        return owners;
    }

    static boolean setupAnswersHome(String query) {
        return query != null && query.contains(SETUP + "/");
    }

    /**
     * Packages that may be disabled after Home is set. A package that saved the Wi-Fi stays enabled:
     * disabling it deletes those networks. When Wi-Fi is on and the dump did not name an owner, nobody
     * is disabled. Setting the Home role does not touch those networks, so it is not decided here.
     */
    static java.util.List<String> disablePlan(java.util.List<String> candidates, Set<String> owners, boolean wifiOn) {
        java.util.List<String> plan = new java.util.ArrayList<>();
        if (candidates == null) return plan;
        if (wifiOn && owners == null) return plan;
        for (String pkg : candidates) {
            if (owners != null && owners.contains(pkg)) continue;
            plan.add(pkg);
        }
        return plan;
    }

    /** Why a switch stopped before it changed anything, or "" when it ran. */
    static final String STOP_FMT = "fmt";
    static final String STOP_WIFI = "wifi";

    interface Outcome {
        void finished(boolean ours, String resolved, String log, String stopped, java.util.List<String> changed);
    }

    /** Reads the TV, then runs set-home-activity and the role command, and reports the resolved package. */
    static void setHome(android.content.Context ctx, Outcome outcome) {
        Runner runner = new Runner(ctx, outcome);
        runner.shell(setHome(ctx.getPackageName()), () ->
                runner.shell(addRole(ctx.getPackageName()), () ->
                        runner.shell(RESOLVE, () -> runner.finish(packageOfResolve(runner.last)))));
    }

    /**
     * Free My TV's order: refuse when Free My TV still holds Home or a package saved the Wi-Fi,
     * set the home activity, then disable the Google launcher packages that are still on.
     */
    static void turnStockOff(android.content.Context ctx, Outcome outcome) {
        Runner runner = new Runner(ctx, outcome);
        runner.gather(() -> runner.turnStockOff());
    }

    /** pm enable for the packages this switch turns off, setup last, then resolve Home again. */
    static void turnStockOn(android.content.Context ctx, Outcome outcome) {
        Runner runner = new Runner(ctx, outcome);
        runner.gather(() -> runner.turnListedOn(stockPackages()));
    }

    static void turnOne(android.content.Context ctx, String pkg, boolean off, Outcome outcome) {
        Runner runner = new Runner(ctx, outcome);
        if (!PackageGuard.safeName(pkg) || PackageGuard.blocked(ctx.getPackageName(), pkg)) {
            outcome.finished(false, "", "blocked", "", java.util.Collections.emptyList());
            return;
        }
        String command = off ? disable(pkg) : enable(pkg);
        runner.shell(command, () -> runner.shell(setHome(ctx.getPackageName()), () ->
                runner.shell(RESOLVE, () -> {
                    if (off && turnedOff(runner.outputs.get(command))) runner.changed.add(pkg);
                    if (!off && turnedOn(runner.outputs.get(command))) runner.changed.add(pkg);
                    runner.finish(packageOfResolve(runner.last));
                })));
    }

    private static String[] stockPackages() {
        String[] names = new String[4 + ASSISTANT.length];
        names[0] = LAUNCHERX;
        names[1] = TV_LAUNCHER;
        names[2] = RECOMMENDATIONS;
        names[3] = SETUP;
        System.arraycopy(ASSISTANT, 0, names, 4, ASSISTANT.length);
        return names;
    }

    private static final class Runner {
        final android.content.Context ctx;
        final Outcome outcome;
        final StringBuilder log = new StringBuilder();
        final java.util.Map<String, String> outputs = new java.util.HashMap<>();
        final java.util.List<String> changed = new java.util.ArrayList<>();
        String last = "";
        boolean stopped;
        boolean skippedWifi;

        Runner(android.content.Context ctx, Outcome outcome) {
            this.ctx = ctx;
            this.outcome = outcome;
        }

        void gather(Runnable then) {
            String[] names = new String[stockPackages().length + 1];
            names[0] = FREE_MY_TV;
            System.arraycopy(stockPackages(), 0, names, 1, stockPackages().length);
            shell(QUERY, () -> shell(RESOLVE, () -> shell(HOLDERS, () ->
                    shell(DISABLED, () -> shell(WIFI, () -> shell(WIFI_ON, () -> pathAt(names, 0, then)))))));
        }

        void pathAt(String[] names, int index, Runnable then) {
            if (index >= names.length) {
                then.run();
                return;
            }
            shell(path(names[index]), () -> pathAt(names, index + 1, then));
        }

        void turnStockOff() {
            String self = ctx.getPackageName();
            String home = packageOfResolve(outputs.get(RESOLVE));
            String holders = outputs.get(HOLDERS);
            boolean fmtOn = installed(outputs.get(path(FREE_MY_TV)))
                    && !disabledListed(outputs.get(DISABLED), FREE_MY_TV);
            if (fmtOn && (FREE_MY_TV.equals(home) || holds(holders, FREE_MY_TV))) {
                finishStopped(STOP_FMT, home.isEmpty() ? FREE_MY_TV : home);
                return;
            }
            Set<String> owners = wifiOwners(outputs.get(WIFI));
            boolean wifiRunning = "1".equals(outputs.get(WIFI_ON) == null ? "" : outputs.get(WIFI_ON).trim());
            java.util.List<String> candidates = new java.util.ArrayList<>();
            if (setupAnswersHome(outputs.get(QUERY)) && present(SETUP)) candidates.add(SETUP);
            if (present(LAUNCHERX)) candidates.add(LAUNCHERX);
            if (present(TV_LAUNCHER)) candidates.add(TV_LAUNCHER);
            if (TV_LAUNCHER.equals(home) && present(RECOMMENDATIONS)) candidates.add(RECOMMENDATIONS);
            for (String assistant : ASSISTANT) if (present(assistant)) candidates.add(assistant);
            java.util.List<String> plan = disablePlan(candidates, owners, wifiRunning);
            skippedWifi = plan.size() < candidates.size();
            // The role change does not delete saved networks. Disabling the app that saved them does,
            // so that step is skipped above. TCL Google TV (Android 11–14) keeps Home on the role holder;
            // set-home-activity and add-role-holder are both tried, and a refusal of one does not skip the other.
            shellOptional(setHomeLegacy(self), () -> shellOptional(setHome(self), () ->
                    shellOptional(addRole(self), () -> shellOptional(addRoleShort(self), () ->
                            disableAt(plan, 0, () -> verify(self, true))))));
        }

        void turnListedOn(String[] names) {
            java.util.List<String> plan = new java.util.ArrayList<>();
            for (String name : names) {
                if (SETUP.equals(name)) continue;
                if (installed(outputs.get(path(name))) && disabledListed(outputs.get(DISABLED), name)) plan.add(name);
            }
            if (installed(outputs.get(path(SETUP))) && disabledListed(outputs.get(DISABLED), SETUP)) plan.add(SETUP);
            enableAt(plan, 0, () -> verify(ctx.getPackageName(), false));
        }

        boolean present(String pkg) {
            return installed(outputs.get(path(pkg))) && !disabledListed(outputs.get(DISABLED), pkg);
        }

        void disableAt(java.util.List<String> plan, int index, Runnable then) {
            if (index >= plan.size()) {
                then.run();
                return;
            }
            String pkg = plan.get(index);
            String command = disable(pkg);
            shellOptional(command, () -> {
                if (turnedOff(outputs.get(command))) changed.add(pkg);
                disableAt(plan, index + 1, then);
            });
        }

        void enableAt(java.util.List<String> plan, int index, Runnable then) {
            if (index >= plan.size()) {
                then.run();
                return;
            }
            String pkg = plan.get(index);
            String command = enable(pkg);
            shell(command, () -> {
                if (turnedOn(outputs.get(command))) changed.add(pkg);
                enableAt(plan, index + 1, then);
            });
        }

        void verify(String self, boolean again) {
            shell(RESOLVE, () -> {
                String pkg = packageOfResolve(last);
                if (again && !self.equals(pkg)) {
                    shell(setHome(self), () -> shell(RESOLVE, () -> finish(packageOfResolve(last))));
                    return;
                }
                finish(pkg);
            });
        }

        void shell(String command, Runnable then) {
            shell(command, then, false);
        }

        /** Like {@link #shell}, but a refused command does not end the switch. The next command still runs. */
        void shellOptional(String command, Runnable then) {
            shell(command, then, true);
        }

        void shell(String command, Runnable then, boolean optional) {
            if (stopped) return;
            log.append("$ ").append(command).append('\n');
            LocalAdb.get(ctx).runRaw(command, (ok, message) -> {
                String text = message == null ? "" : message;
                outputs.put(command, text);
                last = text;
                log.append(text).append("\n\n");
                if (!ok && !optional) finishStopped("", packageOfResolve(text));
                else then.run();
            });
        }

        void finish(String resolved) {
            if (stopped) return;
            stopped = true;
            boolean ours = ctx.getPackageName().equals(resolved);
            String why = skippedWifi && !ours ? STOP_WIFI : "";
            outcome.finished(ours, resolved, log.toString(), why, changed);
        }

        void finishStopped(String why, String resolved) {
            if (stopped) return;
            stopped = true;
            outcome.finished(false, resolved == null ? "" : resolved, log.toString(), why, changed);
        }
    }
}
