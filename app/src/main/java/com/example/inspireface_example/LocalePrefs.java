package com.example.inspireface_example;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Per-app language selection. The demo defaults to English regardless of the system
 * language; the bottom-right toggle on the liveness screen switches to Chinese and back.
 * The choice is stored in SharedPreferences and re-applied on every app start.
 */
public final class LocalePrefs {

    private static final String PREFS = "settings";
    private static final String KEY_LOCALE = "app_locale";
    private static final String ENGLISH = "en";
    private static final String CHINESE = "zh";

    private LocalePrefs() {
    }

    /** Applies the stored language (English by default). Call from Application.onCreate. */
    public static void applyStored(Context context) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(stored(context)));
    }

    /** Switches between English and Chinese; running activities recreate automatically. */
    public static void toggle(Context context) {
        String next = CHINESE.equals(stored(context)) ? ENGLISH : CHINESE;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LOCALE, next).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next));
    }

    private static String stored(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LOCALE, ENGLISH);
    }
}
