# Android Library Unified Checklist

library 的一致性檢查。

## 1. 命名與風格
- `package`、檔名、類名、函式名遵循 Kotlin 慣例（UpperCamelCase / lowerCamelCase）。
- 公開 API 命名語義一致，避免「同名不同角色」。
- deprecated API 在 README 僅保留遷移指引，不作為主流程。

## 2. 架構與技術棧
- 保持每個庫既有依賴策略（如 Hilt/Room/MMKV），不強制改棧。
- README 與實際程式碼一致（初始化方式、入口 API、必要依賴）。

## 3. KDoc 最低門檻
- 所有 `public class/object/interface` 需有用途說明。
- 所有 `public` 函式至少描述參數、回傳值/副作用。
- 涉及主執行緒、生命週期、IO 或 coroutine 的 API 需標註約束。

## 4. README 五分鐘上手
- 必須包含 `Quick Start (3 steps)`：依賴、初始化、第一個可跑範例。
- 首頁可讓使用者在 5 分鐘內完成最小串接。
- 進階章節（遷移、優化、FAQ）需後置。

## 5. ProGuard/Consumer Rules
- `proguard-rules.pro`：僅針對庫自身 release 建置必要規則。
- `consumer-rules.pro`：僅保留反射、序列化、註解處理必要入口。
- 避免整包 `-keep ... { *; }`，優先精準 `-keepclassmembers`。
- 移除不存在類別規則、錯誤語法與過寬 `-dontwarn`。
