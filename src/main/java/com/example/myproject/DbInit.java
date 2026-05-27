package com.example.myproject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class DbInit {
    private static final String JDBC_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASSWORD = "";

    // 開発中はここを使う
    private static final Path EXTERNAL_JSON_PATH =
            Paths.get(System.getProperty("user.dir"), "data", "schools-v2.json");

    // フォールバック用（src/main/resources/data/schools-v2.json）
    private static final String CLASSPATH_JSON = "data/schools-v2.json";

    public static void init() {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             Statement stmt = conn.createStatement()) {

            createTableIfNeeded(stmt);
            clearSchoolV2Table(stmt);
            loadSchoolV2FromJson(stmt);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("DbInit.init() failed", e);
        }
    }

    public static void reloadFromJson() {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             Statement stmt = conn.createStatement()) {

            createTableIfNeeded(stmt);
            clearSchoolV2Table(stmt);
            loadSchoolV2FromJson(stmt);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("DbInit.reloadFromJson() failed", e);
        }
    }

    private static void createTableIfNeeded(Statement stmt) throws Exception {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS exam_school_v2 (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                school_name VARCHAR(300),
                category VARCHAR(300),
                capacity VARCHAR(100),
                exam_dates CLOB,
                subjects CLOB,
                alternate_subjects CLOB,
                interview VARCHAR(100),
                english_qualification_benefit CLOB,
                notes CLOB,
                info_link CLOB
            )
        """);
    }

    private static void clearSchoolV2Table(Statement stmt) throws Exception {
        stmt.execute("DELETE FROM exam_school_v2");
        stmt.execute("ALTER TABLE exam_school_v2 ALTER COLUMN id RESTART WITH 1");
    }

    private static void loadSchoolV2FromJson(Statement stmt) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> schools;

        System.out.println("user.dir = " + System.getProperty("user.dir"));
        System.out.println("external path = " + EXTERNAL_JSON_PATH.toAbsolutePath());
        System.out.println("external exists = " + Files.exists(EXTERNAL_JSON_PATH));

        if (Files.exists(EXTERNAL_JSON_PATH)) {
            schools = mapper.readValue(
                    EXTERNAL_JSON_PATH.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            System.out.println("Loaded schools-v2 from external file: " + EXTERNAL_JSON_PATH);
        } else {
            try (InputStream is = DbInit.class.getClassLoader().getResourceAsStream(CLASSPATH_JSON)) {
                if (is == null) {
                    throw new RuntimeException("JSON not found: external=" + EXTERNAL_JSON_PATH + ", classpath=" + CLASSPATH_JSON);
                }

                schools = mapper.readValue(
                        is,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                System.out.println("Loaded schools-v2 from classpath: " + CLASSPATH_JSON);
            }
        }

        for (Map<String, Object> school : schools) {
            insertV2(stmt,
                    asString(school.get("schoolName")),
                    asString(school.get("category")),
                    asString(school.get("capacity")),
                    asString(school.get("examDates")),
                    asString(school.get("subjects")),
                    asString(school.get("alternateSubjects")),
                    asString(school.get("interview")),
                    asString(school.get("englishQualificationBenefit")),
                    asString(school.get("notes")),
                    asString(school.get("infoLink"))
            );
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static void insertV2(Statement stmt,
                                 String schoolName,
                                 String category,
                                 String capacity,
                                 String examDates,
                                 String subjects,
                                 String alternateSubjects,
                                 String interview,
                                 String englishQualificationBenefit,
                                 String notes,
                                 String infoLink) throws Exception {
        stmt.execute("INSERT INTO exam_school_v2 ("
                + "school_name, category, capacity, exam_dates, "
                + "subjects, alternate_subjects, interview, "
                + "english_qualification_benefit, notes, info_link"
                + ") VALUES ("
                + q(schoolName) + ", "
                + q(category) + ", "
                + q(capacity) + ", "
                + q(examDates) + ", "
                + q(subjects) + ", "
                + q(alternateSubjects) + ", "
                + q(interview) + ", "
                + q(englishQualificationBenefit) + ", "
                + q(notes) + ", "
                + q(infoLink)
                + ")");
    }

    private static String q(String s) {
        if (s == null) {
            return "NULL";
        }
        return "'" + s.replace("'", "''") + "'";
    }

    public static Path getExternalJsonPath() {
        return EXTERNAL_JSON_PATH;
    }
}
