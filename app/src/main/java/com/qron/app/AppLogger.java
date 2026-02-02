package com.qron.app;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AppLogger {

    public static void logAction(String action, String details) {
        FirebaseFirestore fStore = FirebaseFirestore.getInstance();
        FirebaseAuth fAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = fAuth.getCurrentUser();

        String email = (currentUser != null) ? currentUser.getEmail() : "Anonymous";
        long timestamp = System.currentTimeMillis();
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = sdf.format(new Date(timestamp));

        Map<String, Object> log = new HashMap<>();
        log.put("timestamp", timestamp);
        log.put("email", email);
        log.put("action", action);
        log.put("details", details);

        fStore.collection("SystemLogs")
                .document(dateKey)
                .collection("Logs")
                .add(log);
    }
}
