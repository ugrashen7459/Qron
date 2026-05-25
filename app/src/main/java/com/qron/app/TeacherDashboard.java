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
    TextView tvTimer;
    Button btnGenerateQR, btnViewReports, btnManualAttendance;
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
            btnGenerateQR = findViewById(R.id.btnGenerateQR);
            btnViewReports = findViewById(R.id.btnViewReports);
            btnProfile = findViewById(R.id.btnProfile);
            swipeRefresh = findViewById(R.id.swipeRefresh);
            btnManualAttendance = findViewById(R.id.btnManualAttendance);

            fStore = FirebaseFirestore.getInstance();
            fAuth = FirebaseAuth.getInstance();
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.dashboard_error, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
        }
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
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<String> subjects = (List<String>) doc.get("subjects");
                        if (subjects != null) {
                            spinnerSubjects.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, subjects));
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
                .addOnSuccessListener(this, loc -> {
                    if (loc != null) {
                        checkSession(loc.getLatitude(), loc.getLongitude(), duration);
                    } else {
                        Toast.makeText(this, "Location error. Check GPS.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkSession(final double lat, final double lon, int duration) {
        final String sub = spinnerSubjects.getText().toString();
        final String course = spinnerCourse.getText().toString();
        final String sem = spinnerSemester.getText().toString();
        final String sessionId = (course + "_" + sem + "_" + sub).replace(" ", "_");

        fStore.collection("ActiveSessions").document(sessionId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long savedTime = doc.getLong("timestamp");
                        int dur = doc.get("duration", Integer.class) != null ? doc.get("duration", Integer.class) : 50;

                        if (savedTime != null) {
                            long diff = System.currentTimeMillis() - savedTime;
                            long max = (long) dur * 60 * 1000;
                            
                            if (diff < max) {
                                String qrData = doc.getString("qrData");
                                if (qrData != null && qrData.split(",").length >= 7) {
                                    Toast.makeText(this, getString(R.string.session_active), Toast.LENGTH_SHORT).show();
                                    currentSessionId = qrData.split(",")[0];
                                    btnManualAttendance.setVisibility(View.VISIBLE);
                                    displayQRCode(qrData);
                                    startTimer(max - diff);
                                    return;
                                }
                            }
                        }
                    }
                    createSession(course, sem, sub, lat, lon, sessionId, duration);
                });
    }

    private void createSession(String course, String sem, String sub, double lat, double lon, String activeId, int dur) {
        final String teacherEmail = fAuth.getCurrentUser().getEmail();
        long now = System.currentTimeMillis();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(now));
        
        Map<String, Object> session = new HashMap<>();
        session.put("subject", sub);
        session.put("course", course);
        session.put("semester", sem);
        session.put("teacherEmail", teacherEmail);
        session.put("startTime", now);
        session.put("date", dateStr); 
        session.put("duration", dur);
        session.put("timestamp", FieldValue.serverTimestamp());

        DocumentReference sessionRef = fStore.collection("class_sessions").document();
        currentSessionId = sessionRef.getId();
        sessionRef.set(session); 

        String qr = currentSessionId + "," + lat + "," + lon + "," + (now / 1000) + "," + sub + "," + course + "," + sem;

        Map<String, Object> active = new HashMap<>();
        active.put("qrData", qr);
        active.put("timestamp", now);
        active.put("duration", dur);
        
        fStore.collection("ActiveSessions").document(activeId).set(active);
        
        btnManualAttendance.setVisibility(View.VISIBLE);
        displayQRCode(qr);
        startTimer((long) dur * 60 * 1000);
        
        Toast.makeText(this, NetworkUtils.isNetworkAvailable(this) ? 
                getString(R.string.qr_gen_success) : getString(R.string.gen_offline), Toast.LENGTH_SHORT).show();

        SheetLogger.logToSheet(teacherEmail, "Generate QR", "Session: " + currentSessionId + " [" + sub + "]");
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
