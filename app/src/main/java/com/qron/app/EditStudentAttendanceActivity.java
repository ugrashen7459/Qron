package com.qron.app;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditStudentAttendanceActivity extends AppCompatActivity {

    private String sessionId, subject, date, course, semester;
    private RecyclerView rvStudents;
    private FloatingActionButton fabAddStudent;
    private FirebaseFirestore fStore;
    private List<AttendanceRecord> studentList = new ArrayList<>();
    private StudentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_student_attendance);

        sessionId = getIntent().getStringExtra("sessionId");
        subject = getIntent().getStringExtra("subject");
        date = getIntent().getStringExtra("date");
        course = getIntent().getStringExtra("course");
        semester = getIntent().getStringExtra("semester");

        ((TextView) findViewById(R.id.tvSessionInfo)).setText("Session: " + subject);
        ((TextView) findViewById(R.id.tvDateInfo)).setText("Date: " + date);

        rvStudents = findViewById(R.id.rvStudents);
        fabAddStudent = findViewById(R.id.fabAddStudent);
        fStore = FirebaseFirestore.getInstance();

        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentAdapter(studentList);
        rvStudents.setAdapter(adapter);

        fetchAttendance();

        fabAddStudent.setOnClickListener(v -> showAddStudentDialog());
    }

    private void fetchAttendance() {
        fStore.collection("attendance")
                .whereEqualTo("sessionId", sessionId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    studentList.clear();
                    for (DocumentSnapshot doc : snapshots) {
                        studentList.add(new AttendanceRecord(
                                doc.getId(),
                                doc.getString("studentName"),
                                doc.getString("regNo") != null ? doc.getString("regNo") : doc.getString("studentEmail")
                        ));
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showAddStudentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Student to Session");

        final EditText input = new EditText(this);
        input.setHint("Enter Registration Number");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Search & Add", (dialog, which) -> {
            String regNo = input.getText().toString().trim();
            if (!regNo.isEmpty()) {
                searchAndAddStudent(regNo);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void searchAndAddStudent(String regNo) {
        fStore.collection("users")
                .whereEqualTo("regNo", regNo)
                .whereEqualTo("role", "Student")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        DocumentSnapshot userDoc = snapshots.getDocuments().get(0);
                        String studentName = userDoc.getString("fullName");
                        String studentEmail = userDoc.getString("email");
                        String studentId = userDoc.getId();

                        // Check if already present
                        fStore.collection("attendance")
                                .whereEqualTo("sessionId", sessionId)
                                .whereEqualTo("studentId", studentId)
                                .get()
                                .addOnSuccessListener(attendanceSnapshots -> {
                                    if (attendanceSnapshots.isEmpty()) {
                                        markPresent(studentId, studentName, studentEmail, regNo);
                                    } else {
                                        Toast.makeText(this, "Student already marked present", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Toast.makeText(this, "Student not found!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void markPresent(String studentId, String name, String email, String regNo) {
        long timestamp = System.currentTimeMillis();
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));

        Map<String, Object> att = new HashMap<>();
        att.put("studentName", name);
        att.put("studentId", studentId);
        att.put("studentEmail", email);
        att.put("regNo", regNo);
        att.put("subject", subject);
        att.put("course", course);
        att.put("semester", semester);
        att.put("sessionId", sessionId);
        att.put("date", date);
        att.put("time", time);
        att.put("timestamp", timestamp);
        att.put("status", "Present");

        fStore.collection("attendance").add(att).addOnSuccessListener(documentReference -> {
            Toast.makeText(this, name + " marked Present", Toast.LENGTH_SHORT).show();
            
            String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
            SheetLogger.logToSheet(adminEmail, "Edit Attendance", "Added student " + name + " (" + regNo + ") to session " + sessionId);
            
            fetchAttendance();
        });
    }

    private class AttendanceRecord {
        String docId, name, regNo;
        AttendanceRecord(String docId, String name, String regNo) {
            this.docId = docId;
            this.name = name;
            this.regNo = regNo;
        }
    }

    private class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {
        private List<AttendanceRecord> list;
        StudentAdapter(List<AttendanceRecord> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_edit, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AttendanceRecord record = list.get(position);
            holder.tvName.setText(record.name);
            holder.tvReg.setText("ID: " + record.regNo);
            holder.btnRemove.setOnClickListener(v -> {
                fStore.collection("attendance").document(record.docId).delete().addOnSuccessListener(unused -> {
                    Toast.makeText(EditStudentAttendanceActivity.this, "Marked Absent", Toast.LENGTH_SHORT).show();
                    
                    String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                    SheetLogger.logToSheet(adminEmail, "Edit Attendance", "Removed student " + record.name + " (" + record.regNo + ") from session " + sessionId);

                    fetchAttendance();
                });
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvReg;
            Button btnRemove;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvStudentName);
                tvReg = itemView.findViewById(R.id.tvRegNo);
                btnRemove = itemView.findViewById(R.id.btnRemove);
            }
        }
    }
}
