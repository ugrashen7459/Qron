package com.qron.app;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentReportAdapter extends RecyclerView.Adapter<StudentReportAdapter.ViewHolder> {

    private List<Map<String, Object>> userList;
    private FirebaseFirestore fStore = FirebaseFirestore.getInstance();
    private String currentUserRole = null;

    public StudentReportAdapter(List<Map<String, Object>> userList) {
        this.userList = userList;
        fetchCurrentUserRole();
    }

    private void fetchCurrentUserRole() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            fStore.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    currentUserRole = documentSnapshot.getString("role");
                    notifyDataSetChanged();
                }
            });
        }
    }

    public void setFilteredList(List<Map<String, Object>> filteredList) {
        this.userList = filteredList;
        notifyDataSetChanged();
    }

    public List<Map<String, Object>> getCurrentList() {
        return userList != null ? userList : new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> user = userList.get(position);
        String name = (String) user.get("fullName");
        String regNo = (String) user.get("regNo");
        String email = (String) user.get("email");
        String course = (String) user.get("course");
        String semester = (String) user.get("semester");
        String userId = (String) user.get("userId");
        String role = (String) user.get("role");

        if (regNo == null || regNo.isEmpty()) regNo = "N/A";

        if ("Student".equalsIgnoreCase(role)) {
            holder.txtStudentName.setText(name + " (Reg: " + regNo + ")");
        } else {
            holder.txtStudentName.setText(name);
        }
        
        holder.txtEmail.setText(email);

        if ("Teacher".equals(role)) {
            holder.txtAttendanceSummary.setText("Role: Teacher");
            holder.btnResetDevice.setVisibility(View.GONE);
            holder.btnEditUser.setVisibility(View.GONE);
            holder.btnDeleteUser.setVisibility(View.GONE);
        } else {
            // Role-based visibility for management buttons
            if ("Admin".equals(currentUserRole)) {
                holder.btnResetDevice.setVisibility(View.VISIBLE);
                holder.btnEditUser.setVisibility(View.VISIBLE);
                holder.btnDeleteUser.setVisibility(View.VISIBLE);
            } else if ("Teacher".equals(currentUserRole)) {
                holder.btnResetDevice.setVisibility(View.VISIBLE); // Teachers can reset device ID
                holder.btnEditUser.setVisibility(View.GONE);
                holder.btnDeleteUser.setVisibility(View.GONE);
            } else {
                holder.btnResetDevice.setVisibility(View.GONE);
                holder.btnEditUser.setVisibility(View.GONE);
                holder.btnDeleteUser.setVisibility(View.GONE);
            }
            
            String existingSummary = (String) user.get("uiAttendanceString");
            if (existingSummary != null && !existingSummary.equals("Calculating attendance...")) {
                holder.txtAttendanceSummary.setText(existingSummary);
            } else {
                holder.txtAttendanceSummary.setText("Calculating attendance...");
                calculateAttendance(user, email, course, semester, holder.txtAttendanceSummary);
            }
        }

        holder.btnResetDevice.setOnClickListener(v -> showResetDialog(v.getContext(), userId, name));
        holder.btnEditUser.setOnClickListener(v -> showEditDialog(v.getContext(), user, position));
        holder.btnDeleteUser.setOnClickListener(v -> showDeleteDialog(v.getContext(), userId, name, position));
    }

    private void showDeleteDialog(Context context, String userId, String name, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Student?")
                .setMessage("Are you sure you want to delete " + name + "? This cannot be undone.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    if (userId != null) {
                        fStore.collection("users").document(userId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(context, "Student Deleted Successfully", Toast.LENGTH_SHORT).show();
                                    
                                    String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                                    Map<String, Object> user = userList.get(position);
                                    String regNo = (String) user.get("regNo");
                                    SheetLogger.logToSheet(adminEmail, "Student Deleted", "Removed " + name + " (RegNo: " + (regNo != null ? regNo : "N/A") + ")");

                                    userList.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, userList.size());
                                })
                                .addOnFailureListener(e -> Toast.makeText(context, "Delete Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(Context context, Map<String, Object> user, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Student Details");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText editName = new EditText(context);
        editName.setHint("Name");
        editName.setText((String) user.get("fullName"));
        layout.addView(editName);

        final EditText editRegNo = new EditText(context);
        editRegNo.setHint("Registration Number");
        editRegNo.setText((String) user.get("regNo"));
        layout.addView(editRegNo);

        final EditText editPhone = new EditText(context);
        editPhone.setHint("Mobile Number");
        editPhone.setText((String) user.get("phone"));
        layout.addView(editPhone);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String newName = editName.getText().toString();
            String newRegNo = editRegNo.getText().toString();
            String newPhone = editPhone.getText().toString();
            String userId = (String) user.get("userId");

            if (!TextUtils.isEmpty(newName) && !TextUtils.isEmpty(newRegNo)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", newName);
                updates.put("regNo", newRegNo);
                updates.put("phone", newPhone);

                fStore.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(context, "Student Details Updated", Toast.LENGTH_SHORT).show();
                            user.put("fullName", newName);
                            user.put("regNo", newRegNo);
                            user.put("phone", newPhone);
                            notifyItemChanged(position);
                        })
                        .addOnFailureListener(e -> Toast.makeText(context, "Update Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(context, "Name and RegNo cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResetDialog(Context context, String userId, String name) {
        new AlertDialog.Builder(context)
                .setTitle("Reset Device Lock")
                .setMessage("Reset device lock for " + name + "? This will allow the student to register on a new phone.")
                .setPositiveButton("YES", (dialog, which) -> {
                    if (userId != null) {
                        fStore.collection("users").document(userId)
                                .update("deviceId", null)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(context, "Device ID Reset Successfully", Toast.LENGTH_SHORT).show();
                                    
                                    String userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                                    // Try to find regNo from userList for better logging
                                    String regNo = "N/A";
                                    for (Map<String, Object> user : userList) {
                                        if (userId.equals(user.get("userId"))) {
                                            regNo = (String) user.get("regNo");
                                            break;
                                        }
                                    }
                                    SheetLogger.logToSheet(userEmail, "Device Reset", "Reset Lock for " + name + " (RegNo: " + regNo + ")");
                                })
                                .addOnFailureListener(e -> Toast.makeText(context, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("NO", null)
                .show();
    }

    public void calculateAttendance(Map<String, Object> user, String email, String course, String semester, TextView summaryView) {
        if (course == null || semester == null) {
            if (summaryView != null) summaryView.setText("No course/semester assigned.");
            return;
        }

        fStore.collection("class_sessions")
                .whereEqualTo("course", course)
                .whereEqualTo("semester", semester)
                .get()
                .addOnSuccessListener(totalClassesSnapshot -> {
                    Map<String, Integer> totalClassesPerSubject = new HashMap<>();
                    for (DocumentSnapshot doc : totalClassesSnapshot.getDocuments()) {
                        String sub = doc.getString("subject");
                        if (sub != null) {
                            totalClassesPerSubject.put(sub, totalClassesPerSubject.getOrDefault(sub, 0) + 1);
                        }
                    }

                    if (totalClassesPerSubject.isEmpty()) {
                        String msg = "No classes held for this batch.";
                        if (summaryView != null) summaryView.setText(msg);
                        user.put("uiAttendanceString", msg);
                        return;
                    }

                    fStore.collection("attendance")
                            .whereEqualTo("studentEmail", email)
                            .get()
                            .addOnSuccessListener(attendanceSnapshot -> {
                                Map<String, Integer> attendedClassesPerSubject = new HashMap<>();
                                for (DocumentSnapshot doc : attendanceSnapshot.getDocuments()) {
                                    String sub = doc.getString("subject");
                                    if (sub != null) {
                                        attendedClassesPerSubject.put(sub, attendedClassesPerSubject.getOrDefault(sub, 0) + 1);
                                    }
                                }

                                StringBuilder summary = new StringBuilder();
                                for (String subject : totalClassesPerSubject.keySet()) {
                                    int total = totalClassesPerSubject.get(subject);
                                    int attended = attendedClassesPerSubject.getOrDefault(subject, 0);
                                    double percentage = ((double) attended / total) * 100;
                                    
                                    if (summary.length() > 0) summary.append(" | ");
                                    summary.append(String.format(Locale.getDefault(), "%s: %d/%d (%.0f%%)", subject, attended, total, percentage));
                                }
                                String finalSummary = summary.toString();
                                if (summaryView != null) summaryView.setText(finalSummary);
                                
                                user.put("uiAttendanceString", finalSummary);
                            });
                });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtStudentName, txtEmail, txtAttendanceSummary;
        ImageButton btnResetDevice, btnEditUser, btnDeleteUser;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtStudentName = itemView.findViewById(R.id.txtStudentName);
            txtEmail = itemView.findViewById(R.id.txtEmail);
            txtAttendanceSummary = itemView.findViewById(R.id.txtAttendanceSummary);
            btnResetDevice = itemView.findViewById(R.id.btnResetDevice);
            btnEditUser = itemView.findViewById(R.id.btnEditUser);
            btnDeleteUser = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}
