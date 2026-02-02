package com.qron.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RegisterUser extends AppCompatActivity {

    private static final int PICK_CSV_FILE = 1;
    EditText regFullName, regEmail, regPassword, regPhone, regRegNo;
    Spinner spinnerCourse, spinnerSemester;
    Button registerBtn, btnBulkUpload;
    ProgressBar progressBar;
    TextView tvProgress;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user);

        regFullName = findViewById(R.id.regFullName);
        regRegNo = findViewById(R.id.regRegNo);
        regEmail = findViewById(R.id.regEmail);
        regPassword = findViewById(R.id.regPassword);
        regPhone = findViewById(R.id.regPhone);
        spinnerCourse = findViewById(R.id.spinnerCourse);
        spinnerSemester = findViewById(R.id.spinnerSemester);
        registerBtn = findViewById(R.id.registerBtn);
        btnBulkUpload = findViewById(R.id.btnBulkUpload);
        progressBar = findViewById(R.id.registerProgress);
        tvProgress = findViewById(R.id.tvProgress);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        role = getIntent().getStringExtra("role");

        if ("Student".equalsIgnoreCase(role)) {
            spinnerCourse.setVisibility(View.VISIBLE);
            spinnerSemester.setVisibility(View.VISIBLE);
            regRegNo.setVisibility(View.VISIBLE);
            btnBulkUpload.setVisibility(View.VISIBLE);
            registerBtn.setText("Register Student");
            setupCascadingSpinners();
        } else {
            regRegNo.setVisibility(View.GONE);
            btnBulkUpload.setVisibility(View.VISIBLE); // Enabled for Teacher too
            registerBtn.setText("Register Teacher");
            spinnerCourse.setVisibility(View.GONE);
            spinnerSemester.setVisibility(View.GONE);
        }

        registerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String fullName = regFullName.getText().toString().trim();
                final String email = regEmail.getText().toString().trim();
                String password = regPassword.getText().toString().trim();
                final String phone = regPhone.getText().toString().trim();
                final String regNo = regRegNo.getText().toString().trim();
                final String selectedCourse = spinnerCourse.getSelectedItem() != null ? spinnerCourse.getSelectedItem().toString().trim() : "";
                final String selectedSemester = spinnerSemester.getSelectedItem() != null ? spinnerSemester.getSelectedItem().toString().trim() : "";

                if (TextUtils.isEmpty(fullName)) {
                    regFullName.setError("Full Name is required.");
                    return;
                }
                if ("Student".equalsIgnoreCase(role) && TextUtils.isEmpty(regNo)) {
                    regRegNo.setError("Registration Number is required.");
                    return;
                }
                if (TextUtils.isEmpty(email)) {
                    regEmail.setError("Email is required.");
                    return;
                }
                if (TextUtils.isEmpty(password)) {
                    regPassword.setError("Password is required.");
                    return;
                }
                if (password.length() < 6) {
                    regPassword.setError("Password must be at least 6 characters.");
                    return;
                }

                if ("Student".equalsIgnoreCase(role)) {
                    if (spinnerCourse.getSelectedItemPosition() <= 0) {
                        Toast.makeText(RegisterUser.this, "Please select a Course", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (spinnerSemester.getSelectedItemPosition() <= 0) {
                        Toast.makeText(RegisterUser.this, "Please select a Semester", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);

                createAccount(fullName, email, password, phone, regNo, selectedCourse, selectedSemester);
            }
        });

        btnBulkUpload.setOnClickListener(v -> {
            if ("Student".equalsIgnoreCase(role)) {
                if (spinnerCourse.getSelectedItemPosition() <= 0 || spinnerSemester.getSelectedItemPosition() <= 0) {
                    Toast.makeText(this, "Please select Course and Semester first", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");
            startActivityForResult(Intent.createChooser(intent, "Select CSV File"), PICK_CSV_FILE);
        });
    }

    private void createAccount(String fullName, String email, String password, String phone, String regNo, String selectedCourse, String selectedSemester) {
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance("Secondary");
        } catch (IllegalStateException e) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey("AIzaSyBXQo4oonXZ1ddkTmgDH_5BLm8_LVsvark")
                    .setApplicationId("1:487168248961:android:1143a3a88fd55159e55180")
                    .setProjectId("smartattendance-e0cc2")
                    .build();
            secondaryApp = FirebaseApp.initializeApp(getApplicationContext(), options, "Secondary");
        }

        FirebaseAuth tempAuth = FirebaseAuth.getInstance(secondaryApp);

        tempAuth.createUserWithEmailAndPassword(email, password).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
            @Override
            public void onSuccess(AuthResult authResult) {
                String uid = authResult.getUser().getUid();
                DocumentReference df = fStore.collection("users").document(uid);
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("fullName", fullName);
                userInfo.put("email", email);
                userInfo.put("phone", phone);
                userInfo.put("role", role);

                if ("Student".equalsIgnoreCase(role)) {
                    userInfo.put("course", selectedCourse);
                    userInfo.put("semester", selectedSemester);
                    userInfo.put("regNo", regNo);
                }

                df.set(userInfo).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        if (tvProgress.getVisibility() != View.VISIBLE) {
                            Toast.makeText(RegisterUser.this, "User Created Successfully", Toast.LENGTH_SHORT).show();
                            regFullName.setText("");
                            regRegNo.setText("");
                            regEmail.setText("");
                            regPassword.setText("");
                            regPhone.setText("");
                            if ("Student".equalsIgnoreCase(role)) {
                                spinnerCourse.setSelection(0);
                                spinnerSemester.setSelection(0);
                            }
                        }
                        tempAuth.signOut();
                        if (tvProgress.getVisibility() != View.VISIBLE) {
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(RegisterUser.this, "Failed to create Firestore entry: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(RegisterUser.this, "Failed to create account: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_FILE && resultCode == RESULT_OK && data != null) {
            handleBulkUpload(data.getData());
        }
    }

    private void handleBulkUpload(Uri uri) {
        final String selectedCourse = spinnerCourse.getSelectedItem() != null ? spinnerCourse.getSelectedItem().toString().trim() : "";
        final String selectedSemester = spinnerSemester.getSelectedItem() != null ? spinnerSemester.getSelectedItem().toString().trim() : "";

        List<String[]> dataRows = new ArrayList<>();
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.toLowerCase().contains("name") || line.toLowerCase().contains("email")) {
                        continue;
                    }
                }

                String[] tokens = line.split(",");
                if (tokens.length >= 5) {
                    dataRows.add(tokens);
                }
            }
            reader.close();
        } catch (Exception e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (dataRows.isEmpty()) {
            Toast.makeText(this, "No valid data found in CSV", Toast.LENGTH_SHORT).show();
            return;
        }

        tvProgress.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(false);
        progressBar.setMax(dataRows.size());
        progressBar.setProgress(0);

        AtomicInteger completedCount = new AtomicInteger(0);
        int total = dataRows.size();

        for (String[] row : dataRows) {
            String name = row[0].trim();
            String regNo = row[1].trim();
            String email = row[2].trim();
            String mobile = row[3].trim();
            String password = row[4].trim();

            FirebaseApp secondaryApp;
            try {
                secondaryApp = FirebaseApp.getInstance("Secondary");
            } catch (IllegalStateException e) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey("AIzaSyBXQo4oonXZ1ddkTmgDH_5BLm8_LVsvark")
                        .setApplicationId("1:487168248961:android:1143a3a88fd55159e55180")
                        .setProjectId("smartattendance-e0cc2")
                        .build();
                secondaryApp = FirebaseApp.initializeApp(getApplicationContext(), options, "Secondary");
            }
            FirebaseAuth tempAuth = FirebaseAuth.getInstance(secondaryApp);

            tempAuth.createUserWithEmailAndPassword(email, password).addOnSuccessListener(authResult -> {
                String uid = authResult.getUser().getUid();
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("fullName", name);
                userInfo.put("email", email);
                userInfo.put("phone", mobile);
                userInfo.put("role", role); // Dynamic based on current role (Student or Teacher)

                if ("Student".equalsIgnoreCase(role)) {
                    userInfo.put("course", selectedCourse);
                    userInfo.put("semester", selectedSemester);
                    userInfo.put("regNo", regNo);
                }

                fStore.collection("users").document(uid).set(userInfo).addOnCompleteListener(task -> {
                    int current = completedCount.incrementAndGet();
                    progressBar.setProgress(current);
                    tvProgress.setText("Uploading: " + current + "/" + total);
                    tempAuth.signOut();
                    if (current == total) {
                        Toast.makeText(RegisterUser.this, "Bulk upload completed successfully!", Toast.LENGTH_LONG).show();
                        tvProgress.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                    }
                });
            }).addOnFailureListener(e -> {
                int current = completedCount.incrementAndGet();
                progressBar.setProgress(current);
                tvProgress.setText("Uploading: " + current + "/" + total);
                if (current == total) {
                    Toast.makeText(RegisterUser.this, "Bulk upload completed with some errors.", Toast.LENGTH_LONG).show();
                    tvProgress.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                }
            });
        }
    }

    private void setupCascadingSpinners() {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            List<String> coursesList = new ArrayList<>();
            coursesList.add("Select Course");
            if (documentSnapshot.exists()) {
                Map<String, Object> data = documentSnapshot.getData();
                if (data != null) {
                    coursesList.addAll(data.keySet());
                }
            }
            ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, coursesList);
            courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCourse.setAdapter(courseAdapter);
        });

        spinnerCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    updateSemesterSpinner(parent.getItemAtPosition(position).toString());
                } else {
                    spinnerSemester.setAdapter(null);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSemesterSpinner(String courseName) {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long totalSems = documentSnapshot.getLong(courseName);
                if (totalSems != null) {
                    List<String> sems = new ArrayList<>();
                    sems.add("Select Semester");
                    for (int i = 1; i <= totalSems; i++) {
                        sems.add("Sem " + i);
                    }
                    ArrayAdapter<String> semAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sems);
                    semAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerSemester.setAdapter(semAdapter);
                }
            }
        });
    }
}
