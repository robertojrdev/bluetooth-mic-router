package com.btmicfix;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import java.util.ArrayList;
import java.util.List;

/** Only user-selected duration and headset identity are saved, privately in this app. */
final class RoutingSettings {
    private final SharedPreferences preferences;

    RoutingSettings(Context context) {
        preferences = context.getSharedPreferences("routing_settings", Context.MODE_PRIVATE);
    }

    int minutes() { return SessionDeadline.validMinutes(preferences.getInt("minutes", 5)); }
    boolean automatic() { return preferences.getBoolean("automatic", true); }
    void automatic(boolean value) { preferences.edit().putBoolean("automatic", value).apply(); }
    void minutes(int value) {
        preferences.edit().putInt("minutes", SessionDeadline.validMinutes(value)).apply();
    }
    String headsetKey() { return preferences.getString("headset_key", ""); }
    String headsetName() { return preferences.getString("headset_name", ""); }
    void headset(AudioDeviceInfo device) {
        preferences.edit().putString("headset_key", device == null ? "" : key(device))
                .putString("headset_name", device == null ? "" : device.getProductName().toString()).apply();
    }

    static String key(AudioDeviceInfo device) {
        String address = device.getAddress();
        return device.getType() + ":" + (address.isEmpty() ? device.getProductName().toString() : address);
    }

    List<AudioDeviceInfo> candidates(List<AudioDeviceInfo> available) {
        List<AudioDeviceInfo> matches = new ArrayList<>();
        String preferred = headsetKey();
        for (AudioDeviceInfo device : available) {
            if (RoutingService.isHeadset(device)
                    && (preferred.isEmpty() || preferred.equals(key(device)))) matches.add(device);
        }
        return matches;
    }

    static String durationLabel(int minutes) {
        return minutes == 0 ? "Until I stop" : minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
