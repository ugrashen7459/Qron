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
            fStore.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    currentUserRole = doc.getString("role");
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
            if ("Admin".equals(currentUserRole)) {
                holder.btnResetDevice.setVisibility(View.GONE); // Disabled for teachers
                holder.btnEditUser.setVisibility(View.VISIBLE);
                holder.btnDeleteUser.setVisibility(View.VISIBLE);
            } else {
                holder.btnResetDevice.setVisibility(View.GONE);
                holder.btnEditUser.setVisibility(View.GONE);
                holder.btnDeleteUser.setVisibility(View.GONE);
            }
        } else {
            // Role-based visibility for management buttons for Students
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
        holder.btnDeleteUser.setOnClickListener(v -> showDeleteDialog(v.getContext(), user, position));
    }

    private void showDeleteDialog(Context context, Map<String, Object> user, int position) {
        String role = (String) user.get("role");
        String name = (String) user.get("fullName");
        String userId = (String) user.get("userId");

        new AlertDialog.Builder(context)
                .setTitle("Delete " + role + "?")
                .setMessage("Are you sure you want to delete " + name + "? This cannot be undone.")
                .setPositiveButton("Yes, Delete", (dialog, which) -> {
                    if (userId != null) {
                        fStore.collection("users").document(userId)
                                .delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(context, role + " Deleted Successfully", Toast.LENGTH_SHORT).show();
                                    
                                    String adminEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "Unknown";
                                    String regNo = (String) user.get("regNo");
                                    SheetLogger.logToSheet(adminEmail, role + " Deleted", "Removed " + name + (regNo != null ? " (" + regNo + ")" : ""));

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
        String role = (String) user.get("role");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit " + role + " Details");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText editName = new EditText(context);
        editName.setHint("Name");
        editName.setText((String) user.get("fullName"));
        layout.addView(editName);

        final EditText editRegNo = new EditText(context);
        editRegNo.setHint("Registration Number");
        if ("Student".equalsIgnoreCase(role)) {
            editRegNo.setText((String) user.get("regNo"));
            layout.addView(editRegNo);
        }

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

            if (!TextUtils.isEmpty(newName)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", newName);
                if ("Student".equalsIgnoreCase(role)) {
                    updates.put("regNo", newRegNo);
                }
                updates.put("phone", newPhone);

                fStore.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(context, role + " Details Updated", Toast.LENGTH_SHORT).show();
                            user.put("fullName", newName);
                            if ("Student".equalsIgnoreCase(role)) {
                                user.put("regNo", newRegNo);
                            }
                            user.put("phone", newPhone);
                            notifyItemChanged(position);
                        })
                        .addOnFailureListener(e -> Toast.makeText(context, "Update Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResetDialog(Context context, String userId, String name) {
        new AlertDialog.Builder(context)
                .setTitle("Reset Device Lock")
                .setMessage("Reset device lock for " + name + "? This will allow them to register on a new phone.")
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

    public void calculateAttendance(Map<String, Object> user, String email, String course, String sem, TextView view) {
        if (course == null || sem == null) {
            if (view != null) view.setText("No batch info");
            return;
        }

        fStore.collection("class_sessions")
                .whereEqualTo("course", course)
                .whereEqualTo("semester", sem)
                .get()
                .addOnSuccessListener(snapTotal -> {
                    Map<String, Integer> totalMap = new HashMap<>();
                    for (DocumentSnapshot doc : snapTotal.getDocuments()) {
                        String sub = doc.getString("subject");
                        if (sub != null) totalMap.put(sub, totalMap.getOrDefault(sub, 0) + 1);
                    }

                    if (totalMap.isEmpty()) {
                        String m = "No classes found";
                        if (view != null) view.setText(m);
                        user.put("uiAttendanceString", m);
                        return;
                    }

                    fStore.collection("attendance")
                            .whereEqualTo("studentEmail", email)
                            .get()
                            .addOnSuccessListener(snapAtt -> {
                                Map<String, Integer> attMap = new HashMap<>();
                                for (DocumentSnapshot doc : snapAtt.getDocuments()) {
                                    String sub = doc.getString("subject");
                                    if (sub != null) attMap.put(sub, attMap.getOrDefault(sub, 0) + 1);
                                }

                                StringBuilder sb = new StringBuilder();
                                for (String sub : totalMap.keySet()) {
                                    int t = totalMap.get(sub);
                                    int a = attMap.getOrDefault(sub, 0);
                                    double p = ((double) a / t) * 100;
                                    if (sb.length() > 0) sb.append(" | ");
                                    sb.append(String.format(Locale.getDefault(), "%s: %d/%d (%.0f%%)", sub, a, t, p));
                                }
                                String res = sb.toString();
                                if (view != null) view.setText(res);
                                user.put("uiAttendanceString", res);
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
