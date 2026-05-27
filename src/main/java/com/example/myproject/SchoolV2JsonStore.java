package com.example.myproject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SchoolV2JsonStore {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path jsonPath;

    public SchoolV2JsonStore(Path jsonPath) {
        this.jsonPath = jsonPath;
    }

    public List<SchoolV2> loadAll() {
        try {
            if (!Files.exists(jsonPath)) {
                return new ArrayList<>();
            }

            return mapper.readValue(
                    jsonPath.toFile(),
                    new TypeReference<List<SchoolV2>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON from " + jsonPath, e);
        }
    }

    public void saveAll(List<SchoolV2> schools) {
        try {
            Files.createDirectories(jsonPath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), schools);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save JSON to " + jsonPath, e);
        }
    }

    public void replaceAll(List<SchoolV2> schools) {
        validate(schools);
        saveAll(schools);
    }

    private void validate(List<SchoolV2> schools) {
        if (schools == null) {
            throw new IllegalArgumentException("schools must not be null");
        }

        for (int i = 0; i < schools.size(); i++) {
            SchoolV2 s = schools.get(i);
            if (s == null) {
                throw new IllegalArgumentException("Record " + i + " is null");
            }
            if (isBlank(s.schoolName)) {
                throw new IllegalArgumentException("Record " + i + ": schoolName is required");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}