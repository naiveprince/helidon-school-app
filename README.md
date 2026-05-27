# README.md

## helidon-school-app

中学受験「英語入試・帰国生入試」情報アプリ
Helidon + Render + GitHub Actions による半自動更新システム。

＜半自動更新とは＞
本アプリの学校データは、GitHub Actions によって毎週自動で巡回・更新されます。
更新可能な学校は自動で反映され、更新に失敗した学校はそのまま前回の内容を保持します。
失敗した学校については、GitHub Actions の Run updater ログで SSL_ERROR、MISSING_URL、UNKNOWN_ERROR などのエラー内容を確認し、必要に応じて手動で修正してください。
つまり、本システムは「完全自動更新」ではなく、自動で更新できる学校は自動更新し、更新できなかった学校だけ人手で確認して修正する 半自動運用です。
この方式により、学校サイトのSSL不備、URL変更、ページ構成変更などが起きても、更新できる学校は止めずに反映しつつ、問題のある学校だけを個別に見直せます。

---

# 概要

本システムは、

* 帰国生入試
* 英語入試
* 国際系中学校

を対象にした、

```text
学校検索
学校一覧表示
学校情報監視
半自動更新
```

システムです。

現在：

* 約110校
* GitHub Actions 自動巡回
* archive 自動バックアップ
* Render 自動デプロイ
* Android App 連携

まで対応しています。

---

# システム構成

```text
GitHub Actions
        ↓
Python巡回
(requests + BeautifulSoup)
        ↓
schools-v2.json 更新
        ↓
archive バックアップ
        ↓
GitHub commit
        ↓
Render Auto Deploy
        ↓
Helidon API
        ↓
Android App / Web
```

---

# リポジトリ構成

```text
.
├── .github/workflows/
├── archive/
├── data/
├── scripts/
├── src/
├── Dockerfile
├── pom.xml
└── README.md
```

---

# JSON管理

## 正本JSON

唯一の正本：

```text
data/schools-v2.json
```

---

# 自動バックアップ

毎週：

```text
archive/schools-v2-YYYYMMDD-HHMMSS.json
```

へ自動保存。

例：

```text
archive/schools-v2-20260526-120049.json
```

---

# GitHub Actions

## workflow

```text
.github/workflows/update-schools.yml
```

---

# 実行タイミング

毎週：

```text
月曜 AM3:00 JST
```

実行。

---

# GitHub Actions がやること

* 学校URL巡回
* requests
* BeautifulSoup
* HTML取得
* タイトル解析
* check-report.json生成
* archiveバックアップ
* data/schools-v2.json 更新
* GitHub commit

---

# Python

## スクリプト

```text
scripts/update_schools.py
```

---

# requirements.txt

```text
requests
beautifulsoup4
```

---

# 文字化け対策

```python
response.encoding = response.apparent_encoding
```

を実装済。

---

# check-report.json

生成場所：

```text
data/check-report.json
```

---

# エラー分類

## SSL_ERROR

学校側SSL問題。

通常は放置OK。

---

## MISSING_URL

infoLink未設定。

必要時のみ修正。

---

## UNKNOWN_ERROR

要確認。

---

# 半自動更新

## 基本思想

本システムは：

```text
完全自動更新
```

ではなく、

```text
半自動監視＋人確認
```

を採用。

---

# 理由

学校サイトは：

* HTML変更
* PDF変更
* Cloudflare
* SSL不整合
* URL変更

が頻発するため。

---

# 毎週やること（5分）

## ① Actions確認

```text
GitHub
↓
Actions
↓
Update schools JSON
```

---

## ② Success確認

緑ならOK。

---

## ③ Run updater確認

以下を見る：

* SSL_ERROR
* MISSING_URL
* UNKNOWN_ERROR

---

## ④ 必要校だけ修正

GitHub GUIで：

```text
data/schools-v2.json
```

を直接編集。

---

## ⑤ Commit

GitHub GUI：

```text
Commit changes
```

---

# Render

## Auto Deploy

```text
Auto Deploy = On Commit
```

設定。

GitHub push 後、自動デプロイ。

---

# Render構成（重要）

## Dockerfile

```dockerfile
COPY data ./data
```

を実装済。

---

# なぜ必要？

Render が：

```text
/helidon/data/schools-v2.json
```

を external file として読むため。

---

# DbInit.java

## external JSON優先

```java
private static final Path EXTERNAL_JSON_PATH =
        Paths.get(System.getProperty("user.dir"), "data", "schools-v2.json");
```

---

# JSON読込順

```text
external file
↓
classpath fallback
```

---

# Renderログ確認

成功時：

```text
external exists = true
Loaded schools-v2 from external file
```

---

# Android App

別リポジトリ：

```text
SchoolViewer
```

---

# Android構成

* Kotlin
* Jetpack Compose
* Retrofit
* Material3

---

# Google Play

## クローズドテスト対応済

現在：

* upload key reset済
* App Bundle対応済
* 署名鍵対応済

---

# 署名鍵

## 保存場所

```text
SchoolViewer/app/release-keystore.jks
```

---

# .gitignore

必須：

```gitignore
*.jks
*.keystore
*.pem
app/build/
```

---

# App Bundle生成

```bash
./gradlew bundleRelease
```

---

# AAB生成場所

```text
app/build/outputs/bundle/release/app-release.aab
```

---

# Render再デプロイ

必要時：

```text
Manual Deploy
↓
Clear build cache & deploy latest commit
```

---

# 現在の完成状態

✅ GitHub Actions
✅ BeautifulSoup巡回
✅ archiveバックアップ
✅ Git履歴保存
✅ Render自動デプロイ
✅ external JSON読込
✅ Android App連携
✅ Google Play対応
✅ 半自動更新

---

# 今後の候補

* 差分通知
* Slack通知
* PR生成
* PDF解析強化
* AI要約

---


