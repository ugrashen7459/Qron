package com.qron.app;

import android.app.AlertDialog;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class PromoteStudentsActivity extends AppCompatActivity {

    private static final String TAG = "PromoteDebug";
    Spinner spinnerCourse, spinnerCurrentSem, spinnerTargetSem;
    Button btnPromoteBatch;
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
        progressBar = findViewById(R.id.progressBar);

        fStore = FirebaseFirestore.getInstance();

        setupSpinners();

        btnPromoteBatch.setOnClickListener(v -> confirmPromotion());
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
                        semesters.add("Semester " + i);
                    }
                    ArrayAdapter<String> semesterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, semesters);
                    semesterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerCurrentSem.setAdapter(semesterAdapter);
                    spinnerTargetSem.setAdapter(semesterAdapter);
                }
            }
        });
    }

    private void confirmPromotion() {
        if (spinnerCourse.getSelectedItem() == null || spinnerCurrentSem.getSelectedItem() == null || spinnerTargetSem.getSelectedItem() == null) {
            Toast.makeText(this, "Please select all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String course = spinnerCourse.getSelectedItem().toString();
        String currentSem = spinnerCurrentSem.getSelectedItem().toString();
        String targetSem = spinnerTargetSem.getSelectedItem().toString();

        if (currentSem.equals(targetSem)) {
            Toast.makeText(this, "Current and Target semesters cannot be same", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Promote Batch?")
                .setMessage("Are you sure you want to move all students of " + course + " from " + currentSem + " to " + targetSem + "?")
                .setPositiveButton("Yes, Promote", (dialog, which) -> performPromotion(course, currentSem, targetSem))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPromotion(String selectedCourse, String currentSem, String targetSemRaw) {
        progressBar.setVisibility(View.VISIBLE);
        btnPromoteBatch.setEnabled(false);

        final String targetSem = targetSemRaw.replace("Semester", "Sem");
        String currentSemNumber = currentSem.replaceAll("[^0-9]", "");

        fStore.collection("users")
                .whereEqualTo("role", "Student")
                .whereEqualTo("course", selectedCourse)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(PromoteStudentsActivity.this, "No students found for " + selectedCourse, Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnPromoteBatch.setEnabled(true);
                        return;
                    }

                    WriteBatch batch = fStore.batch();
                    int count = 0;
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String studentSem = doc.getString("semester");
                        if (studentSem != null) {
                            String studentSemNum = studentSem.replaceAll("[^0-9]", "");
                            if (studentSemNum.equals(currentSemNumber)) {
                                batch.update(doc.getReference(), "semester", targetSem);
                                count++;
                            }
                        }
                    }

                    if (count == 0) {
                        Toast.makeText(PromoteStudentsActivity.this, "No students found in " + currentSem, Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnPromoteBatch.setEnabled(true);
                        return;
                    }

                    final int finalCount = count;
                    batch.commit().addOnSuccessListener(unused -> {
                        progressBar.setVisibility(View.GONE);
                        btnPromoteBatch.setEnabled(true);
                        Toast.makeText(PromoteStudentsActivity.this, finalCount + " students promoted to " + targetSem + " successfully.", Toast.LENGTH_LONG).show();

                        String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                        SheetLogger.logToSheet(adminEmail, "Promoted Batch", "Batch " + selectedCourse + " promoted from " + currentSem + " to " + targetSemRaw);

                    }).addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        btnPromoteBatch.setEnabled(true);
                        Toast.makeText(PromoteStudentsActivity.this, "Promotion Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnPromoteBatch.setEnabled(true);
                    Toast.makeText(PromoteStudentsActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
