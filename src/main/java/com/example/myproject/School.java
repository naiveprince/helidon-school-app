package com.example.myproject;

public class School {
    public long id;
    public String examDate;
    public String schoolName;
    public String className;
    public String capacity;
    public String subjects;
    public String englishOnly;
    public String applicationDeadlineDocs;
    public String benefits;
    public String remarks;

    public School(long id,
                  String examDate,
                  String schoolName,
                  String className,
                  String capacity,
                  String subjects,
                  String englishOnly,
                  String applicationDeadlineDocs,
                  String benefits,
                  String remarks) {
        this.id = id;
        this.examDate = examDate;
        this.schoolName = schoolName;
        this.className = className;
        this.capacity = capacity;
        this.subjects = subjects;
        this.englishOnly = englishOnly;
        this.applicationDeadlineDocs = applicationDeadlineDocs;
        this.benefits = benefits;
        this.remarks = remarks;
    }
}
