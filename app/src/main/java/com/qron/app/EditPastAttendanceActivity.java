package com.qron.app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditPastAttendanceActivity extends AppCompatActivity {

    private Spinner spinnerBranch, spinnerSem;
    private EditText etDate;
    private Button btnFetch;
    private RecyclerView rvSubjects;
    private FirebaseFirestore fStore;
    private List<SessionModel> sessionList = new ArrayList<>();
    private SubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_past_attendance);

        spinnerBranch = findViewById(R.id.spinnerBranch);
        spinnerSem = findViewById(R.id.spinnerSem);
        etDate = findViewById(R.id.etDate);
        btnFetch = findViewById(R.id.btnFetch);
        rvSubjects = findViewById(R.id.rvSubjects);

        fStore = FirebaseFirestore.getInstance();

        setupSpinners();
        setupDatePicker();

        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(sessionList);
        rvSubjects.setAdapter(adapter);

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
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        String subject = doc.getString("subject");
                        String sId = doc.getId();
                        sessionList.add(new SessionModel(subject, 0, sId));
                    }
                    if (sessionList.isEmpty()) {
                        Toast.makeText(this, "No sessions found", Toast.LENGTH_SHORT).show();
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {
        private List<SessionModel> list;

        SubjectAdapter(List<SessionModel> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject_edit, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SessionModel session = list.get(position);
            holder.tvSubjectName.setText(session.getSubjectName());
            
            fStore.collection("attendance").whereEqualTo("sessionId", session.getSessionId()).get()
                    .addOnSuccessListener(snapshots -> holder.tvStudentCount.setText("Present: " + snapshots.size()));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(EditPastAttendanceActivity.this, EditStudentAttendanceActivity.class);
                intent.putExtra("sessionId", session.getSessionId());
                intent.putExtra("subject", session.getSubjectName());
                intent.putExtra("date", etDate.getText().toString());
                intent.putExtra("course", spinnerBranch.getSelectedItem().toString());
                intent.putExtra("semester", spinnerSem.getSelectedItem().toString());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSubjectName, tvStudentCount;
            ViewHolder(View itemView) {
                super(itemView);
                tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
                tvStudentCount = itemView.findViewById(R.id.tvStudentCount);
            }
        }
    }
}
