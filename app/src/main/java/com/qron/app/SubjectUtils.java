package com.qron.app;

public class SubjectUtils {

    public static String[] getSubjects(String course, String semester) {
        if (course == null || semester == null) return new String[0];

        String key = course + " " + semester;
        switch (key) {
            case "BCA Semester 1":
                return new String[]{"C Programming", "Maths", "English", "Digital Electronics"};
            case "BCA Semester 3":
                return new String[]{"C++", "Java", "Data Structures", "OS"};
            case "BCA Semester 5":
                return new String[]{"Python", "Cloud Computing", "AI", "Project"};
            case "MCA Semester 1":
                return new String[]{"Advanced Java", "DBMS", "Networking", "Stats"};
            case "MCA Semester 3":
                return new String[]{"ML", "Big Data", "IoT", "Thesis"};
            case "B-Tech Semester 1":
                return new String[]{"Physics", "Chemistry", "Maths-1", "English"};
            case "B-Tech Semester 7":
            case "B-Tech Semester 8":
                return new String[]{"VLSI", "Neural Networks", "Robotics", "Internship"};
            case "BBA Semester 1":
                return new String[]{"Business Ethics", "Economics", "Accounts"};
            case "BBA Semester 3":
                return new String[]{"Management", "Accounting", "HR"};
            case "BBA Semester 5":
                return new String[]{"Strategic Mgmt", "Marketing", "Project"};
            case "BSc Semester 1":
                return new String[]{"Physics", "Chemistry", "Mathematics"};
            case "BSc Semester 3":
                return new String[]{"Organic Chemistry", "Thermodynamics", "Optics"};
            case "BSc Semester 5":
                return new String[]{"Quantum Physics", "Nuclear Chemistry", "Statistics"};
            default:
                return new String[]{"Subject-1", "Subject-2", "Subject-3", "Elective"};
        }
    }
}
