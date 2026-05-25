package com.qron.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PromoteStudentsActivity extends AppCompatActivity {

    private static final String TAG = "PromoteDebug";
    Spinner spinnerCourse, spinnerCurrentSem, spinnerTargetSem;
    Button btnPromoteBatch, btnGraduateAndArchive;
    ProgressBar progressBar;
    FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promote_students);

        spinnerCourse = findViewById(R.id.spinnerCourse);
        spinnerCurrentSem = findViewById(R.id.spinnerCurrentSem);
        spinnerTargetSem = findViewById(R.id.spinnerTargetSem);
        btnPromoteBatch = findViewById(R.id.btnPromoteBatch);
        btnGraduateAndArchive = findViewById(R.id.btnGraduateAndArchive);
        progressBar = findViewById(R.id.progressBar);

        fStore = FirebaseFirestore.getInstance();

        setupSpinners();

        btnPromoteBatch.setOnClickListener(v -> handleAction("PROMOTE"));
        btnGraduateAndArchive.setOnClickListener(v -> handleAction("GRADUATE"));
    }

    private void setupSpinners() {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            List<String> coursesList = new ArrayList<>();
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
                updateSemesterSpinners(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateSemesterSpinners(String courseName) {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long totalSems = documentSnapshot.getLong(courseName);
                if (totalSems != null) {
                    List<String> semesters = new ArrayList<>();
                    for (int i = 1; i <= totalSems; i++) {
                        semesters.add("Sem " + i);
                    }
                    ArrayAdapter<String> semesterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, semesters);
                    semesterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerCurrentSem.setAdapter(semesterAdapter);
                    spinnerTargetSem.setAdapter(semesterAdapter);
                }
            }
        });
    }

    private void handleAction(String type) {
        if (spinnerCourse.getSelectedItem() == null || spinnerCurrentSem.getSelectedItem() == null) {
            Toast.makeText(this, "Please select Course and Current Sem", Toast.LENGTH_SHORT).show();
            return;
        }

        String course = spinnerCourse.getSelectedItem().toString();
        String currentSem = spinnerCurrentSem.getSelectedItem().toString();
        
        if (type.equals("PROMOTE")) {
            String targetSem = spinnerTargetSem.getSelectedItem().toString();
            if (currentSem.equals(targetSem)) {
                Toast.makeText(this, "Current and Target semesters cannot be same", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Promote Batch?")
                    .setMessage("This will backup data to Excel, share it, delete current attendance, and move students to " + targetSem + ". Proceed?")
                    .setPositiveButton("Yes, Promote", (dialog, which) -> startProcess(type, course, currentSem, targetSem))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Graduate & Archive?")
                    .setMessage("CRITICAL: This will backup data to Excel, share it, and PERMANENTLY DELETE all students and attendance for " + course + " " + currentSem + ". Proceed?")
                    .setPositiveButton("Yes, Graduate & Delete", (dialog, which) -> startProcess(type, course, currentSem, null))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void startProcess(String type, String course, String currentSem, String targetSem) {
        progressBar.setVisibility(View.VISIBLE);
        btnPromoteBatch.setEnabled(false);
        btnGraduateAndArchive.setEnabled(false);

        // Fetch Students
        fStore.collection("users")
                .whereEqualTo("role", "Student")
                .whereEqualTo("course", course)
                .whereEqualTo("semester", currentSem)
                .get()
                .addOnSuccessListener(studentSnaps -> {
                    if (studentSnaps.isEmpty()) {
                        Toast.makeText(this, "No students found in this batch", Toast.LENGTH_SHORT).show();
                        resetUI();
                        return;
                    }

                    List<Map<String, Object>> studentsList = new ArrayList<>();
                    for (DocumentSnapshot doc : studentSnaps) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("id", doc.getId());
                            studentsList.add(data);
                        }
                    }

                    fetchDataForExcel(type, course, currentSem, targetSem, studentsList);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetUI();
                });
    }

    private void fetchDataForExcel(String type, String course, String currentSem, String targetSem, List<Map<String, Object>> studentsList) {
        // Fetch Subjects
        fStore.collection("Curriculum").document("Subjects")
                .collection(course).document(currentSem).get()
                .addOnSuccessListener(subjectDoc -> {
                    List<String> subjects = (List<String>) subjectDoc.get("subjects");
                    if (subjects == null || subjects.isEmpty()) {
                        Toast.makeText(this, "No subjects found for this batch", Toast.LENGTH_SHORT).show();
                        resetUI();
                        return;
                    }

                    // Fetch Total Classes (Sessions)
                    fStore.collection("class_sessions")
                            .whereEqualTo("course", course)
                            .whereEqualTo("semester", currentSem)
                            .get()
                            .addOnSuccessListener(sessionSnaps -> {
                                Map<String, Integer> totalClassesPerSubject = new HashMap<>();
                                for (DocumentSnapshot doc : sessionSnaps) {
                                    String sub = doc.getString("subject");
                                    if (sub != null) {
                                        totalClassesPerSubject.put(sub, totalClassesPerSubject.getOrDefault(sub, 0) + 1);
                                    }
                                }

                                // Fetch Attendance Records
                                fStore.collection("attendance")
                                        .whereEqualTo("course", course)
                                        .whereEqualTo("semester", currentSem)
                                        .get()
                                        .addOnSuccessListener(attSnaps -> {
                                            // Aggregate Attendance: StudentEmail -> (Subject -> AttendedCount)
                                            Map<String, Map<String, Integer>> attendanceMap = new HashMap<>();
                                            for (DocumentSnapshot doc : attSnaps) {
                                                String email = doc.getString("studentEmail");
                                                String sub = doc.getString("subject");
                                                if (email != null && sub != null) {
                                                    Map<String, Integer> studentAtt = attendanceMap.computeIfAbsent(email, k -> new HashMap<>());
                                                    studentAtt.put(sub, studentAtt.getOrDefault(sub, 0) + 1);
                                                }
                                            }

                                            generateExcelAndCleanup(type, course, currentSem, targetSem, studentsList, subjects, totalClassesPerSubject, attendanceMap, attSnaps.getDocuments());
                                        });
                            });
                });
    }

    private void generateExcelAndCleanup(String type, String course, String currentSem, String targetSem,
                                         List<Map<String, Object>> studentsList, List<String> subjects,
                                         Map<String, Integer> totalClasses, Map<String, Map<String, Integer>> attendanceMap,
                                         List<DocumentSnapshot> attDocsToDelete) {

        StringBuilder htmlData = new StringBuilder();
        htmlData.append("<html><body><table border='1'>");

        // Header Row (Identical to ViewStudentsActivity)
        htmlData.append("<tr><th>Name</th><th>Reg No</th><th>Email</th><th>Phone</th>");
        for (String sub : subjects) {
            htmlData.append("<th>").append(sub).append(" Count</th>");
            htmlData.append("<th>").append(sub).append(" %</th>");
        }
        htmlData.append("</tr>");

        // Data Rows
        for (Map<String, Object> student : studentsList) {
            String name = (String) student.get("fullName");
            String regNo = (String) student.get("regNo");
            String email = (String) student.get("email");
            String phone = (String) student.get("phone");

            htmlData.append("<tr>");
            htmlData.append("<td>").append(name != null ? name : "").append("</td>");
            htmlData.append("<td style='mso-number-format:\"\\@\"'>").append(regNo != null ? regNo : "N/A").append("</td>");
            htmlData.append("<td>").append(email != null ? email : "").append("</td>");
            htmlData.append("<td>").append(phone != null ? phone : "").append("</td>");

            Map<String, Integer> studentAttended = attendanceMap.getOrDefault(email, new HashMap<>());

            for (String sub : subjects) {
                int total = totalClasses.getOrDefault(sub, 0);
                int attended = studentAttended.getOrDefault(sub, 0);
                double percentage = total > 0 ? ((double) attended / total) * 100 : 0;

                String colorCode = "#FFFFFF"; // Default white
                if (percentage < 50) {
                    colorCode = "#FF9999"; // Red
                } else if (percentage < 75) {
                    colorCode = "#FFFF99"; // Yellow
                }

                htmlData.append("<td style='mso-number-format:\"\\@\"'>").append(attended).append("/").append(total).append("</td>");
                htmlData.append("<td style='background-color:").append(colorCode).append("'>")
                        .append(String.format(Locale.getDefault(), "%.0f%%", percentage)).append("</td>");
            }
            htmlData.append("</tr>");
        }
        htmlData.append("</table></body></html>");

        try {
            String fileName = course.replace(" ", "_") + "_" + currentSem.replace(" ", "_") + "_Archive.xls";
            File file = new File(getExternalFilesDir(null), fileName);
            FileOutputStream out = new FileOutputStream(file);
            out.write(htmlData.toString().getBytes());
            out.close();

            Uri path = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/vnd.ms-excel");
            shareIntent.putExtra(Intent.EXTRA_STREAM, path);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Archive Report"));

            // Perform Cleanup in Firestore after sharing
            performCleanup(type, course, currentSem, targetSem, studentsList, attDocsToDelete);

        } catch (IOException e) {
            Log.e(TAG, "Archive error", e);
            Toast.makeText(this, "Archive Failed", Toast.LENGTH_SHORT).show();
            resetUI();
        }
    }

    private void performCleanup(String type, String course, String currentSem, String targetSem, List<Map<String, Object>> studentsList, List<DocumentSnapshot> attDocsToDelete) {
        WriteBatch batch = fStore.batch();

        // Delete Attendance records
        for (DocumentSnapshot ds : attDocsToDelete) {
            batch.delete(ds.getReference());
        }

        // Handle User records
        for (Map<String, Object> student : studentsList) {
            com.google.firebase.firestore.DocumentReference ref = fStore.collection("users").document((String) student.get("id"));
            if (type.equals("GRADUATE")) {
                batch.delete(ref);
            } else {
                batch.update(ref, "semester", targetSem);
            }
        }

        batch.commit().addOnSuccessListener(unused -> {
            String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
            String logAction = type.equals("GRADUATE") ? "Batch Graduated & Archived" : "Batch Promoted & Attendance Archived";
            SheetLogger.logToSheet(adminEmail, logAction, "Course: " + course + ", Sem: " + currentSem);

            Toast.makeText(this, "Process completed: Data archived and cleaned up.", Toast.LENGTH_LONG).show();
            resetUI();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Cleanup error", e);
            Toast.makeText(this, "Archive shared, but Firestore cleanup failed.", Toast.LENGTH_SHORT).show();
            resetUI();
        });
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        btnPromoteBatch.setEnabled(true);
        btnGraduateAndArchive.setEnabled(true);
    }
}
