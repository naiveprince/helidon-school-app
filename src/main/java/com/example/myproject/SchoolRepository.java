package com.example.myproject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SchoolRepository {
    private static final String URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public List<School> findAll() {
        List<School> result = new ArrayList<>();

        String sql = """
                SELECT id, exam_date, school_name, class_name, capacity,
                       subjects, english_only, application_deadline_docs,
                       benefits, remarks
                FROM exam_school
                ORDER BY id
                """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(new School(
                        rs.getLong("id"),
                        rs.getString("exam_date"),
                        rs.getString("school_name"),
                        rs.getString("class_name"),
                        rs.getString("capacity"),
                        rs.getString("subjects"),
                        rs.getString("english_only"),
                        rs.getString("application_deadline_docs"),
                        rs.getString("benefits"),
                        rs.getString("remarks")
                ));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public List<School> findByName(String name) {
        List<School> result = new ArrayList<>();

        String sql = """
                SELECT id, exam_date, school_name, class_name, capacity,
                       subjects, english_only, application_deadline_docs,
                       benefits, remarks
                FROM exam_school
                WHERE school_name = ?
                ORDER BY id
                """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new School(
                            rs.getLong("id"),
                            rs.getString("exam_date"),
                            rs.getString("school_name"),
                            rs.getString("class_name"),
                            rs.getString("capacity"),
                            rs.getString("subjects"),
                            rs.getString("english_only"),
                            rs.getString("application_deadline_docs"),
                            rs.getString("benefits"),
                            rs.getString("remarks")
                    ));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }
}
