package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private LauncherLocale() {
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    /** base with the language chosen in settings, or the theme locale, or base itself for the TV's language. */
    static Context wrap(Context base) {
        String tag = chosenTag(base);
        if (tag.isEmpty()) tag = Config.load(base).locale;
        Locale locale = parse(tag);
        if (locale == null) return base;
        // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
        Locale.setDefault(locale);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(locale);
        c.setLayoutDirection(locale);
        return base.createConfigurationContext(c);
    }

    /** "" follows the TV. "en", or Arabic with Western (latn) or Arabic-Indic (arab) digits. */
    static final String DIGITS_EXPLICIT = "digits_explicit";

    static String chosenTag(Context base) {
        android.content.SharedPreferences prefs = base.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(DIGITS_EXPLICIT, false)
                && !"latn".equals(prefs.getString(LanguageActivity.DIGITS_KEY, "latn"))) {
            prefs.edit().putString(LanguageActivity.DIGITS_KEY, "latn").apply();
        }
        String choice = prefs.getString(LanguageActivity.LANG_KEY, "");
        boolean arabDigits = prefs.getBoolean(DIGITS_EXPLICIT, false)
                && "arab".equals(prefs.getString(LanguageActivity.DIGITS_KEY, "latn"));
        String language = choice.isEmpty()
                ? base.getResources().getConfiguration().getLocales().get(0).getLanguage() : choice;
        if ("en".equals(choice)) return "en";
        if ("ar".equals(language)) return arabDigits ? "ar-EG-u-nu-arab" : "ar-EG-u-nu-latn";
        return choice.isEmpty() ? "" : choice;
    }
}
