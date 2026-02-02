package com.qron.app;

import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.IOException;

public class SheetLogger {
    private static final String SHEET_URL = "https://script.google.com/macros/s/AKfycbzKS1uH4h7N4TRIm1Xdo_BwMAMWJkx6fDvb_I9n-2jMMEyThhUWMJDORSJ2IRZenL1FVg/exec";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void logToSheet(String email, String action, String details) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("action", action);
            json.put("details", details);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SHEET_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("SheetLogger", "Failed to log to sheet: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        Log.d("SheetLogger", "Log sent successfully");
                    } else {
                        Log.e("SheetLogger", "Server error: " + response.code());
                    }
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.e("SheetLogger", "Error creating JSON: " + e.getMessage());
        }
    }
}
