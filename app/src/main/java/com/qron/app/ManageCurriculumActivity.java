package com.qron.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageCurriculumActivity extends AppCompatActivity {

    private EditText etCourseName, etTotalSemesters;
    private Button btnSaveCourse, btnAddSubject;
    private Spinner spinnerCourse, spinnerSemester;
    private RecyclerView rvSubjects;
    private FirebaseFirestore fStore;
    private List<String> coursesList = new ArrayList<>();
    private List<String> subjectsList = new ArrayList<>();
    private SubjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_curriculum);

        etCourseName = findViewById(R.id.etCourseName);
        etTotalSemesters = findViewById(R.id.etTotalSemesters);
        btnSaveCourse = findViewById(R.id.btnSaveCourse);
        btnAddSubject = findViewById(R.id.btnAddSubject);
        spinnerCourse = findViewById(R.id.spinnerCourse);
        spinnerSemester = findViewById(R.id.spinnerSemester);
        rvSubjects = findViewById(R.id.rvSubjects);

        fStore = FirebaseFirestore.getInstance();

        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter();
        rvSubjects.setAdapter(adapter);

        loadCourses();

        btnSaveCourse.setOnClickListener(v -> saveCourse());
        btnAddSubject.setOnClickListener(v -> showAddSubjectDialog());

        spinnerCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSemesterSpinner(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerSemester.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadSubjects();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadCourses() {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            coursesList.clear();
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
    }

    private void saveCourse() {
        String courseName = etCourseName.getText().toString().trim();
        String totalSemsStr = etTotalSemesters.getText().toString().trim();

        if (TextUtils.isEmpty(courseName) || TextUtils.isEmpty(totalSemsStr)) {
            Toast.makeText(this, "Please enter all details", Toast.LENGTH_SHORT).show();
            return;
        }

        int totalSems = Integer.parseInt(totalSemsStr);
        Map<String, Object> courseData = new HashMap<>();
        courseData.put(courseName, totalSems);

        fStore.collection("Curriculum").document("Courses").update(courseData)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Course Saved", Toast.LENGTH_SHORT).show();
                    loadCourses();
                    String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                    SheetLogger.logToSheet(adminEmail, "Manage Curriculum", "Added/Updated Course: " + courseName + " with " + totalSems + " semesters");
                })
                .addOnFailureListener(e -> {
                    fStore.collection("Curriculum").document("Courses").set(courseData);
                    loadCourses();
                });
    }

    private void updateSemesterSpinner(String courseName) {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Long totalSems = documentSnapshot.getLong(courseName);
                if (totalSems != null) {
                    List<String> sems = new ArrayList<>();
                    for (int i = 1; i <= totalSems; i++) {
                        sems.add("Sem " + i);
                    }
                    ArrayAdapter<String> semAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sems);
                    semAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerSemester.setAdapter(semAdapter);
                }
            }
        });
    }

    private void loadSubjects() {
        String course = spinnerCourse.getSelectedItem().toString();
        String sem = spinnerSemester.getSelectedItem().toString();

        fStore.collection("Curriculum").document("Subjects")
                .collection(course).document(sem).get()
                .addOnSuccessListener(documentSnapshot -> {
                    subjectsList.clear();
                    if (documentSnapshot.exists()) {
                        List<String> subs = (List<String>) documentSnapshot.get("subjects");
                        if (subs != null) {
                            subjectsList.addAll(subs);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void showAddSubjectDialog() {
        if (spinnerCourse.getSelectedItem() == null || spinnerSemester.getSelectedItem() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Subject");
        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String subName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(subName)) {
                addSubject(subName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void addSubject(String subName) {
        String course = spinnerCourse.getSelectedItem().toString();
        String sem = spinnerSemester.getSelectedItem().toString();

        subjectsList.add(subName);
        Map<String, Object> data = new HashMap<>();
        data.put("subjects", subjectsList);

        fStore.collection("Curriculum").document("Subjects")
                .collection(course).document(sem).set(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Subject Added", Toast.LENGTH_SHORT).show();
                    loadSubjects();
                });
    }

    private class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String subject = subjectsList.get(position);
            holder.text1.setText(subject);
            holder.itemView.setOnClickListener(v -> showEditDeleteDialog(subject, position));
        }

        @Override
        public int getItemCount() { return subjectsList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
            }
        }
    }

    private void showEditDeleteDialog(String subject, int position) {
        String[] options = {"Edit", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(subject)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditDialog(subject, position);
                    else deleteSubject(position);
                }).show();
    }

    private void showEditDialog(String oldName, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Subject");
        final EditText input = new EditText(this);
        input.setText(oldName);
        builder.setView(input);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                subjectsList.set(position, newName);
                updateSubjectsInDb();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteSubject(int position) {
        subjectsList.remove(position);
        updateSubjectsInDb();
    }

    private void updateSubjectsInDb() {
        String course = spinnerCourse.getSelectedItem().toString();
        String sem = spinnerSemester.getSelectedItem().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("subjects", subjectsList);

        fStore.collection("Curriculum").document("Subjects")
                .collection(course).document(sem).set(data)
                .addOnSuccessListener(unused -> loadSubjects());
    }
}
