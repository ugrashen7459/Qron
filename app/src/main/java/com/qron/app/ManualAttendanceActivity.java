package com.qron.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManualAttendanceActivity extends AppCompatActivity {

    private static final String TAG = "ManualAttendance";
    private RecyclerView recyclerViewManual;
    private SearchView searchViewManual;
    private FirebaseFirestore fStore;
    private String course, semester, subject, sessionId;
    private List<StudentModel> originalList = new ArrayList<>();
    private List<StudentModel> filteredList = new ArrayList<>();
    private ManualAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_attendance);

        course = getIntent().getStringExtra("course");
        semester = getIntent().getStringExtra("semester");
        subject = getIntent().getStringExtra("subject");
        sessionId = getIntent().getStringExtra("sessionId");

        // Trim values to ensure consistency with other parts of the app
        if (course != null) course = course.trim();
        if (semester != null) semester = semester.trim();
        if (subject != null) subject = subject.trim();

        recyclerViewManual = findViewById(R.id.recyclerViewManual);
        searchViewManual = findViewById(R.id.searchViewManual);
        fStore = FirebaseFirestore.getInstance();

        recyclerViewManual.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ManualAdapter();
        recyclerViewManual.setAdapter(adapter);

        fetchStudentsAndAttendance();

        searchViewManual.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return false;
            }
        });
    }

    private void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (StudentModel student : originalList) {
                String name = student.name != null ? student.name.toLowerCase() : "";
                String reg = student.regNo != null ? student.regNo.toLowerCase() : "";
                if (name.contains(lowerCaseQuery) || reg.contains(lowerCaseQuery)) {
                    filteredList.add(student);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void fetchStudentsAndAttendance() {
        fStore.collection("users")
                .whereEqualTo("role", "Student")
                .whereEqualTo("course", course)
                .get()
                .addOnSuccessListener(studentSnapshots -> {
                    originalList.clear();
                    
                    String targetSemNum = semester != null ? semester.replaceAll("[^0-9]", "") : "";

                    for (DocumentSnapshot doc : studentSnapshots.getDocuments()) {
                        String studentSem = doc.getString("semester");
                        String studentName = doc.getString("fullName");

                        String studentSemNum = studentSem != null ? studentSem.replaceAll("[^0-9]", "") : "";

                        boolean isMatch = false;
                        if (!studentSemNum.isEmpty() && !targetSemNum.isEmpty()) {
                            if (studentSemNum.equals(targetSemNum)) {
                                isMatch = true;
                            }
                        }

                        if (isMatch) {
                            StudentModel student = new StudentModel(
                                    doc.getId(),
                                    studentName,
                                    doc.getString("email"),
                                    doc.getString("regNo"),
                                    false
                            );
                            originalList.add(student);
                        }
                    }

                    Collections.sort(originalList, (s1, s2) -> {
                        String r1 = s1.regNo != null ? s1.regNo : "";
                        String r2 = s2.regNo != null ? s2.regNo : "";
                        return r1.compareTo(r2);
                    });

                    filteredList.clear();
                    filteredList.addAll(originalList);

                    fStore.collection("attendance")
                            .whereEqualTo("sessionId", sessionId)
                            .get()
                            .addOnSuccessListener(attendanceSnapshots -> {
                                for (DocumentSnapshot attDoc : attendanceSnapshots.getDocuments()) {
                                    String studentId = attDoc.getString("studentId");
                                    for (StudentModel s : originalList) {
                                        if (s.id.equals(studentId)) {
                                            s.isPresent = true;
                                            s.attendanceDocId = attDoc.getId();
                                            break;
                                        }
                                    }
                                }
                                filter(searchViewManual.getQuery().toString());
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ManualAttendanceActivity.this, "Error fetching students: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private class StudentModel {
        String id, name, email, regNo, attendanceDocId;
        boolean isPresent;

        StudentModel(String id, String name, String email, String regNo, boolean isPresent) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.regNo = regNo;
            this.isPresent = isPresent;
        }
    }

    private class ManualAdapter extends RecyclerView.Adapter<ManualAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manual_attendance, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StudentModel student = filteredList.get(position);
            
            String displayText = student.name;
            if (student.regNo != null && !student.regNo.isEmpty()) {
                displayText = displayText + " (" + student.regNo + ")";
            }
            holder.txtName.setText(displayText);
            holder.txtEmail.setText(student.email);
            
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(student.isPresent);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    markAttendance(student, position);
                } else {
                    removeAttendance(student, position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtEmail;
            CheckBox checkBox;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtStudentName);
                txtEmail = itemView.findViewById(R.id.txtEmail);
                checkBox = itemView.findViewById(R.id.checkBoxAttendance);
            }
        }
    }

    private void markAttendance(StudentModel student, int position) {
        long timestamp = System.currentTimeMillis();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));

        Map<String, Object> attendance = new HashMap<>();
        attendance.put("studentName", student.name);
        attendance.put("studentId", student.id);
        attendance.put("studentEmail", student.email);
        attendance.put("subject", subject);
        attendance.put("course", course);
        attendance.put("semester", semester);
        attendance.put("sessionId", sessionId);
        attendance.put("date", date);
        attendance.put("time", time);
        attendance.put("timestamp", timestamp);
        attendance.put("status", "Present");

        fStore.collection("attendance").add(attendance).addOnSuccessListener(documentReference -> {
            student.isPresent = true;
            student.attendanceDocId = documentReference.getId();
            Toast.makeText(this, "Attendance Marked for " + student.name, Toast.LENGTH_SHORT).show();

            String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
            SheetLogger.logToSheet(adminEmail, "Manual Attendance", "Added [" + student.name + " (RegNo: " + student.regNo + ")] manually | " + course + " - " + semester + " - " + subject + " (Session: " + sessionId + ")");
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            adapter.notifyItemChanged(position);
        });
    }

    private void removeAttendance(StudentModel student, int position) {
        if (student.attendanceDocId == null) return;

        fStore.collection("attendance").document(student.attendanceDocId).delete().addOnSuccessListener(unused -> {
            student.isPresent = false;
            student.attendanceDocId = null;
            Toast.makeText(this, "Attendance Removed for " + student.name, Toast.LENGTH_SHORT).show();
            
            String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
            SheetLogger.logToSheet(adminEmail, "Manual Attendance", "Removed [" + student.name + " (RegNo: " + student.regNo + ")] manual attendance | " + course + " - " + semester + " - " + subject + " (Session: " + sessionId + ")");
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            adapter.notifyItemChanged(position);
        });
    }
}
