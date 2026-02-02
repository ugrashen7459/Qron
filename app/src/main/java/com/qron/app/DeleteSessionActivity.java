package com.qron.app;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeleteSessionActivity extends AppCompatActivity {

    private Spinner spinnerBranch, spinnerSem;
    private EditText etDate;
    private Button btnFetch;
    private RecyclerView rvSessions;
    private FirebaseFirestore fStore;
    private List<SessionModel> sessionList = new ArrayList<>();
    private SessionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_session);

        spinnerBranch = findViewById(R.id.spinnerBranch);
        spinnerSem = findViewById(R.id.spinnerSem);
        etDate = findViewById(R.id.etDate);
        btnFetch = findViewById(R.id.btnFetch);
        rvSessions = findViewById(R.id.rvSessions);

        fStore = FirebaseFirestore.getInstance();

        setupSpinners();
        setupDatePicker();

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SessionAdapter(sessionList, this::showDeleteConfirmation);
        rvSessions.setAdapter(adapter);

        btnFetch.setOnClickListener(v -> fetchSessions());
    }

    private void setupSpinners() {
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
            spinnerBranch.setAdapter(courseAdapter);
        });

        spinnerBranch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    updateSemesterSpinner(parent.getItemAtPosition(position).toString());
                } else {
                    spinnerSem.setAdapter(null);
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
                    spinnerSem.setAdapter(semAdapter);
                }
            }
        });
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
                String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year1, month1 + 1, dayOfMonth);
                etDate.setText(date);
            }, year, month, day);
            datePickerDialog.show();
        });
    }

    private void fetchSessions() {
        if (spinnerBranch.getSelectedItem() == null || spinnerSem.getSelectedItem() == null) return;
        
        String branch = spinnerBranch.getSelectedItem().toString();
        String sem = spinnerSem.getSelectedItem().toString();
        String date = etDate.getText().toString();

        if (branch.equals("Select Course") || sem.equals("Select Semester") || date.isEmpty()) {
            Toast.makeText(this, "Please select all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        fStore.collection("class_sessions")
                .whereEqualTo("course", branch)
                .whereEqualTo("semester", sem)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    sessionList.clear();
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "No sessions found for this date", Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    for (DocumentSnapshot sessionDoc : queryDocumentSnapshots) {
                        String subject = sessionDoc.getString("subject");
                        String sId = sessionDoc.getId();
                        
                        fStore.collection("attendance")
                                .whereEqualTo("sessionId", sId)
                                .get()
                                .addOnSuccessListener(attendanceSnapshots -> {
                                    long count = attendanceSnapshots.size();
                                    sessionList.add(new SessionModel(subject, count, sId));
                                    adapter.notifyDataSetChanged();
                                });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showDeleteConfirmation(SessionModel session) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Session")
                .setMessage("Are you sure you want to delete this session? This will remove all attendance and decrement the total class count.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSession(session))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSession(SessionModel session) {
        WriteBatch batch = fStore.batch();

        if (session.getSessionId() != null) {
            batch.delete(fStore.collection("class_sessions").document(session.getSessionId()));
        }

        fStore.collection("attendance")
                .whereEqualTo("sessionId", session.getSessionId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }

                    batch.commit().addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Session and all associated attendance deleted successfully", Toast.LENGTH_SHORT).show();
                        
                        String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                        String branch = spinnerBranch.getSelectedItem().toString();
                        String sem = spinnerSem.getSelectedItem().toString();
                        String date = etDate.getText().toString();
                        String subject = session.getSubjectName();
                        
                        SheetLogger.logToSheet(adminEmail, "Session Deleted", branch + " - " + sem + " - " + subject + " (Date: " + date + ")");

                        fetchSessions();
                    }).addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                });
    }
}
