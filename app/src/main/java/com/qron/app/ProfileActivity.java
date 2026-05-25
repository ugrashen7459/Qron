package com.qron.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileDebug";
    TextView profileName, profileEmail, profilePhone, profileCourse, profileSemester, profileRegNo;
    LinearLayout layoutCourse, layoutSemester, layoutRegNo;
    Button btnChangePassword, btnLogout;
    ImageButton btnBack;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        profileName = findViewById(R.id.profileName);
        profileRegNo = findViewById(R.id.profileRegNo);
        profileEmail = findViewById(R.id.profileEmail);
        profilePhone = findViewById(R.id.profilePhone);
        profileCourse = findViewById(R.id.profileCourse);
        profileSemester = findViewById(R.id.profileSemester);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnLogout = findViewById(R.id.btnLogout);
        
        layoutRegNo = findViewById(R.id.layoutRegNo);
        layoutCourse = findViewById(R.id.layoutCourse);
        layoutSemester = findViewById(R.id.layoutSemester);
        btnBack = findViewById(R.id.btnBack);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v -> {
            String email = profileEmail.getText().toString();
            showPasswordResetDialog(email);
        });

        btnLogout.setOnClickListener(v -> {
            fAuth.signOut();
            Intent intent = new Intent(ProfileActivity.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        loadUserProfile();
    }

    private void showPasswordResetDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage("Send password reset link to " + email + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    fAuth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(unused -> Toast.makeText(ProfileActivity.this, "Reset link sent to your email. Please check your inbox.", Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> Toast.makeText(ProfileActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String userId = currentUser.getUid();
        DocumentReference df = fStore.collection("users").document(userId);
        df.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Log.d(TAG, "Snapshot: " + documentSnapshot.getData());
                
                profileName.setText(documentSnapshot.getString("fullName"));
                profileEmail.setText(documentSnapshot.getString("email"));
                profilePhone.setText(documentSnapshot.getString("phone"));

                String role = documentSnapshot.getString("role");

                if ("Student".equalsIgnoreCase(role)) {
                    layoutRegNo.setVisibility(View.VISIBLE);
                    layoutCourse.setVisibility(View.VISIBLE);
                    layoutSemester.setVisibility(View.VISIBLE);
                    
                    String regNo = String.valueOf(documentSnapshot.get("regNo"));
                    if (regNo == null || regNo.equals("null") || regNo.isEmpty()) {
                        regNo = "N/A";
                    }
                    profileRegNo.setText("Reg No: " + regNo);
                    
                    profileCourse.setText(documentSnapshot.getString("course"));
                    profileSemester.setText(documentSnapshot.getString("semester"));
                } else {
                    layoutRegNo.setVisibility(View.GONE);
                    layoutCourse.setVisibility(View.GONE);
                    layoutSemester.setVisibility(View.GONE);
                }
            }
        }).addOnFailureListener(e -> Toast.makeText(ProfileActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}