package com.qron.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        fabAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(EditStudentAttendanceActivity.this, AddStudentToSessionActivity.class);
            intent.putExtra("sessionId", sessionId);
            intent.putExtra("subject", subject);
            intent.putExtra("date", date);
            intent.putExtra("course", course);
            intent.putExtra("semester", semester);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchAttendance();
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
                    
                    // Sort locally by registration number
                    Collections.sort(studentList, (s1, s2) -> {
                        if (s1.regNo == null) return 1;
                        if (s2.regNo == null) return -1;
                        return s1.regNo.compareTo(s2.regNo);
                    });

                    adapter.notifyDataSetChanged();
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
