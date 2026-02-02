package com.qron.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    TextView txtStudentName, txtStatus, tvConnectionStatus;
    Button btnScanQR, btnStudentLogout;
    ImageButton btnProfile;
    ListView attendanceList;
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
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        btnScanQR = findViewById(R.id.btnScanQR);
        btnStudentLogout = findViewById(R.id.btnStudentLogout);
        btnProfile = findViewById(R.id.btnProfile);
        attendanceList = findViewById(R.id.attendanceList);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupConnectionStatusListener();

        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> {
            loadData();
            new Handler().postDelayed(() -> swipeRefresh.setRefreshing(false), 2000);
        });

        btnScanQR.setOnClickListener(v -> {
            if (myCourse == null || mySemester == null) {
                Toast.makeText(StudentDashboard.this, "Profile incomplete. Please contact Admin.", Toast.LENGTH_SHORT).show();
                return;
            }
            startScanning();
        });

        btnProfile.setOnClickListener(v -> startActivity(new Intent(StudentDashboard.this, ProfileActivity.class)));

        btnStudentLogout.setOnClickListener(v -> {
            FirebaseUser user = fAuth.getCurrentUser();
            String email = (user != null) ? user.getEmail() : "Unknown";
            SheetLogger.logToSheet(email, "Logout", "Student logged out");

            fAuth.signOut();
            Intent intent = new Intent(StudentDashboard.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
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

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        fetchStudentProfile();
    }

    private void fetchStudentProfile() {
        String uid = fAuth.getCurrentUser().getUid();
        DocumentReference df = fStore.collection("users").document(uid);
        df.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                studentName = documentSnapshot.getString("fullName");
                myCourse = documentSnapshot.getString("course");
                mySemester = documentSnapshot.getString("semester");
                txtStudentName.setText("Welcome, " + studentName);
                
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
        List<AttendanceItem> summaryList = new ArrayList<>();
        
        for (String subject : totalMap.keySet()) {
            int total = totalMap.get(subject);
            int attended = attendedMap.getOrDefault(subject, 0);
            double percentage = ((double) attended / total) * 100;
            
            summaryList.add(new AttendanceItem(subject, String.format(Locale.getDefault(), "%.1f%%", percentage)));
        }

        AttendanceAdapter adapter = new AttendanceAdapter(this, summaryList);
        attendanceList.setAdapter(adapter);
    }

    private void startScanning() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan Attendance QR Code");
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
                Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_SHORT).show();
                return;
            }

            final String sessionId = parts[0];
            final double teacherLat = Double.parseDouble(parts[1]);
            final double teacherLon = Double.parseDouble(parts[2]);

            // For fully offline operation, we might not be able to fetch sessionDoc from server
            // But Firestore persistence will try to get it from local cache if it was recently fetched.
            fStore.collection("class_sessions").document(sessionId).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            DocumentSnapshot sessionDoc = task.getResult();
                            String sessionCourse = sessionDoc.getString("course");
                            String sessionSemester = sessionDoc.getString("semester");

                            if (sessionCourse == null || sessionSemester == null || myCourse == null || mySemester == null ||
                                    !sessionCourse.trim().equalsIgnoreCase(myCourse.trim()) ||
                                    !sessionSemester.trim().equalsIgnoreCase(mySemester.trim())) {
                                Toast.makeText(StudentDashboard.this, "Wrong Class! You belong to " + myCourse + " but this is " + sessionCourse + ".", Toast.LENGTH_LONG).show();
                                return;
                            }

                            Long startTime = sessionDoc.getLong("startTime");
                            Integer duration = sessionDoc.get("duration", Integer.class);
                            if (duration == null) duration = 50; 

                            final String subject = sessionDoc.getString("subject");
                            long currentTime = System.currentTimeMillis();

                            if (startTime != null && (currentTime - startTime) <= (long) duration * 60 * 1000) {
                                validateLocationAndMark(sessionId, subject, teacherLat, teacherLon);
                            } else {
                                Toast.makeText(StudentDashboard.this, "QR Expired! Class ended.", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // If we can't find the session (e.g., first time offline), 
                            // we proceed if we trust the QR data for offline mode
                            // or show a specific message.
                            Log.d(TAG, "Session doc not found locally/online. Proceeding with QR info.");
                            validateLocationAndMark(sessionId, "Unknown Subject", teacherLat, teacherLon);
                        }
                    });

        } catch (Exception e) {
            Toast.makeText(this, "Error parsing QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void validateLocationAndMark(final String sessionId, final String subject, final double teacherLat, final double teacherLon) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return; 
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(studentLocation -> {
                    if (studentLocation != null) {
                        checkDistanceAndMark(studentLocation, teacherLat, teacherLon, sessionId, subject);
                    } else {
                        // Fallback to LocationManager GPS_PROVIDER
                        tryGPSProviderFallback(teacherLat, teacherLon, sessionId, subject);
                    }
                })
                .addOnFailureListener(e -> {
                    tryGPSProviderFallback(teacherLat, teacherLon, sessionId, subject);
                });
    }

    private void tryGPSProviderFallback(double teacherLat, double teacherLon, String sessionId, String subject) {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastKnown != null) {
                    checkDistanceAndMark(lastKnown, teacherLat, teacherLon, sessionId, subject);
                } else {
                    Toast.makeText(this, "Waiting for GPS signal...", Toast.LENGTH_SHORT).show();
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location location) {
                            checkDistanceAndMark(location, teacherLat, teacherLon, sessionId, subject);
                        }
                        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                        @Override public void onProviderEnabled(@NonNull String provider) {}
                        @Override public void onProviderDisabled(@NonNull String provider) {}
                    }, null);
                }
            }
        } else {
            Toast.makeText(StudentDashboard.this, "Could not get your location. Please ensure GPS is ON.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkDistanceAndMark(Location studentLocation, double teacherLat, double teacherLon, String sessionId, String subject) {
        float[] results = new float[1];
        Location.distanceBetween(teacherLat, teacherLon, studentLocation.getLatitude(), studentLocation.getLongitude(), results);
        float distanceInMeters = results[0];

        if (distanceInMeters <= MAX_ALLOWED_DISTANCE) {
            checkDuplicateAndMark(sessionId, subject);
        } else {
            txtStatus.setText("Status: Too Far (" + (int)distanceInMeters + "m)");
            Toast.makeText(StudentDashboard.this, "You are too far from the teacher! (" + (int)distanceInMeters + "m)", Toast.LENGTH_SHORT).show();
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

    private void checkDuplicateAndMark(final String sessionId, final String subject) {
        final String email = fAuth.getCurrentUser().getEmail();
        final String uid = fAuth.getCurrentUser().getUid();

        // Use local-first logic for offline support
        fStore.collection("attendance")
                .whereEqualTo("studentEmail", email)
                .whereEqualTo("sessionId", sessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        Toast.makeText(StudentDashboard.this, "Attendance already marked for this session!", Toast.LENGTH_SHORT).show();
                    } else {
                        // If offline, task might fail or return empty even if marked (if not synced yet)
                        // But Firestore's internal persistence usually handles this.
                        saveAttendanceData(sessionId, subject, uid, email);
                    }
                });
    }

    private void saveAttendanceData(String sessionId, String subject, String uid, String email) {
        long timestamp = System.currentTimeMillis();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));

        Map<String, Object> attendance = new HashMap<>();
        attendance.put("studentName", studentName);
        attendance.put("studentId", uid);
        attendance.put("studentEmail", email);
        attendance.put("subject", subject != null ? subject.trim() : "");
        attendance.put("course", myCourse != null ? myCourse.trim() : "");
        attendance.put("semester", mySemester != null ? mySemester.trim() : "");
        attendance.put("sessionId", sessionId);
        attendance.put("date", date);
        attendance.put("time", time);
        attendance.put("timestamp", timestamp);
        attendance.put("status", "Present");

        // Save immediately (Firestore queues it if offline)
        fStore.collection("attendance").add(attendance);
        
        txtStatus.setText("Status: Attendance Marked for " + subject);
        Toast.makeText(StudentDashboard.this, "Attendance Marked Locally. Will sync when online.", Toast.LENGTH_LONG).show();
        
        SheetLogger.logToSheet(email, "Scan Success", "Student scanned QR for " + subject + " (Session: " + sessionId + ")");
        loadAttendanceSummary();
    }

    private static class AttendanceItem {
        String subject;
        String percentage;

        AttendanceItem(String subject, String percentage) {
            this.subject = subject;
            this.percentage = percentage;
        }
    }

    private static class AttendanceAdapter extends ArrayAdapter<AttendanceItem> {
        AttendanceAdapter(Context context, List<AttendanceItem> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_attendance_row, parent, false);
            }
            AttendanceItem item = getItem(position);
            TextView tvSubject = convertView.findViewById(R.id.tvSubjectName);
            TextView tvPercentage = convertView.findViewById(R.id.tvPercentageText);

            if (item != null) {
                tvSubject.setText(item.subject);
                tvPercentage.setText(item.percentage);
            }
            return convertView;
        }
    }
}
