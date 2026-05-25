package com.qron.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ViewStudentsActivity extends AppCompatActivity {

    private static final String TAG = "ViewStudentsActivity";
    Spinner spinnerFilterCourse, spinnerFilterSemester;
    SearchView searchViewStudents;
    RecyclerView recyclerViewStudents;
    ImageButton btnDirectDownload, btnFilterAttendance;
    StudentReportAdapter adapter;
    List<Map<String, Object>> allStudentsList = new ArrayList<>();
    FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_students);

        spinnerFilterCourse = findViewById(R.id.spinnerFilterCourse);
        spinnerFilterSemester = findViewById(R.id.spinnerFilterSemester);
        searchViewStudents = findViewById(R.id.searchViewStudents);
        recyclerViewStudents = findViewById(R.id.recyclerViewStudents);
        btnDirectDownload = findViewById(R.id.btnDirectDownload);
        btnFilterAttendance = findViewById(R.id.btnFilterAttendance);

        fStore = FirebaseFirestore.getInstance();
        recyclerViewStudents.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentReportAdapter(new ArrayList<>());
        recyclerViewStudents.setAdapter(adapter);

        setupSpinners();
        fetchStudents();

        btnDirectDownload.setOnClickListener(v -> {
            exportFilteredDataToExcel();
        });

        btnFilterAttendance.setOnClickListener(v -> {
            showFilterDialog();
        });

        searchViewStudents.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Subject-Wise Defaulter Filter");
        builder.setMessage("Show students if ANY single subject is below X%.\nEnter Minimum % per Subject:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 75");
        builder.setView(input);

        builder.setPositiveButton("Apply Filter", (dialog, which) -> {
            String value = input.getText().toString();
            if (!value.isEmpty()) {
                applyDefaulterFilter(Integer.parseInt(value));
            } else {
                Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("Clear Filter", (dialog, which) -> {
            filterList(); // Resets to current spinner/search filters
        });
        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    private void applyDefaulterFilter(int cutoff) {
        List<Map<String, Object>> defaulterList = new ArrayList<>();
        
        String selectedCourse = spinnerFilterCourse.getSelectedItem() != null ? spinnerFilterCourse.getSelectedItem().toString() : "All Courses";
        String selectedSemester = spinnerFilterSemester.getSelectedItem() != null ? spinnerFilterSemester.getSelectedItem().toString() : "All Semesters";
        String query = searchViewStudents.getQuery().toString().toLowerCase();

        for (Map<String, Object> student : allStudentsList) {
            String name = student.get("fullName") != null ? ((String) student.get("fullName")).toLowerCase() : "";
            String regNo = student.get("regNo") != null ? ((String) student.get("regNo")).toLowerCase() : "";
            String course = (String) student.get("course");
            String semester = (String) student.get("semester");

            boolean matchesCourse = selectedCourse.equals("All Courses") || (course != null && course.equals(selectedCourse));
            boolean matchesSemester = selectedSemester.equals("All Semesters") || (semester != null && semester.equals(selectedSemester));
            boolean matchesSearch = name.contains(query) || regNo.contains(query);

            if (matchesCourse && matchesSemester && matchesSearch) {
                    Map<String, Double> subjectPercentages = (Map<String, Double>) student.get("subjectPercentages");
                    
                    if (subjectPercentages != null && !subjectPercentages.isEmpty()) {
                        boolean isDefaulter = false;
                        for (Double percent : subjectPercentages.values()) {
                            if (percent < cutoff) {
                                isDefaulter = true;
                                break;
                            }
                        }
                        
                        if (isDefaulter) {
                            defaulterList.add(student);
                        }
                    }
            }
        }
        
        // Sorting by regNo ascending
        Collections.sort(defaulterList, (s1, s2) -> {
            String r1 = (String) s1.get("regNo");
            String r2 = (String) s2.get("regNo");
            if (r1 == null) r1 = "";
            if (r2 == null) r2 = "";
            return r1.compareTo(r2);
        });

        adapter.setFilteredList(defaulterList);
        Toast.makeText(this, "Found " + defaulterList.size() + " students below " + cutoff + "% in at least one subject", Toast.LENGTH_LONG).show();
    }

    private void setupSpinners() {
        fStore.collection("Curriculum").document("Courses").get().addOnSuccessListener(documentSnapshot -> {
            List<String> coursesList = new ArrayList<>();
            coursesList.add("All Courses");
            if (documentSnapshot.exists()) {
                Map<String, Object> data = documentSnapshot.getData();
                if (data != null) {
                    coursesList.addAll(data.keySet());
                }
            }
            ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, coursesList);
            courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFilterCourse.setAdapter(courseAdapter);
        });

        spinnerFilterCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    updateSemesterSpinner(parent.getItemAtPosition(position).toString());
                } else {
                    spinnerFilterSemester.setAdapter(null);
                }
                filterList();
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
                    List<String> semesters = new ArrayList<>();
                    semesters.add("All Semesters");
                    for (int i = 1; i <= totalSems; i++) {
                        semesters.add("Sem " + i);
                    }
                    ArrayAdapter<String> semesterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, semesters);
                    semesterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerFilterSemester.setAdapter(semesterAdapter);

                    spinnerFilterSemester.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            filterList();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                }
            }
        });
    }

    private void fetchStudents() {
        fStore.collection("users")
                .whereEqualTo("role", "Student")
                .limit(20) // Implementation of Pagination to save Firebase reads
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allStudentsList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("userId", doc.getId());
                            allStudentsList.add(data);
                        }
                    }
                    filterList();
                });
    }

    private void filterList() {
        String selectedCourse = spinnerFilterCourse.getSelectedItem() != null ? spinnerFilterCourse.getSelectedItem().toString() : "All Courses";
        String selectedSemester = spinnerFilterSemester != null && spinnerFilterSemester.getSelectedItem() != null ? spinnerFilterSemester.getSelectedItem().toString() : "All Semesters";
        String query = searchViewStudents.getQuery().toString().toLowerCase();

        List<Map<String, Object>> filteredList = new ArrayList<>();

        for (Map<String, Object> student : allStudentsList) {
            String name = student.get("fullName") != null ? ((String) student.get("fullName")).toLowerCase() : "";
            String regNo = student.get("regNo") != null ? ((String) student.get("regNo")).toLowerCase() : "";
            String course = (String) student.get("course");
            String semester = (String) student.get("semester");

            boolean matchesCourse = selectedCourse.equals("All Courses") || (course != null && course.equals(selectedCourse));
            boolean matchesSemester = selectedSemester.equals("All Semesters") || (semester != null && semester.equals(selectedSemester));
            boolean matchesSearch = name.contains(query) || regNo.contains(query);

            if (matchesCourse && matchesSemester && matchesSearch) {
                filteredList.add(student);
            }
        }

        // Sorting by regNo ascending
        Collections.sort(filteredList, (s1, s2) -> {
            String r1 = (String) s1.get("regNo");
            String r2 = (String) s2.get("regNo");
            if (r1 == null) r1 = "";
            if (r2 == null) r2 = "";
            return r1.compareTo(r2);
        });

        adapter.setFilteredList(filteredList);
    }

    private void exportFilteredDataToExcel() {
        List<Map<String, Object>> list = adapter.getCurrentList();
        if (list.isEmpty()) {
            Toast.makeText(this, "List is empty!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spinnerFilterCourse.getSelectedItem() == null || spinnerFilterSemester.getSelectedItem() == null) return;
        
        String selectedCourse = spinnerFilterCourse.getSelectedItem().toString();
        String selectedSemester = spinnerFilterSemester.getSelectedItem().toString();

        if (selectedCourse.equals("All Courses") || selectedSemester.equals("All Semesters")) {
            Toast.makeText(this, "Please select specific Course and Semester", Toast.LENGTH_SHORT).show();
            return;
        }

        fStore.collection("Curriculum").document("Subjects")
                .collection(selectedCourse).document(selectedSemester).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> subjectsList = (List<String>) documentSnapshot.get("subjects");
                        if (subjectsList != null) {
                            String[] subjects = subjectsList.toArray(new String[0]);
                            generateExcel(subjects, list, selectedCourse, selectedSemester);
                        }
                    } else {
                        Toast.makeText(this, "No subjects found for this batch", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void generateExcel(String[] subjects, List<Map<String, Object>> list, String selectedCourse, String selectedSemester) {
        StringBuilder htmlData = new StringBuilder();
        htmlData.append("<html><body><table border='1'>");
        
        // Header Row
        htmlData.append("<tr><th>Name</th><th>Reg No</th><th>Email</th><th>Phone</th>");
        for (String sub : subjects) {
            htmlData.append("<th>").append(sub).append(" Count</th>");
            htmlData.append("<th>").append(sub).append(" %</th>");
        }
        htmlData.append("</tr>");

        // Data Rows
        for (Map<String, Object> student : list) {
            String name = (String) student.get("fullName");
            String regNo = (String) student.get("regNo");
            String email = (String) student.get("email");
            String phone = (String) student.get("phone");
            String rawData = (String) student.get("uiAttendanceString");

            htmlData.append("<tr>");
            htmlData.append("<td>").append(name).append("</td>");
            htmlData.append("<td style='mso-number-format:\"\\@\"'>").append(regNo != null ? regNo : "N/A").append("</td>");
            htmlData.append("<td>").append(email).append("</td>");
            htmlData.append("<td>").append(phone).append("</td>");

            if (rawData == null || rawData.isEmpty() || rawData.equals("Calculating attendance...")) {
                for (int i = 0; i < subjects.length; i++) {
                    htmlData.append("<td style='mso-number-format:\"\\@\"'>0/0</td><td>0%</td>");
                }
            } else {
                for (String sub : subjects) {
                    Pattern pattern = Pattern.compile(Pattern.quote(sub) + ":\\s*(\\d+/\\d+)\\s*\\((\\d+)%\\)");
                    Matcher matcher = pattern.matcher(rawData);
                    if (matcher.find()) {
                        String count = matcher.group(1);
                        int percent = Integer.parseInt(matcher.group(2));
                        
                        String colorCode = "#FFFFFF"; // Default white
                        if (percent < 50) {
                            colorCode = "#FF9999"; // Red
                        } else if (percent < 75) {
                            colorCode = "#FFFF99"; // Yellow
                        }

                        // Apply mso-number-format:"\@" to force text mode for fractions
                        htmlData.append("<td style='mso-number-format:\"\\@\"'>").append(count).append("</td>");
                        htmlData.append("<td style='background-color:").append(colorCode).append("'>")
                                .append(percent).append("%</td>");
                    } else {
                        htmlData.append("<td style='mso-number-format:\"\\@\"'>0/0</td><td>0%</td>");
                    }
                }
            }
            htmlData.append("</tr>");
        }

        htmlData.append("</table></body></html>");

        try {
            String fileName = selectedCourse + "_" + selectedSemester + "_Attendance_Report.xls";
            File file = new File(getExternalFilesDir(null), fileName);
            FileOutputStream out = new FileOutputStream(file);
            out.write(htmlData.toString().getBytes());
            out.close();
            openExcelFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Export error", e);
            Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openExcelFile(File file) {
        Uri path = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/vnd.ms-excel");
        intent.putExtra(Intent.EXTRA_STREAM, path);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Attendance Report"));
    }
}