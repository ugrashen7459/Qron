package com.qron.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<SessionModel> sessionList;
    private OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(SessionModel session);
    }

    public SessionAdapter(List<SessionModel> sessionList, OnDeleteClickListener deleteClickListener) {
        this.sessionList = sessionList;
        this.deleteClickListener = deleteClickListener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        SessionModel session = sessionList.get(position);
        holder.tvSubjectName.setText(session.getSubjectName());
        holder.tvStudentCount.setText("Students: " + session.getStudentCount());

        holder.btnDeleteSession.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                deleteClickListener.onDeleteClick(session);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    public static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectName, tvStudentCount;
        ImageButton btnDeleteSession;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvStudentCount = itemView.findViewById(R.id.tvStudentCount);
            btnDeleteSession = itemView.findViewById(R.id.btnDeleteSession);
        }
    }
}
