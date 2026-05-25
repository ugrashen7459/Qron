package com.qron.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class Login extends AppCompatActivity {

    EditText loginEmail, loginPassword;
    Button loginBtn;
    ProgressBar progressBar;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            checkUserRole(FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        loginEmail = findViewById(R.id.loginEmail);
        loginPassword = findViewById(R.id.loginPassword);
        loginBtn = findViewById(R.id.loginBtn);
        progressBar = findViewById(R.id.progressBar);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        loginBtn.setOnClickListener(v -> {
            String email = loginEmail.getText().toString().trim();
            String password = loginPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                loginEmail.setError("Email is required.");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                loginPassword.setError("Password is required.");
                return;
            }

            progressBar.setVisibility(View.VISIBLE);

            fAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> checkUserRole(result.getUser().getUid()))
                .addOnFailureListener(e -> {
                    Toast.makeText(Login.this, "Error! " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
        });
    }

    private void checkUserRole(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        DocumentReference dr = fStore.collection("users").document(uid);
        dr.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String role = doc.getString("role");
                String devId = doc.getString("deviceId");
                String currentId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                if (role != null) {
                    if ("Student".equals(role)) {
                        if (TextUtils.isEmpty(devId)) {
                            dr.update("deviceId", currentId);
                        } else if (!devId.equals(currentId)) {
                            Toast.makeText(Login.this, getString(R.string.device_mismatch), Toast.LENGTH_LONG).show();
                            fAuth.signOut();
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                    }

                    String email = fAuth.getCurrentUser() != null ? fAuth.getCurrentUser().getEmail() : "Unknown";
                    if ("Admin".equals(role)) SheetLogger.logToSheet(email, "Login", "Admin logged in");

                    Intent intent;
                    switch (role) {
                        case "Admin": intent = new Intent(this, AdminDashboard.class); break;
                        case "Teacher": intent = new Intent(this, TeacherDashboard.class); break;
                        case "Student": intent = new Intent(this, StudentDashboard.class); break;
                        default:
                            Toast.makeText(this, "Unknown role: " + role, Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            return;
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
            progressBar.setVisibility(View.GONE);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
        });
    }
}