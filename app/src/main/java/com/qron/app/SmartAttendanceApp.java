package com.qron.app;

import android.app.Application;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class SmartAttendanceApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        try {
            // Explicitly use the URL from your google-services.json
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://smartattendance-e0cc2-default-rtdb.firebaseio.com");
            database.setPersistenceEnabled(true);
            database.getReference(".info/connected").keepSynced(true);
        } catch (Exception e) {
            Log.e("SmartAttendanceApp", "RTDB initialization error: " + e.getMessage());
        }

        try {
            FirebaseFirestore fStore = FirebaseFirestore.getInstance();
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            fStore.setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.e("SmartAttendanceApp", "Firestore initialization error: " + e.getMessage());
        }
    }
}
