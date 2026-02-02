package com.qron.app;

public class SessionModel {
    private String subjectName;
    private long studentCount;
    private String sessionId;

    public SessionModel() {
    }

    public SessionModel(String subjectName, long studentCount, String sessionId) {
        this.subjectName = subjectName;
        this.studentCount = studentCount;
        this.sessionId = sessionId;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public long getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(long studentCount) {
        this.studentCount = studentCount;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
