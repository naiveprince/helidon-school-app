package com.example.myproject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class SchoolV2Repository {
    private static final String URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public List<SchoolV2> findAll() {
        List<SchoolV2> result = new ArrayList<>();

        String sql = """
                SELECT id, school_name, category, capacity, exam_dates,
                       subjects, alternate_subjects, interview,
                       english_qualification_benefit, notes, info_link
                FROM exam_school_v2
                ORDER BY id
                """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public List<SchoolV2> findByName(String name) {
        List<SchoolV2> result = new ArrayList<>();

        String sql = """
                SELECT id, school_name, category, capacity, exam_dates,
                       subjects, alternate_subjects, interview,
                       english_qualification_benefit, notes, info_link
                FROM exam_school_v2
                WHERE school_name = ?
                ORDER BY id
                """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public SchoolV2 findById(long id) {
        String sql = """
                SELECT id, school_name, category, capacity, exam_dates,
                       subjects, alternate_subjects, interview,
                       english_qualification_benefit, notes, info_link
                FROM exam_school_v2
                WHERE id = ?
                """;

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void reloadFromJson() {
        DbInit.reloadFromJson();
    }

    private SchoolV2 mapRow(ResultSet rs) throws Exception {
        return new SchoolV2(
                rs.getLong("id"),
                rs.getString("school_name"),
                rs.getString("category"),
                rs.getString("capacity"),
                rs.getString("exam_dates"),
                rs.getString("subjects"),
                rs.getString("alternate_subjects"),
                rs.getString("interview"),
                rs.getString("english_qualification_benefit"),
                rs.getString("notes"),
                rs.getString("info_link")
        );
    }
}
