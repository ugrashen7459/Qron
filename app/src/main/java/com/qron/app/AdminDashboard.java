package com.qron.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminDashboard extends AppCompatActivity {

    Button btnAddTeacher, btnAddStudent, btnLogout, btnViewStudents, btnViewTeachers, btnPromoteStudents, btnDeleteSession, btnEditPastAttendance, btnManageCurriculum;
    SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        btnAddTeacher = findViewById(R.id.btnAddTeacher);
        btnAddStudent = findViewById(R.id.btnAddStudent);
        btnLogout = findViewById(R.id.btnLogout);
        btnViewStudents = findViewById(R.id.btnViewStudents);
        btnViewTeachers = findViewById(R.id.btnViewTeachers);
        btnPromoteStudents = findViewById(R.id.btnPromoteStudents);
        btnDeleteSession = findViewById(R.id.btnDeleteSession);
        btnEditPastAttendance = findViewById(R.id.btnEditPastAttendance);
        btnManageCurriculum = findViewById(R.id.btnManageCurriculum);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> {
            loadData();
            new Handler().postDelayed(() -> swipeRefresh.setRefreshing(false), 2000);
        });

        btnAddTeacher.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboard.this, RegisterUser.class);
            intent.putExtra("role", "Teacher");
            startActivity(intent);
        });

        btnAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboard.this, RegisterUser.class);
            intent.putExtra("role", "Student");
            startActivity(intent);
        });

        btnViewStudents.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, ViewStudentsActivity.class)));

        btnViewTeachers.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, AdminViewTeachersActivity.class)));

        btnPromoteStudents.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, PromoteStudentsActivity.class)));

        btnDeleteSession.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, DeleteSessionActivity.class)));

        btnEditPastAttendance.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, EditPastAttendanceActivity.class)));

        btnManageCurriculum.setOnClickListener(v -> startActivity(new Intent(AdminDashboard.this, ManageCurriculumActivity.class)));

        btnLogout.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String email = (user != null) ? user.getEmail() : "Unknown";
            SheetLogger.logToSheet(email, "Logout", "Admin logged out");

            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(AdminDashboard.this, Login.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        // Admin dashboard is mostly static buttons
    }
}
