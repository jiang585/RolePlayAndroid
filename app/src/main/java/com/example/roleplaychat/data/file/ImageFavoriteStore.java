package com.example.roleplaychat.data.file;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Stores favorites without a database migration, preserving existing scripts and chats. */
public final class ImageFavoriteStore {
    private static final String PREFS = "image_favorites";
    private static final String KEY_PATHS = "paths";
    private final SharedPreferences preferences;

    public ImageFavoriteStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean isFavorite(String localPath) {
        return localPath != null && paths().contains(localPath);
    }

    public synchronized boolean toggle(String localPath) {
        if (localPath == null || localPath.trim().isEmpty()) return false;
        Set<String> values = paths();
        boolean added = !values.contains(localPath);
        if (added) values.add(localPath); else values.remove(localPath);
        preferences.edit().putStringSet(KEY_PATHS, values).apply();
        return added;
    }

    public synchronized Set<String> all() {
        return paths();
    }

    private Set<String> paths() {
        return new HashSet<>(preferences.getStringSet(KEY_PATHS, Collections.emptySet()));
    }
}
