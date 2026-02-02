package com.qron.app;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminViewTeachersActivity extends AppCompatActivity {

    SearchView searchViewTeachers;
    RecyclerView recyclerViewTeachers;
    StudentReportAdapter adapter;
    List<Map<String, Object>> allTeachersList = new ArrayList<>();
    FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_students); // Reusing the same layout as students

        // Hide spinners as they might not be relevant for teachers unless they have departments
        findViewById(R.id.spinnerFilterCourse).setVisibility(View.GONE);
        findViewById(R.id.spinnerFilterSemester).setVisibility(View.GONE);

        searchViewTeachers = findViewById(R.id.searchViewStudents);
        searchViewTeachers.setQueryHint("Search Teacher Name");
        recyclerViewTeachers = findViewById(R.id.recyclerViewStudents);

        fStore = FirebaseFirestore.getInstance();
        recyclerViewTeachers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentReportAdapter(new ArrayList<>());
        recyclerViewTeachers.setAdapter(adapter);

        fetchTeachers();

        searchViewTeachers.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterList();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterList();
                return false;
            }
        });
    }

    private void fetchTeachers() {
        fStore.collection("users")
                .whereEqualTo("role", "Teacher")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allTeachersList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("userId", doc.getId());
                            allTeachersList.add(data);
                        }
                    }
                    filterList();
                });
    }

    private void filterList() {
        String query = searchViewTeachers.getQuery().toString().toLowerCase();
        List<Map<String, Object>> filteredList = new ArrayList<>();

        for (Map<String, Object> teacher : allTeachersList) {
            String name = teacher.get("fullName") != null ? ((String) teacher.get("fullName")).toLowerCase() : "";
            if (name.contains(query)) {
                filteredList.add(teacher);
            }
        }
        adapter.setFilteredList(filteredList);
    }
}
