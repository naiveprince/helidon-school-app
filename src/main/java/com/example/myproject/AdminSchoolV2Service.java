package com.example.myproject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.HttpRouting;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminSchoolV2Service {

    private static final String ADMIN_TOKEN_ENV = "ADMIN_API_TOKEN";

    private AdminSchoolV2Service() {
    }

    public static void register(HttpRouting.Builder routing) {
        routing.post("/admin/api/schools-v2/upload", (req, res) -> {
            try {
                String token = req.headers()
                        .first(HeaderNames.create("X-ADMIN-TOKEN"))
                        .orElse("");

                if (!isAuthorized(token)) {
                    res.status(401);
                    res.header("Content-Type", "application/json; charset=UTF-8");
                    res.send("""
                        {"success":false,"message":"Unauthorized"}
                        """);
                    return;
                }

                String body = req.content().as(String.class);

                ObjectMapper mapper = new ObjectMapper();

                List<Map<String, Object>> rawRows = mapper.readValue(
                        body.getBytes(StandardCharsets.UTF_8),
                        new TypeReference<List<Map<String, Object>>>() {}
                );

                List<SchoolV2> schools = new ArrayList<>();
                long id = 1L;

                for (Map<String, Object> row : rawRows) {
                    String schoolName = firstNonBlank(
                            row.get("schoolName"),
                            row.get("name"),
                            row.get("school_name")
                    );

                    String category = firstNonBlank(
                            row.get("category")
                    );

                    String capacity = firstNonBlank(
                            row.get("capacity")
                    );

                    String examDates = firstNonBlank(
                            row.get("examDates"),
                            row.get("exam_dates")
                    );

                    String subjects = firstNonBlank(
                            row.get("subjects")
                    );

                    String alternateSubjects = firstNonBlank(
                            row.get("alternateSubjects"),
                            row.get("alternate_subjects")
                    );

                    String interview = firstNonBlank(
                            row.get("interview")
                    );

                    String englishQualificationBenefit = firstNonBlank(
                            row.get("englishQualificationBenefit"),
                            row.get("english_qualification_benefit")
                    );

                    String notes = firstNonBlank(
                            row.get("notes")
                    );

                    String infoLink = firstNonBlank(
                            row.get("infoLink"),
                            row.get("info_link")
                    );

                    // 完全な空行は無視
                    if (isAllBlank(
                            schoolName, category, capacity, examDates, subjects,
                            alternateSubjects, interview, englishQualificationBenefit,
                            notes, infoLink
                    )) {
                        continue;
                    }

                    SchoolV2 s = new SchoolV2();
                    s.id = id++;
                    s.schoolName = schoolName;
                    s.category = category;
                    s.capacity = capacity;
                    s.examDates = examDates;
                    s.subjects = subjects;
                    s.alternateSubjects = alternateSubjects;
                    s.interview = interview;
                    s.englishQualificationBenefit = englishQualificationBenefit;
                    s.notes = notes;
                    s.infoLink = infoLink;

                    schools.add(s);
                }

                SchoolV2JsonStore jsonStore = new SchoolV2JsonStore(DbInit.getExternalJsonPath());
                jsonStore.replaceAll(schools);
                DbInit.reloadFromJson();

                res.header("Content-Type", "application/json; charset=UTF-8");
                res.send("""
                    {"success":true,"message":"schools-v2 uploaded and reloaded successfully"}
                    """);

            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                res.header("Content-Type", "application/json; charset=UTF-8");
                String msg = escapeJson(e.getMessage());
                res.send("{\"success\":false,\"message\":\"" + msg + "\"}");
            }
        });
    }

    private static boolean isAuthorized(String token) {
        String expected = System.getenv(ADMIN_TOKEN_ENV);
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return expected.equals(token);
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static boolean isAllBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
