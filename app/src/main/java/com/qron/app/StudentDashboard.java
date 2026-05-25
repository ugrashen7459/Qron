package com.qron.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentDashboard extends AppCompatActivity {

    private static final int LOCATION_REQ_CODE = 123;
    private static final float MAX_ALLOWED_DISTANCE = 100.0f;
    private static final String TAG = "StudentDashboard";
    TextView txtStudentName, txtStatus;
    Button btnScanQR;
    ImageButton btnProfile;
    RecyclerView attendanceRecyclerView;
    StudentSubjectAdapter subjectAdapter;
    SwipeRefreshLayout swipeRefresh;
    FusedLocationProviderClient fusedLocationClient;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String studentName, myCourse, mySemester;
    private String pendingQrData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        txtStudentName = findViewById(R.id.txtStudentName);
        txtStatus = findViewById(R.id.txtStatus);
        btnScanQR = findViewById(R.id.btnScanQR);
        btnProfile = findViewById(R.id.btnProfile);
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> {
            loadData();
            new Handler().postDelayed(() -> swipeRefresh.setRefreshing(false), 2000);
        });

        btnScanQR.setOnClickListener(v -> {
            if (myCourse == null || mySemester == null) {
                Toast.makeText(StudentDashboard.this, getString(R.string.profile_incomplete), Toast.LENGTH_SHORT).show();
                return;
            }
            startScanning();
        });

        btnProfile.setOnClickListener(v -> startActivity(new Intent(StudentDashboard.this, ProfileActivity.class)));

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser == null) return;

        fStore.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        studentName = doc.getString("fullName");
                        myCourse = doc.getString("course");
                        mySemester = doc.getString("semester");
                        txtStudentName.setText(getString(R.string.welcome) + ", " + studentName);
                        loadAttendanceSummary();
                    }
                });
    }

    private void loadAttendanceSummary() {
        if (myCourse == null || mySemester == null) return;

        final String email = fAuth.getCurrentUser().getEmail();

        fStore.collection("class_sessions")
                .whereEqualTo("course", myCourse.trim())
                .whereEqualTo("semester", mySemester.trim())
                .get()
                .addOnSuccessListener(totalClassesSnapshot -> {
                    Map<String, Integer> totalClassesPerSubject = new HashMap<>();
                    for (DocumentSnapshot doc : totalClassesSnapshot.getDocuments()) {
                        String subject = doc.getString("subject");
                        if (subject != null) {
                            totalClassesPerSubject.put(subject.trim(), totalClassesPerSubject.getOrDefault(subject.trim(), 0) + 1);
                        }
                    }

                    fStore.collection("attendance")
                            .whereEqualTo("studentEmail", email)
                            .get()
                            .addOnSuccessListener(attendanceSnapshot -> {
                                Map<String, Integer> attendedClassesPerSubject = new HashMap<>();
                                for (DocumentSnapshot doc : attendanceSnapshot.getDocuments()) {
                                    String subject = doc.getString("subject");
                                    if (subject != null) {
                                        attendedClassesPerSubject.put(subject.trim(), attendedClassesPerSubject.getOrDefault(subject.trim(), 0) + 1);
                                    }
                                }

                                displaySummary(totalClassesPerSubject, attendedClassesPerSubject);
                            });
                });
    }

    private void displaySummary(Map<String, Integer> totalMap, Map<String, Integer> attendedMap) {
        List<StudentSubjectAdapter.AttendanceItem> summaryList = new ArrayList<>();

        for (String subject : totalMap.keySet()) {
            int total = totalMap.get(subject);
            int attended = attendedMap.getOrDefault(subject, 0);
            summaryList.add(new StudentSubjectAdapter.AttendanceItem(subject, attended, total));
        }

        if (subjectAdapter == null) {
            subjectAdapter = new StudentSubjectAdapter(summaryList);
            attendanceRecyclerView.setAdapter(subjectAdapter);
        } else {
            subjectAdapter.setList(summaryList);
        }
    }

    private void startScanning() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt(getString(R.string.scan_attendance));
        integrator.setOrientationLocked(true);
        integrator.setCaptureActivity(CaptureAct.class);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                handleScanResult(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleScanResult(String contents) {
        pendingQrData = contents;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQ_CODE);
        } else {
            processAttendance(pendingQrData);
            pendingQrData = null;
        }
    }

    private void processAttendance(String contents) {
        try {
            String[] parts = contents.split(",");
            if (parts.length < 4) {
                Toast.makeText(this, getString(R.string.invalid_qr), Toast.LENGTH_SHORT).show();
                return;
            }

            final String sessionId = parts[0];
            final double teacherLat = Double.parseDouble(parts[1]);
            final double teacherLon = Double.parseDouble(parts[2]);
            
            final String qrSubject = (parts.length > 4) ? parts[4] : "Unknown";
            final String qrCourse = (parts.length > 5) ? parts[5] : null;
            final String qrSemester = (parts.length > 6) ? parts[6] : null;

            fStore.collection("class_sessions").document(sessionId).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            DocumentSnapshot doc = task.getResult();
                            String course = doc.getString("course");
                            String sem = doc.getString("semester");

                            if (course == null || !course.trim().equalsIgnoreCase(myCourse.trim()) ||
                                sem == null || !sem.trim().equalsIgnoreCase(mySemester.trim())) {
                                Toast.makeText(this, getString(R.string.wrong_class_expected, myCourse, mySemester), Toast.LENGTH_LONG).show();
                                return;
                            }

                            Long start = doc.getLong("startTime");
                            int duration = doc.get("duration", Integer.class) != null ? doc.get("duration", Integer.class) : 50;

                            if (start != null && (System.currentTimeMillis() - start) <= (long) duration * 60 * 1000) {
                                validateAndMark(sessionId, doc.getString("subject"), teacherLat, teacherLon, course, sem);
                            } else {
                                Toast.makeText(this, getString(R.string.qr_expired), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            validateAndMark(sessionId, qrSubject, teacherLat, teacherLon, qrCourse, qrSemester);
                        }
                    });

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.scan_error, e.getLocalizedMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void validateAndMark(String sessionId, String subject, double tLat, double tLon, String course, String sem) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        checkRange(loc, tLat, tLon, sessionId, subject, course, sem);
                    } else {
                        fallbackGPS(tLat, tLon, sessionId, subject, course, sem);
                    }
                })
                .addOnFailureListener(e -> fallbackGPS(tLat, tLon, sessionId, subject, course, sem));
    }

    private void fallbackGPS(double tLat, double tLon, String sId, String sub, String c, String sem) {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) {
                    checkRange(last, tLat, tLon, sId, sub, c, sem);
                } else {
                    Toast.makeText(this, getString(R.string.waiting_gps), Toast.LENGTH_SHORT).show();
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location l) {
                            checkRange(l, tLat, tLon, sId, sub, c, sem);
                        }
                        @Override public void onStatusChanged(String p, int s, Bundle e) {}
                        @Override public void onProviderEnabled(@NonNull String p) {}
                        @Override public void onProviderDisabled(@NonNull String p) {}
                    }, null);
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.gps_required), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkRange(Location sLoc, double tLat, double tLon, String sId, String sub, String c, String sem) {
        float[] res = new float[1];
        Location.distanceBetween(tLat, tLon, sLoc.getLatitude(), sLoc.getLongitude(), res);
        if (res[0] <= MAX_ALLOWED_DISTANCE) {
            checkDuplicateAndMark(sId, sub, c, sem);
        } else {
            txtStatus.setText(getString(R.string.too_far) + " (" + (int)res[0] + "m)");
            Toast.makeText(this, getString(R.string.too_far_distance, (int)res[0]), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingQrData != null) {
                    processAttendance(pendingQrData);
                    pendingQrData = null;
                }
            } else {
                Toast.makeText(this, "Location permission required for attendance verification.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkDuplicateAndMark(final String sessionId, final String subject, final String sessionCourse, final String sessionSemester) {
        final String email = fAuth.getCurrentUser().getEmail();
        final String uid = fAuth.getCurrentUser().getUid();

        fStore.collection("attendance")
                .whereEqualTo("studentEmail", email)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        Toast.makeText(StudentDashboard.this, getString(R.string.attendance_already_marked), Toast.LENGTH_SHORT).show();
                    } else {
                        saveAttendanceData(sessionId, subject, uid, email, sessionCourse, sessionSemester);
                    }
                });
    }

    private void saveAttendanceData(String sessionId, String subject, String uid, String email, String sessionCourse, String sessionSemester) {
        long timestamp = System.currentTimeMillis();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));

        Map<String, Object> attendance = new HashMap<>();
        attendance.put("studentName", studentName);
        attendance.put("studentId", uid);
        attendance.put("studentEmail", email);
        attendance.put("subject", subject != null ? subject.trim() : "Unknown");
        attendance.put("course", (sessionCourse != null) ? sessionCourse.trim() : (myCourse != null ? myCourse.trim() : ""));
        attendance.put("semester", (sessionSemester != null) ? sessionSemester.trim() : (mySemester != null ? mySemester.trim() : ""));
        attendance.put("sessionId", sessionId);
        attendance.put("date", date);
        attendance.put("time", time);
        attendance.put("timestamp", timestamp);
        attendance.put("status", "Present");

        fStore.collection("attendance").add(attendance);

        txtStatus.setText("Status: Attendance Marked for " + subject);

        if (NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(StudentDashboard.this, getString(R.string.attendance_marked) + " for " + subject, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(StudentDashboard.this, getString(R.string.sync_offline), Toast.LENGTH_LONG).show();
        }

        SheetLogger.logToSheet(email, "Scan Success", "Student scanned QR for " + subject + " (Session: " + sessionId + ")");
        loadAttendanceSummary();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


}
