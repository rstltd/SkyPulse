# SkyPulse 測試策略：AI coding 時代的抗作弊測試 pipeline

> 目的：這個專案大量以 AI（Claude Code）協助寫 code 與 test。本文件定義一套**抵抗指標作弊、以獨立 oracle 為核心、把 mutation testing 當診斷而非 KPI** 的測試策略。硬性護欄的精簡版在根目錄 `CLAUDE.md` 的 **Testing** 節；本文件是理由與完整 pipeline。

---

## 0. 一句話

當寫 code 的模型同時寫 test，測試的 oracle（判定對錯的依據）會退化成模型自己的假設——於是把哪個指標設成目標，它都能用「複述當下實作行為」來滿足（把 bug 一起編碼成 expected）。**解法不是換一個更好的單一指標，而是讓 pipeline 沒有單一可被作弊的目標，並讓測試錨定「獨立於實作」的來源。**

---

## 1. 為什麼「追 coverage / 淺 unit test」是死路

Coverage 量的是「這行有沒有被執行」，不是「有沒有被驗證」。無斷言的測試能把 coverage 衝到 100%、抓 bug 能力卻是 0。

- **Inozemtseva & Holmes, ICSE 2014, "Coverage Is Not Strongly Correlated with Test Suite Effectiveness"**：控制 test suite 大小後，coverage 與有效性只剩弱-中相關，更強的 coverage 型式（branch/path）不提供更多資訊。 <https://www.cs.ubc.ca/~rtholmes/papers/icse_2014_inozemtseva.pdf>
- **Zhang & Mesbah, FSE 2015, "Assertions Are Strongly Correlated with Test Suite Effectiveness"**：真正與有效性強相關的是**斷言數量與斷言覆蓋率**，不是行數。 <https://people.ece.ubc.ca/amesbah/resources/papers/fse15.pdf>
- **Martin Fowler, "TestCoverage"**：高覆蓋率「太容易用低品質測試達成」；coverage 的正確用途是找出「哪些碼完全沒被測到」。 <https://martinfowler.com/bliki/TestCoverage.html>

**Goodhart's law**：一個量測一旦變成目標，就不再是好量測。把 80% coverage 設成 KPI，AI 與人都會寫無斷言的 trivial 測試灌數字。

> 誠實但書：整體文獻是 **mixed**，不是「coverage 全無用」（SANER 2015 在真 bug 上找到統計顯著相關）。共識到「coverage 是 **floor 不是 ceiling**、必要非充分」。它適合當**負面清單**（找完全沒測到的區域），不適合當正面 KPI。

---

## 2. 為什麼 mutation testing 有用、但當硬 KPI 一樣被作弊

mutation testing 量的是「如果 code 錯了，測試會不會失敗」——直接回答 coverage 回答不了的問題。一個不斷言具體行為的測試，會讓每一個 value-change 變異體存活，即使 coverage 100%。存活變異體成群出現在高覆蓋函式上，就是「這裡沒有有效斷言」的鐵證。

但當成**要衝高的 KPI**一樣會被作弊：

- **套套邏輯殺變異體**：AI 為殺存活變異體補斷言、卻沒先驗「原始行為對不對」→ 把 bug 當需求釘進測試。
- **等價變異體（equivalent mutants）**：語法不同但語義相同、任何測試都殺不掉，讓 100% 本質不可達；設門檻會製造「亂標等價以美化分數」的誘因。
- **記憶灌水**：LLM 在訓練資料看過的公開 repo 上靠記憶拿虛高分（有研究觀察到公開 repo mutation score 近 100%、換到私有碼崩到個位數~20%，且模型會刪斷言/產生空 test body 求綠）。

**正解（Google 6000 名工程師的規模化實務）**：用 mutation testing 但**不設 mutation-score 目標**；改成對 diff 跑、把存活體當 code review findings 呈現、人逐一判 productive/equivalent。**mutation = 診斷對話工具，不是 KPI。** <https://research.google/pubs/state-of-mutation-testing-at-google/>

---

## 3. 根本問題：AI 同時寫 code + test 的 oracle 陷阱

當同一個模型從同一 prompt 決定「怎麼實作」與「正確長怎樣」，兩者共享盲點，測試對照的是實作自身的假設而非獨立規格——bug 被同時編碼進 code 和 test（"grading your own homework"）。研究亦指出 LLM 生成的 test oracle 傾向捕捉「實作實際行為」而非「規格預期行為」，且 AI 會主動弱化測試以求綠。

**破解原理**：讓 oracle 獨立於「產生被測物的東西」，而且要**機器可檢查**（否則 agent 無法自主迭代）。依 oracle 獨立性排序的技術：

| 技術 | oracle 來源 | 獨立性 | 適用 SkyPulse | 弱點 |
|---|---|---|---|---|
| **Contract testing**（Pact / Spring Cloud Contract） | 外部 consumer 的期望 | 最高（真外部系統時） | 未來給 GNSS 系統的座標查詢 API | 只驗訊息形狀，不驗業務邏輯 |
| **Property-based / Metamorphic**（jqwik / fast-check） | 領域數學/不變量、輸入輸出關係 | 高（性質源自 spec 時） | ETR1/ETR2、累積雨量、去重、GNSS 分級 | 想不出好性質；弱 MR |
| **Differential**（對照獨立參考實作） | 另一個獨立可信實作 | 高（參考真獨立時） | ETR2 對 brute-force、Haversine 對 GIS 函式庫 | 兩邊 correlated bug |
| **Fuzzing**（Jazzer） | 隱式：不得崩潰/觸發 sanitizer | 中 | collector 解析外部 JSON | 只抓崩潰，不抓「不崩但錯」 |
| **Approval / Snapshot** | 先前已核可的輸出 | 最低（對新碼幾乎零） | 僅限重構鎖定既有可信行為 | 對新 AI code 最危險——把 bug 固化成期望 |

準則：**能寫 relationship-based 斷言（round-trip/對稱/單調/與參考比對）就不要釘 magic number**；property 要落在可辨識的類別（Wlaschin 七類：交換律/round-trip/不變量/idempotence/結構歸納/難證易驗/對照參考實作）並引用領域數學而非 SUT。 <https://fsharpforfunandprofit.com/posts/property-based-testing-2/>

---

## 4. 抗作弊分層 pipeline（本專案）

設計原則：刻意堆疊多層、每層抓不同失敗、oracle 來源彼此獨立——作弊一層不會自動通過另一層。形狀採 Testing Trophy（重心壓整合層）。

| 層 | 抓什麼 | 後端 | 前端 | gate / 診斷 |
|---|---|---|---|---|
| **L0 靜態** | 型別、明顯錯用 | ArchUnit（選用） | ESLint + vue-tsc | **gate** |
| **L1 純函數單元** | 邏輯分支（具體值斷言） | JUnit 5 + AssertJ | Vitest | gate on diff |
| **L2 property / metamorphic** | 邊界、不變量（**獨立 oracle 主力**） | **jqwik** | fast-check | gate on diff |
| **L3 contract** | API JSON 形狀被靜默改壞 | OpenAPI schema 驗；未來 Pact-JVM | MSW / Pact-JS | gate on diff |
| **L4 真 DB 整合** | SQL/schema/時區/TimescaleDB 語意 | **真 Postgres+TimescaleDB**（`docker-compose.test.yml` :5433）+ WireMock + MockMvc | — | gate（關鍵路徑） |
| **L5 e2e** | 端到端關鍵流程 | — | Playwright | gate（少量） |
| **L6 mutation 稽核** | 前六層斷言「會不會失敗」 | PIT（只對 diff 跑） | StrykerJS | **診斷** |
| **L7 coverage 診斷** | 完全沒測到的區域 | JaCoCo | Vitest coverage | **診斷（絕不當 KPI）** |

- **L4 必須跑真 Timescale**：hypertable / continuous aggregate / compression / TIMESTAMPTZ / segment-by 用 H2 會 green-but-lying。本專案已因 Testcontainers 1.20.6 與 Docker Desktop 不相容改用 `docker-compose.test.yml`，這正是正確的 L4。
- **CI gating 原則**：gate 卡**新增 diff**、診斷看全庫（SonarQube "Clean as You Code"）——把 AI 的作弊誘因限縮在剛改的一小段，人審看得住。
- **flaky 管理**：偵測→隔離（quarantine，仍跑仍記錄但不擋 merge）→指派 owner；**retry 是診斷不是解法**，特別防 AI 用加 retry/放寬容差/加 sleep 掩蓋真 bug。
- **前端測行為非實作**（Testing Library），避免 change-detector test。

**為什麼抗作弊**：AI 要通過，得同時騙過「不看實作的 property（L2）」「外部契約（L3）」「真 DB 語意（L4）」「mutation 稽核（L6）」——這些 oracle 互相獨立，沒有任何單一數字可一次玩壞。

---

## 5. AI 測試護欄（硬規則）

見 `CLAUDE.md` 的 **Testing** 節（權威版）。摘要：

1. **期望值禁止從被測函式回填**——須溯源獨立來源。
2. 優先 **relationship-based 斷言**。
3. **property 源自領域數學/spec、不得在 property 內呼叫 SUT 算期望**；禁廢性質；每條要能說「排除了哪個錯誤實作」。
4. metamorphic relation 源自 spec/物理。
5. 禁「讓測試變綠的作弊」（刪斷言/空 body/空 catch/加 retry/放寬容差）；property 失敗時修 code 或依 spec 修正 oracle，不得放寬 property。
6. snapshot/approval 只用於重構鎖定既有可信碼；不得自動更新 snapshot。
7. 整合測試跑真 Postgres+TimescaleDB，不得靜默改 H2/mock。
8. **mutation test = 診斷不是 KPI**：對 diff 跑、存活體逐一判 productive/equivalent、不設全庫硬門檻、不擴大排除清單。

**人審集中三點**：oracle 來源是否溯源外部權威、斷言意圖、抽查存活變異體與 property 是否真不變量。

---

## 6. Mutation testing 的正確用法（PIT）

已在 `pom.xml` 接 PIT（`org.pitest:pitest-maven` + `pitest-junit5-plugin`），scope 對準領域邏輯（`util.*`、`SeismicService`、`SpaceWeatherService`）。

```bash
./mvnw -P'!frontend' test-compile org.pitest:pitest-maven:mutationCoverage
# 報告：target/pit-reports/index.html
```

正確使用：

- **消費單位是「少量 productive 存活體」，不是分數。** 存活體當 code review findings，逐一判「這是該補的測試，還是其實是 bug」。
- **不對全庫 mutation score 設硬門檻**（等價變異體讓 100% 不可達 + 一設門檻就再 Goodhart 化）。先純診斷跑，之後最多只對 diff 設較寬門檻或不 gate。
- **效能**：正式導入 CI 時改 PIT 的 `scmMutationCoverage`（只變異 git ADDED/MODIFIED 類別）+ `withHistory`；排程對 main 跑全量補漏。
- **mutator 用 DEFAULTS**（要更嚴用 STRONGER），**不要開 ALL**（ALL 的算子易生等價變異體=假目標）。
- **Spring**：可用 arcmutate Spring plugin 變異 `@Min/@Max/@Pattern` 等約束；排除 boilerplate（entity/DTO/config），火力集中 WeatherService（ETR）/SeismicService（去重）/GNSS 分級。
- **私有領域邏輯的分數才誠實**：別被公開樣板題高分誤導。

### Worked example：SeismicService 去重（本專案實測）

初次對 `SeismicService` 跑 PIT：35 變異體、殺 18、**存活 6**——其中 5 個是門檻邊界（`ConditionalsBoundaryMutator`），代表 30s/10km/0.3 的邊界沒被測試釘死。

補上 `SeismicServiceDedupPropertyTest`（jqwik：dedup 大小對輸入順序不變＝對稱、idempotence、跨源相同事件收斂到 CWA、超過 30s 永不合併；加「剛好 30 秒 / 31 秒」邊界矩陣）＋ `SeismicServiceTest` 兩個 productive 案例（候選在時窗內但太遠→非重複；CWA 只取代相符的 USGS、不誤刪無關事件）後：

- **殺 18→23、存活 6→4、no_coverage 11→8。**
- 被殺的是 **productive** 缺口：時間邊界（整數秒，`>`vs`>=` 可達）、isDuplicate 謂詞、CWA 取代邏輯。
- 剩下 4 個存活全是**距離/規模的浮點邊界**（≤10km、≤0.3）——**等價變異體**：無法建構「剛好 10.0km（Haversine）/ 剛好 0.3 規模差（double）」的輸入，任何測試都殺不掉。**正確做法是認出並停手，不為衝分數寫脆弱測試。**

這就是「productive 就補、equivalent 就放」的紀律，也是對「AI 會濫用 mutation test」顧慮的實作解答。

---

## 7. 務實起步（CP 值最高）

### 首選：property-based testing（jqwik）給 ETR / 累積雨量 / 去重

這些邏輯「沒有單一明確期望值、但有清楚數學性質」，且性質源自 SWCB 公式（獨立於實作）。可寫的性質（**須對照 SWCB 標準確認語意**）：

- **累積雨量單調**：72h ≥ 48h ≥ 24h ≥ 3h（同一時間點）；**站點獨立**：加 A 站不改 B 站；**非負**。
- **ETR2 對 precip 線性/齊次**（序列乘 k → ETR2 約乘 k）；對**衰減序列遞減**（越早權重越低）。
- **去重** idempotence、排列不變、30s 臨界矩陣（time 為整數，可killable；距離/規模浮點邊界為等價變異體，不追）。
- **距離** 對稱 + 三角不等式；可 differential 對照成熟 GIS 函式庫（JTS/geodesy）。

**閉環**：對這些 property 測試跑 PIT——殺不掉 `WeatherService`/`SeismicService` 的變異體，代表性質太弱（廢性質）。這是 mutation 當交叉檢查的正確用法。

> ⚠️ `WeatherService`（ETR1/ETR2）目前**零單元測試**，是首要補測目標。

### 次選（未來需要時）：contract testing 給對外座標/查詢 API

真實運作 5–6 年的 GNSS 系統會呼叫 SkyPulse API＝真正分離的外部 consumer，獨立性成立。現階段輕量做法：以 OpenAPI schema 為契約來源、後端驗 controller 回應、前端 MSW mock；等 GNSS 系統進場再上 Pact（consumer↔provider）+ broker/can-i-deploy。重點：契約來自規格/消費者需求，不是複製當前 controller 輸出。

**先不碰**：fuzzing（Jazzer）留給 collector 解析外部 JSON；snapshot/approval 只在重構既有可信碼時用，首次核可須人對照 spec 審。

---

## 8. 仍有爭議 / 未定論（誠實標註）

1. coverage 與 bug 的相關性文獻整體 **mixed**，共識到「必要非充分、floor 不是 ceiling」——別用它否定「補完全沒測到的碼」這件有價值的事。
2. **TDD-with-AI 的效果尚無大規模實證定論**（spec-first + 人審測試再讓 AI 實作是強啟發式，非已證定律）。
3. **mutation threshold 該不該 gate 業界無共識**；保守建議先純診斷跑數週再決定要不要對 diff 設寬 gate。
4. Testing Trophy vs Test Pyramid 都是啟發式；本文採 Trophy 是基於「有真 DB + 純函數領域邏輯 + Spring+SPA」的判斷。
5. 本文所有針對 SkyPulse 的具體 property/MR 都是**推論**，須對照 SWCB 有效雨量標準與 CWA/USGS/NOAA 文件確認閾值語意後才落地——這本身就是「oracle 由人擁有、溯源外部權威」原則的實踐。

---

## 參考

- Inozemtseva & Holmes, ICSE 2014 — <https://www.cs.ubc.ca/~rtholmes/papers/icse_2014_inozemtseva.pdf>
- Zhang & Mesbah, FSE 2015 — <https://people.ece.ubc.ca/amesbah/resources/papers/fse15.pdf>
- Martin Fowler, TestCoverage — <https://martinfowler.com/bliki/TestCoverage.html>
- State of Mutation Testing at Google — <https://research.google/pubs/state-of-mutation-testing-at-google/>
- jqwik（property-based testing, Java） — <https://jqwik.net/property-based-testing.html>
- Wlaschin, Choosing properties for property-based testing — <https://fsharpforfunandprofit.com/posts/property-based-testing-2/>
- Metamorphic testing — <https://en.wikipedia.org/wiki/Metamorphic_testing>
- PIT mutation testing — <https://pitest.org/>
- Testing Trophy（Kent C. Dodds） — <https://kentcdodds.com/blog/the-testing-trophy-and-testing-classifications>
- SonarQube Clean as You Code — <https://docs.sonarsource.com/sonarqube-server/user-guide/about-new-code>
