package com.qron.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddStudentToSessionActivity extends AppCompatActivity {

    private String sessionId, course, semester, subject, date;
    private RecyclerView recyclerView;
    private StudentSearchAdapter adapter;
    private List<Map<String, Object>> studentList = new ArrayList<>();
    private List<Map<String, Object>> filteredList = new ArrayList<>();
    private FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_student_to_session);

        sessionId = getIntent().getStringExtra("sessionId");
        course = getIntent().getStringExtra("course");
        semester = getIntent().getStringExtra("semester");
        subject = getIntent().getStringExtra("subject");
        date = getIntent().getStringExtra("date");

        fStore = FirebaseFirestore.getInstance();
        recyclerView = findViewById(R.id.recyclerViewStudents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new StudentSearchAdapter(filteredList);
        recyclerView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.searchViewStudents);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        fetchStudents();
    }

    private void fetchStudents() {
        fStore.collection("users")
                .whereEqualTo("role", "Student")
                .whereEqualTo("course", course)
                .whereEqualTo("semester", semester)
                .orderBy("regNo", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    studentList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("uid", doc.getId());
                            studentList.add(data);
                        }
                    }
                    filter("");
                })
                .addOnFailureListener(e -> {
                    Log.e("AddStudent", "Error fetching students: " + e.getMessage());
                    // Fallback in case index is missing for orderBy
                    fStore.collection("users")
                        .whereEqualTo("role", "Student")
                        .whereEqualTo("course", course)
                        .whereEqualTo("semester", semester)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            studentList.clear();
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                Map<String, Object> data = doc.getData();
                                if (data != null) {
                                    data.put("uid", doc.getId());
                                    studentList.add(data);
                                }
                            }
                            // Sort locally if Firestore index isn't ready
                            studentList.sort((o1, o2) -> {
                                String r1 = (String) o1.get("regNo");
                                String r2 = (String) o2.get("regNo");
                                if (r1 == null) return 1;
                                if (r2 == null) return -1;
                                return r1.compareTo(r2);
                            });
                            filter("");
                        });
                });
    }

    private void filter(String text) {
        filteredList.clear();
        for (Map<String, Object> student : studentList) {
            String name = (String) student.get("fullName");
            String regNo = (String) student.get("regNo");
            if ((name != null && name.toLowerCase().contains(text.toLowerCase())) ||
                (regNo != null && regNo.toLowerCase().contains(text.toLowerCase()))) {
                filteredList.add(student);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class StudentSearchAdapter extends RecyclerView.Adapter<StudentSearchAdapter.ViewHolder> {
        private List<Map<String, Object>> list;

        StudentSearchAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> student = list.get(position);
            String name = (String) student.get("fullName");
            String regNo = (String) student.get("regNo");
            holder.text1.setText(name);
            holder.text2.setText("Reg No: " + regNo);

            holder.itemView.setOnClickListener(v -> addStudentToSession(student));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    private void addStudentToSession(Map<String, Object> student) {
        String studentId = (String) student.get("uid");
        String name = (String) student.get("fullName");
        String regNo = (String) student.get("regNo");
        String email = (String) student.get("email");

        // Check if already present in attendance
        fStore.collection("attendance")
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        markPresent(studentId, name, email, regNo);
                    } else {
                        Toast.makeText(this, "Student already present", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Student Added Successfully", Toast.LENGTH_SHORT).show();
            
            String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
            SheetLogger.logToSheet(adminEmail, "Edit Attendance", "Admin added student " + name + " (" + regNo + ") to session " + sessionId);
            
            finish();
        });
    }
}
