package com.qron.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeacherDashboard extends AppCompatActivity {

    AutoCompleteTextView spinnerSubjects, spinnerCourse, spinnerSemester;
    ImageView qrImage;
    TextView tvTimer, tvConnectionStatus;
    Button btnGenerateQR, btnTeacherLogout, btnViewReports, btnManualAttendance;
    ImageButton btnProfile;
    SwipeRefreshLayout swipeRefresh;
    FusedLocationProviderClient fusedLocationClient;
    FirebaseFirestore fStore;
    FirebaseAuth fAuth;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "TeacherDashboard";

    private String currentSessionId = null;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        try {
            spinnerSubjects = findViewById(R.id.spinnerSubjects);
            spinnerCourse = findViewById(R.id.spinnerCourse);
            spinnerSemester = findViewById(R.id.spinnerSemester);
            qrImage = findViewById(R.id.qrImage);
            tvTimer = findViewById(R.id.tvTimer);
            tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
            btnGenerateQR = findViewById(R.id.btnGenerateQR);
            btnTeacherLogout = findViewById(R.id.btnTeacherLogout);
            btnViewReports = findViewById(R.id.btnViewReports);
            btnProfile = findViewById(R.id.btnProfile);
            swipeRefresh = findViewById(R.id.swipeRefresh);
            btnManualAttendance = findViewById(R.id.btnManualAttendance);

            fStore = FirebaseFirestore.getInstance();
            fAuth = FirebaseAuth.getInstance();
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

            setupConnectionStatusListener();

            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(() -> {
                loadData();
                new Handler().postDelayed(() -> swipeRefresh.setRefreshing(false), 2000);
            });

            if (btnProfile != null) {
                btnProfile.setOnClickListener(v -> startActivity(new Intent(TeacherDashboard.this, ProfileActivity.class)));
            }

            if (spinnerCourse != null && spinnerSemester != null && spinnerSubjects != null) {
                setupSpinners();
            }

            if (btnGenerateQR != null) {
                btnGenerateQR.setOnClickListener(v -> {
                    if (validateSelections()) {
                        showDurationSelectionDialog();
                    }
                });
            }

            if (btnViewReports != null) {
                btnViewReports.setOnClickListener(v -> startActivity(new Intent(TeacherDashboard.this, ViewStudentsActivity.class)));
            }

            if (btnManualAttendance != null) {
                btnManualAttendance.setOnClickListener(v -> {
                    if (currentSessionId != null) {
                        Intent intent = new Intent(TeacherDashboard.this, ManualAttendanceActivity.class);
                        intent.putExtra("course", spinnerCourse.getText().toString());
                        intent.putExtra("semester", spinnerSemester.getText().toString());
                        intent.putExtra("subject", spinnerSubjects.getText().toString());
                        intent.putExtra("sessionId", currentSessionId);
                        startActivity(intent);
                    }
                });
            }

            if (btnTeacherLogout != null) {
                btnTeacherLogout.setOnClickListener(v -> {
                    FirebaseUser user = fAuth.getCurrentUser();
                    String email = (user != null) ? user.getEmail() : "Unknown";
                    SheetLogger.logToSheet(email, "Logout", "Teacher logged out");

                    fAuth.signOut();
                    Intent intent = new Intent(TeacherDashboard.this, Login.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Initialization error: " + e.getMessage());
            Toast.makeText(this, "Dashboard Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupConnectionStatusListener() {
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class);
                if (connected) {
                    tvConnectionStatus.setText("🟢 Online");
                    tvConnectionStatus.setBackgroundColor(Color.parseColor("#4CAF50"));
                } else {
                    tvConnectionStatus.setText("🔴 Offline (Data Saved Locally)");
                    tvConnectionStatus.setBackgroundColor(Color.parseColor("#F44336"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Listener cancelled");
            }
        });
    }

    private void showDurationSelectionDialog() {
        String[] options = {"10 mins", "20 mins", "30 mins", "40 mins"};
        int[] values = {10, 20, 30, 40};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Class Duration");
        builder.setItems(options, (dialog, which) -> {
            int selectedDuration = values[which];
            getCurrentLocation(selectedDuration);
        });
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {}

    private void setupSpinners() {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            List<String> coursesList = new ArrayList<>();
            if (documentSnapshot.exists()) {
                Map<String, Object> data = documentSnapshot.getData();
                if (data != null) {
                    coursesList.addAll(data.keySet());
                }
            }
            ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, coursesList);
            spinnerCourse.setAdapter(courseAdapter);
        });

        spinnerCourse.setOnItemClickListener((parent, view, position, id) -> {
            updateSemesterSpinner(parent.getItemAtPosition(position).toString());
        });

        spinnerSemester.setOnItemClickListener((parent, view, position, id) -> {
            updateSubjectSpinner(spinnerCourse.getText().toString(), parent.getItemAtPosition(position).toString());
        });
    }

    private void updateSemesterSpinner(String courseName) {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long totalSems = documentSnapshot.getLong(courseName);
                if (totalSems != null) {
                    List<String> sems = new ArrayList<>();
                    for (int i = 1; i <= totalSems; i++) {
                        sems.add("Sem " + i);
                    }
                    ArrayAdapter<String> semesterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, sems);
                    spinnerSemester.setAdapter(semesterAdapter);
                    spinnerSemester.setText("", false);
                    spinnerSubjects.setText("", false);
                    btnManualAttendance.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateSubjectSpinner(String course, String semester) {
        fStore.collection("Curriculum").document("Subjects")
                .collection(course).document(semester).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> subjects = (List<String>) documentSnapshot.get("subjects");
                        if (subjects != null) {
                            ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, subjects);
                            spinnerSubjects.setAdapter(subjectAdapter);
                            spinnerSubjects.setText("", false);
                            btnManualAttendance.setVisibility(View.GONE);
                        }
                    } else {
                        spinnerSubjects.setAdapter(null);
                        spinnerSubjects.setText("", false);
                    }
                });
    }

    private boolean validateSelections() {
        if (spinnerCourse.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please select a Course", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (spinnerSemester.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please select a Semester", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (spinnerSubjects.getText().toString().isEmpty()) {
            Toast.makeText(this, "Please select a Subject", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void getCurrentLocation(int duration) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        checkActiveSessionAndHandleQR(location.getLatitude(), location.getLongitude(), duration);
                    } else {
                        Toast.makeText(TeacherDashboard.this, "Location null. Check GPS.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkActiveSessionAndHandleQR(final double latitude, final double longitude, int duration) {
        final String subject = spinnerSubjects.getText().toString();
        final String course = spinnerCourse.getText().toString();
        final String semester = spinnerSemester.getText().toString();
        final String activeSessionDocId = (course + "_" + semester + "_" + subject).replace(" ", "_");

        fStore.collection("ActiveSessions").document(activeSessionDocId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long savedTime = documentSnapshot.getLong("timestamp");
                        Integer savedDuration = documentSnapshot.get("duration", Integer.class);
                        if (savedDuration == null) savedDuration = 50; 

                        if (savedTime != null) {
                            long diffMillis = System.currentTimeMillis() - savedTime;
                            long durationMillis = (long) savedDuration * 60 * 1000;
                            
                            if (diffMillis < durationMillis) {
                                String savedQrData = documentSnapshot.getString("qrData");
                                if (savedQrData != null) {
                                    Toast.makeText(this, "Active session exists! Showing existing QR.", Toast.LENGTH_SHORT).show();
                                    currentSessionId = savedQrData.split(",")[0];
                                    btnManualAttendance.setVisibility(View.VISIBLE);
                                    displayQRCode(savedQrData);
                                    startTimer(durationMillis - diffMillis);
                                    return;
                                }
                            }
                        }
                    }
                    createNewSession(course, semester, subject, latitude, longitude, activeSessionDocId, duration);
                });
    }

    private void createNewSession(String course, String semester, String subject, double lat, double lon, String activeSessionId, int duration) {
        final String teacherEmail = fAuth.getCurrentUser().getEmail();
        long startTime = System.currentTimeMillis();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(startTime));
        
        Map<String, Object> session = new HashMap<>();
        session.put("subject", subject);
        session.put("course", course);
        session.put("semester", semester);
        session.put("teacherEmail", teacherEmail);
        session.put("startTime", startTime);
        session.put("date", dateStr); 
        session.put("duration", duration);
        session.put("timestamp", FieldValue.serverTimestamp());

        // Offline logic: Get doc ID immediately
        DocumentReference sessionRef = fStore.collection("class_sessions").document();
        currentSessionId = sessionRef.getId();
        sessionRef.set(session); // Will be queued locally if offline

        long timestamp = System.currentTimeMillis() / 1000;
        String qrData = currentSessionId + "," + lat + "," + lon + "," + timestamp;

        Map<String, Object> activeSession = new HashMap<>();
        activeSession.put("qrData", qrData);
        activeSession.put("timestamp", System.currentTimeMillis());
        activeSession.put("duration", duration);
        
        fStore.collection("ActiveSessions").document(activeSessionId).set(activeSession);
        
        btnManualAttendance.setVisibility(View.VISIBLE);
        displayQRCode(qrData);
        startTimer((long) duration * 60 * 1000);
        
        Toast.makeText(this, "QR Generated. Data will sync when online.", Toast.LENGTH_LONG).show();
        SheetLogger.logToSheet(teacherEmail, "Generate QR", "Teacher generated session ID: " + currentSessionId + " for " + subject + " (" + course + ")");
    }

    private void startTimer(long durationMillis) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        tvTimer.setVisibility(View.VISIBLE);
        tvTimer.setTextColor(getResources().getColor(R.color.colorPrimary));

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int minutes = (int) (millisUntilFinished / 1000) / 60;
                int seconds = (int) (millisUntilFinished / 1000) % 60;
                String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
                tvTimer.setText(timeLeftFormatted);
            }

            @Override
            public void onFinish() {
                tvTimer.setText("Session Expired");
                tvTimer.setTextColor(Color.RED);
                qrImage.setImageBitmap(null);
            }
        }.start();
    }

    private void displayQRCode(String qrData) {
        MultiFormatWriter writer = new MultiFormatWriter();
        try {
            BitMatrix matrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 400, 400);
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.createBitmap(matrix);
            qrImage.setImageBitmap(bitmap);
        } catch (WriterException e) {
            Log.e(TAG, "QR Error: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted. Click Generate QR again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
