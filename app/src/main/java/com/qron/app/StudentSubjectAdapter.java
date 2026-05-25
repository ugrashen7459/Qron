package com.qron.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class StudentSubjectAdapter extends RecyclerView.Adapter<StudentSubjectAdapter.ViewHolder> {

    private List<AttendanceItem> list;

    public StudentSubjectAdapter(List<AttendanceItem> list) {
        this.list = list;
    }

    public void setList(List<AttendanceItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceItem item = list.get(position);
        holder.tvSubjectName.setText(item.subject);
        holder.tvAttendanceCount.setText(String.format(Locale.getDefault(), "Present: %d/%d", item.attended, item.total));
        holder.tvPercentage.setText(String.format(Locale.getDefault(), "%.0f%%", item.percentage));
        
        if (item.percentage < 75) {
            holder.tvPercentage.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        } else {
            holder.tvPercentage.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.colorPrimary));
        }
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    public static class AttendanceItem {
        String subject;
        int attended;
        int total;
        double percentage;

        public AttendanceItem(String subject, int attended, int total) {
            this.subject = subject;
            this.attended = attended;
            this.total = total;
            this.percentage = total > 0 ? ((double) attended / total) * 100 : 0.0;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectName, tvAttendanceCount, tvPercentage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvAttendanceCount = itemView.findViewById(R.id.tvAttendanceCount);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
        }
    }
}
