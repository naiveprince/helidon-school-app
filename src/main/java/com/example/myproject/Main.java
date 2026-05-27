package com.example.myproject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import io.helidon.http.HeaderNames;
import io.helidon.common.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {

    private static final String LOGIN_COOKIE_NAME = "school_app_auth";

    private Main() {
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();
        Services.set(Config.class, config);

        DbInit.init();

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(Main::routing)
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/schools");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    private static String formatDateForDisplay(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.replaceAll("(\\d{4})-(\\d{2})-(\\d{2})", "$1/$2/$3");
    }

private static String extractGeminiText(String json) {
    if (json == null || json.isBlank()) {
        return "Gemini response was empty.";
    }

    String marker = "\"text\": \"";
    int start = json.indexOf(marker);
    if (start < 0) {
        return "Could not extract text from Gemini response: " + json;
    }

    start += marker.length();
    StringBuilder sb = new StringBuilder();
    boolean escaped = false;

    for (int i = start; i < json.length(); i++) {
        char c = json.charAt(i);

        if (escaped) {
            switch (c) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append(c);
            }
            escaped = false;
            continue;
        }

        if (c == '\\') {
            escaped = true;
            continue;
        }

        if (c == '"') {
            break;
        }

        sb.append(c);
    }

    return sb.toString();
}

private static String callGemini(String prompt) throws Exception {
    String apiKey = System.getenv("GEMINI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalStateException("GEMINI_API_KEY is not set.");
    }

    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    URL url = new URL(endpoint);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setRequestMethod("POST");
    conn.setRequestProperty("x-goog-api-key", apiKey);
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    conn.setDoOutput(true);

    String safePrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");

    String requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "%s"
                    }
                  ]
                }
              ]
            }
            """.formatted(safePrompt);

    try (OutputStream os = conn.getOutputStream()) {
        os.write(requestBody.getBytes(StandardCharsets.UTF_8));
    }

    int status = conn.getResponseCode();
    InputStream is = (status >= 200 && status < 300)
            ? conn.getInputStream()
            : conn.getErrorStream();

    byte[] bytes = is.readAllBytes();
    String responseBody = new String(bytes, StandardCharsets.UTF_8);

    if (status < 200 || status >= 300) {
        throw new RuntimeException("Gemini API error: HTTP " + status + " / " + responseBody);
    }

    return extractGeminiText(responseBody);
}

private static String jsonEscape(String s) {
    if (s == null) {
        return "";
    }
    return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
}

private static String buildSchoolDataPrompt(List<SchoolV2> schools, String question) {
    StringBuilder sb = new StringBuilder();

    sb.append("""
            You are an assistant helping recommend Japanese junior high schools based only on the provided school data.
            Do not invent schools or facts not contained in the data.
            Be cautious and say "候補" rather than guaranteeing admission.
            Return the answer in Japanese.
            For each recommended school, explain the reason based on the provided fields.
            
            User question:
            """).append(question).append("\n\n");

    sb.append("School data:\n");

    for (SchoolV2 s : schools) {
        sb.append("- 学校名: ").append(nullToEmpty(s.schoolName)).append("\n");
        sb.append("  入試分類: ").append(nullToEmpty(s.category)).append("\n");
        sb.append("  募集人数: ").append(nullToEmpty(s.capacity)).append("\n");
        sb.append("  試験日: ").append(nullToEmpty(s.examDates)).append("\n");
        sb.append("  試験科目: ").append(nullToEmpty(s.subjects)).append("\n");
        sb.append("  試験科目特記: ").append(nullToEmpty(s.alternateSubjects)).append("\n");
        sb.append("  面接有無: ").append(nullToEmpty(s.interview)).append("\n");
        sb.append("  英語資格優遇: ").append(nullToEmpty(s.englishQualificationBenefit)).append("\n");
        sb.append("  備考: ").append(nullToEmpty(s.notes)).append("\n");
        sb.append("  学校リンク: ").append(nullToEmpty(s.infoLink)).append("\n\n");
    }

    sb.append("""
            Please answer in this style:
            1. 最初に全体コメントを2〜4文で書く
            2. その後、「候補校:」として学校名を箇条書き
            3. 各学校ごとに理由を書く
            4. 最後に「注意点」を短く書く
            """);

    return sb.toString();
}

private static String nullToEmpty(String s) {
    return s == null ? "" : s;
}

    private static String getAppPassword() {
        return null;
        }


    private static String buildAuthToken() {
        return URLEncoder.encode(getAppPassword(), StandardCharsets.UTF_8);
    }

    private static boolean isAuthenticated(io.helidon.webserver.http.ServerRequest req) {
            return true;
        }


private static String loginPageHtml(String message) {
    String safeMessage = message == null ? "" : escapeHtml(message);

    return """
            <html>
            <head>
              <meta charset="UTF-8">
              <title>ログイン</title>
              <style>
                body {
                  font-family: Arial, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif;
                  background: #f7f7f7;
                  margin: 0;
                  padding: 0;
                }
                .login-box {
                  width: 420px;
                  max-width: calc(100% - 40px);
                  margin: 80px auto;
                  background: white;
                  border: 1px solid #ddd;
                  border-radius: 8px;
                  padding: 24px;
                  box-sizing: border-box;
                  box-shadow: 0 2px 10px rgba(0,0,0,0.08);
                }
                h2 {
                  margin-top: 0;
                  margin-bottom: 16px;
                }
                .message {
                  color: #c62828;
                  min-height: 1.2em;
                  margin-bottom: 12px;
                }
                input[type="password"] {
                  width: 100%;
                  padding: 10px;
                  font-size: 14px;
                  box-sizing: border-box;
                  margin-bottom: 12px;
                }
                button {
                  padding: 10px 16px;
                  font-size: 14px;
                  cursor: pointer;
                }
              </style>
            </head>
            <body>
              <div class="login-box">
                <h2>学校一覧サイト ログイン</h2>
                <div class="message">"""
            + safeMessage +
            """
                </div>
                <form method="GET" action="/do-login">
                  <input type="password" name="password" placeholder="パスワードを入力">
                  <button type="submit">ログイン</button>
                </form>
              </div>
            </body>
            </html>
            """;
}
    static void routing(HttpRouting.Builder routing) {
        SchoolRepository repository = new SchoolRepository();
        SchoolV2Repository repositoryV2 = new SchoolV2Repository();

        AdminSchoolV2Service.register(routing);
        routing
                .register("/greet", new GreetService())

                .get("/simple-greet", (req, res) -> res.send("Hello World!"))

.get("/ai/test", (req, res) -> {
            try {
                String answer = callGemini("Say hello in Japanese in a friendly way.");
                res.header("Content-Type", "application/json; charset=UTF-8");
                res.send("{\"answer\":\"" +
                        escapeHtml(answer)
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n") +
                        "\"}");
            } catch (Exception e) {
                res.status(500);
                res.send("AI test failed: " + e.getMessage());
            }
        })


                .get("/login", (req, res) -> {
                    String error = req.query().first("error").orElse("");
                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(loginPageHtml(error));
                })

                .get("/do-login", (req, res) -> {
                    String password = req.query().first("password").orElse("");

                    if (getAppPassword().equals(password)) {
                        res.header("Set-Cookie",
                                LOGIN_COOKIE_NAME + "=" + buildAuthToken() + "; Path=/; HttpOnly; SameSite=Lax");
                        res.status(302);
                        res.header("Location", "/schools-v2-table");
                        res.send();
                    } else {
                        String errorMessage = URLEncoder.encode("パスワードが違います。", StandardCharsets.UTF_8);
                        res.status(302);
                        res.header("Location", "/login?error=" + errorMessage);
                        res.send();
                    }
                })

.post("/ai/recommend-schools", (req, res) -> {
    try {
        String body = req.content().as(String.class);

        String question = body;
        String marker = "\"question\":\"";
        int start = body.indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            int end = body.indexOf("\"", start);
            if (end > start) {
                question = body.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\");
            }
        }

        List<SchoolV2> schools = repositoryV2.findAll();
        String prompt = buildSchoolDataPrompt(schools, question);
        String answer = callGemini(prompt);

        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{\"answer\":\"" + jsonEscape(answer) + "\"}");
    } catch (Exception e) {
        res.status(500);
        res.send("AI recommend failed: " + e.getMessage());
    }
}) 
               .get("/logout", (req, res) -> {
                    res.header("Set-Cookie",
                            LOGIN_COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                    res.status(302);
                    res.header("Location", "/login");
                    res.send();
                })

                .get("/schools", (req, res) -> {
                    String name = req.query().first("name").orElse(null);

                    if (name == null || name.isBlank()) {
                        List<School> schools = repository.findAll();
                        res.send(schools);
                    } else {
                        List<School> schools = repository.findByName(name);
                        res.send(schools);
                    }
                })

                .get("/schools-v2", (req, res) -> {
                    String name = req.query().first("name").orElse(null);

                    if (name == null || name.isBlank()) {
                        List<SchoolV2> schools = repositoryV2.findAll();
                        res.send(schools);
                    } else {
                        List<SchoolV2> schools = repositoryV2.findByName(name);
                        res.send(schools);
                    }
                })

.get("/api/version", (req, res) -> {
    try {
        Path jsonPath = DbInit.getExternalJsonPath();

        String dataVersion = "unknown";
        if (Files.exists(jsonPath)) {
            dataVersion = Files.getLastModifiedTime(jsonPath)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        int recordCount = repositoryV2.findAll().size();

        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{"
                + "\"dataVersion\":\"" + dataVersion + "\","
                + "\"recordCount\":" + recordCount
                + "}");
    } catch (Exception e) {
        e.printStackTrace();
        res.status(500);
        res.header("Content-Type", "application/json; charset=UTF-8");
        res.send("{\"success\":false,\"message\":\"Failed to get version info\"}");
    }
})

                .get("/schools-table", (req, res) -> {
                    List<School> schools = repository.findAll();

                    StringBuilder html = new StringBuilder();
                    html.append("""
                            <html>
                            <head>
                              <meta charset="UTF-8">
                              <title>Schools</title>
                              <style>
                                body { font-family: Arial, sans-serif; margin: 20px; }
                                table { border-collapse: collapse; width: 100%; }
                                th, td {
                                  border: 1px solid #999;
                                  padding: 8px;
                                  text-align: left;
                                  vertical-align: top;
                                }
                                th { background-color: #f0f0f0; }
                                tr:nth-child(even) { background-color: #fafafa; }
                              </style>
                            </head>
                            <body>
                              <h2>School List</h2>
                              <table>
                                <tr>
                                  <th>ID</th>
                                  <th>Exam Date</th>
                                  <th>School Name</th>
                                  <th>Class Name</th>
                                  <th>Capacity</th>
                                  <th>Subjects</th>
                                  <th>English Only</th>
                                  <th>Application Deadline / Docs</th>
                                  <th>Benefits</th>
                                  <th>Remarks</th>
                                </tr>
                            """);

                    for (School s : schools) {
                        html.append("<tr>")
                                .append("<td>").append(s.id).append("</td>")
                                .append("<td>").append(escapeHtml(formatDateForDisplay(s.examDate))).append("</td>")
                                .append("<td>").append(escapeHtml(s.schoolName)).append("</td>")
                                .append("<td>").append(escapeHtml(s.className)).append("</td>")
                                .append("<td>").append(escapeHtml(s.capacity)).append("</td>")
                                .append("<td>").append(escapeHtml(s.subjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.englishOnly)).append("</td>")
                                .append("<td>").append(escapeHtml(s.applicationDeadlineDocs)).append("</td>")
                                .append("<td>").append(escapeHtml(s.benefits)).append("</td>")
                                .append("<td>").append(escapeHtml(s.remarks)).append("</td>")
                                .append("</tr>");
                    }

                    html.append("""
                              </table>
                            </body>
                            </html>
                            """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(html.toString());
                })

                .get("/api/schools", (req, res) -> {
                    List<SchoolV2> schools = repositoryV2.findAll();
                    res.send(schools);
                })

                .get("/schools-v2-table", (req, res) -> {
                    if (!isAuthenticated(req)) {
                        res.status(302);
                        res.header("Location", "/login");
                        res.send();
                        return;
                    }

                    List<SchoolV2> schools = repositoryV2.findAll();

                    StringBuilder html = new StringBuilder();
                    html.append("""
                            <html>
                            <head>
                              <meta charset="UTF-8">
                              <title>学校一覧 V2</title>
                              <style>
                                body {
                                  font-family: Arial, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif;
                                  margin: 20px;
                                }
                                h2 {
                                  margin-bottom: 12px;
                                }
                                .toolbar {
                                  margin-bottom: 16px;
                                }
                                #searchBox {
                                  width: 380px;
                                  max-width: 100%;
                                  padding: 8px 10px;
                                  font-size: 14px;
                                  box-sizing: border-box;
                                }
                                table {
                                  border-collapse: collapse;
                                  width: 100%;
                                }
                                th, td {
                                  border: 1px solid #999;
                                  padding: 8px;
                                  text-align: left;
                                  vertical-align: top;
                                }
                                th {
                                  background-color: #f0f0f0;
                                  cursor: pointer;
                                  user-select: none;
                                  position: sticky;
                                  top: 0;
                                }
                                tr:nth-child(even) {
                                  background-color: #fafafa;
                                }
                                th.sort-asc::after {
                                  content: " ▲";
                                  font-size: 12px;
                                }
                                th.sort-desc::after {
                                  content: " ▼";
                                  font-size: 12px;
                                }
                                a {
                                  color: #0645ad;
                                  text-decoration: none;
                                }
                                a:hover {
                                  text-decoration: underline;
                                }

                              </style>
                            </head>
                            <body>
                              <h2>学校一覧 V2</h2>
                              <div style="margin-bottom: 12px;">
                                <a href="/logout">ログアウト</a>
                              </div>


                              <div class="toolbar">
                                <input type="text" id="searchBox" placeholder="学校名・入試分類・試験日・備考などで検索">
                              </div>

                              <table id="schoolTable">
                                <thead>
                                  <tr>
                                    <th onclick="sortTable(0, true)">ID</th>
                                    <th onclick="sortTable(1, false)">学校名</th>
                                    <th onclick="sortTable(2, false)">入試分類</th>
                                    <th onclick="sortTable(3, false)">募集人数</th>
                                    <th onclick="sortTable(4, false)">試験日</th>
                                    <th onclick="sortTable(5, false)">試験科目</th>
                                    <th onclick="sortTable(6, false)">試験科目特記</th>
                                    <th onclick="sortTable(7, false)">面接有無</th>
                                    <th onclick="sortTable(8, false)">英語資格優遇</th>
                                    <th onclick="sortTable(9, false)">備考</th>
                                    <th onclick="sortTable(10, false)">学校リンク</th>
                                  </tr>
                                </thead>
                                <tbody>
                            """);

                    for (SchoolV2 s : schools) {
                        html.append("<tr>")
                                .append("<td>").append(s.id).append("</td>")
                                .append("<td>").append(escapeHtml(s.schoolName)).append("</td>")
                                .append("<td>").append(escapeHtml(s.category)).append("</td>")
                                .append("<td>").append(escapeHtml(s.capacity)).append("</td>")
                                .append("<td>").append(escapeHtml(formatDateForDisplay(s.examDates))).append("</td>")
                                .append("<td>").append(escapeHtml(s.subjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.alternateSubjects)).append("</td>")
                                .append("<td>").append(escapeHtml(s.interview)).append("</td>")
                                .append("<td>").append(escapeHtml(s.englishQualificationBenefit)).append("</td>")
                                .append("<td>").append(escapeHtml(s.notes)).append("</td>")
                                .append("<td>")
                                .append(s.infoLink == null || s.infoLink.isBlank()
                                        ? ""
                                        : "<a href=\"" + escapeHtml(s.infoLink) + "\" target=\"_blank\" rel=\"noopener noreferrer\">リンク</a>")
                                .append("</td>")
                                .append("</tr>");
                    }


html.append("""
            </tbody>
          </table>

          <script>
            const searchBox = document.getElementById('searchBox');
            const table = document.getElementById('schoolTable');
            const tbody = table.querySelector('tbody');
            let sortDirections = {};

            searchBox.addEventListener('keyup', function() {
              const keyword = this.value.toLowerCase();
              const rows = tbody.querySelectorAll('tr');

              rows.forEach(row => {
                const text = row.innerText.toLowerCase();
                row.style.display = text.includes(keyword) ? '' : 'none';
              });
            });

            function sortTable(colIndex, numeric) {
              const rows = Array.from(tbody.querySelectorAll('tr'));
              const headers = table.querySelectorAll('th');
              const asc = !sortDirections[colIndex];
              sortDirections = {};
              sortDirections[colIndex] = asc;

              headers.forEach(th => {
                th.classList.remove('sort-asc', 'sort-desc');
              });
              headers[colIndex].classList.add(asc ? 'sort-asc' : 'sort-desc');

              rows.sort((a, b) => {
                const aText = a.children[colIndex].innerText.trim();
                const bText = b.children[colIndex].innerText.trim();

                if (numeric) {
                  const aNum = parseFloat(aText) || 0;
                  const bNum = parseFloat(bText) || 0;
                  return asc ? aNum - bNum : bNum - aNum;
                }

                return asc
                  ? aText.localeCompare(bText, 'ja')
                  : bText.localeCompare(aText, 'ja');
              });

              
                      rows.forEach(row => tbody.appendChild(row));
            }
          </script>
        </body>
        </html>
        """);

                    res.header("Content-Type", "text/html; charset=UTF-8");
                    res.send(html.toString());
                });
    }
}