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

        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                // Authenticate User
                fAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        checkUserRole(authResult.getUser().getUid());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(Login.this, "Error! " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void checkUserRole(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        DocumentReference df = fStore.collection("users").document(uid);
        df.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                if (documentSnapshot.exists()) {
                    String role = documentSnapshot.getString("role");
                    String storedDeviceId = documentSnapshot.getString("deviceId");
                    String currentDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                    if (role != null) {
                        // Device Lock Feature for Students
                        if (role.equals("Student")) {
                            if (storedDeviceId == null || storedDeviceId.isEmpty()) {
                                // Case A: First Time Login - Register device
                                df.update("deviceId", currentDeviceId);
                            } else if (!storedDeviceId.equals(currentDeviceId)) {
                                // Case C: Different Device - Block Login
                                Toast.makeText(Login.this, "Login Failed! You are registered on another device. Please use your own phone.", Toast.LENGTH_LONG).show();
                                fAuth.signOut();
                                progressBar.setVisibility(View.GONE);
                                return;
                            }
                            // Case B: Same Device - Allow Login (fall through to dashboard)
                        }

                        String email = fAuth.getCurrentUser() != null ? fAuth.getCurrentUser().getEmail() : "Unknown";
                        if (role.equals("Admin")) {
                            SheetLogger.logToSheet(email, "Login", "Admin logged in");
                        }

                        Intent intent;
                        if (role.equals("Admin")) {
                            intent = new Intent(getApplicationContext(), AdminDashboard.class);
                        } else if (role.equals("Teacher")) {
                            intent = new Intent(getApplicationContext(), TeacherDashboard.class);
                        } else if (role.equals("Student")) {
                            intent = new Intent(getApplicationContext(), StudentDashboard.class);
                        } else {
                            Toast.makeText(Login.this, "Unknown role: " + role, Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(Login.this, "Role field missing", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(Login.this, "User document does not exist", Toast.LENGTH_SHORT).show();
                }
                progressBar.setVisibility(View.GONE);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(Login.this, "Failed to fetch user role: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }
}