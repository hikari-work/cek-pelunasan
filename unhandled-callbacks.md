# Unhandled Keyboard Callbacks

Callback yang memiliki button di keyboard tapi **tidak ada handler** yang menanganinya.

## Dispatch Mechanism

`CallbackHandler.java` mengekstrak prefix (bagian pertama sebelum `_`) dan mencari handler di `processorMap`.
Jika tidak ditemukan, fallback ke `NoContextCallbackHandler` (key: `"none"`).

```java
String callbackData = update.getCallbackQuery().getData().split("_")[0];
CallbackProcessor callbackProcessor = processorMap.getOrDefault(callbackData, processorMap.get("none"));
```

---

## Handler yang Terdaftar

| Prefix     | Handler Class                          |
|------------|----------------------------------------|
| `tagihan`  | BillsCalculatorCallbackHandler         |
| `pagebills`| BillsByNameCalculatorCallbackHandler   |
| `paging`   | PaginationBillsCallbackHandler         |
| `canvasing`| PaginationToCanvasing                  |
| `canvas`   | CanvasingTabCallbackHandler            |
| `koltas`   | KolektasCallbackHandler                |
| `minimal`  | MinimalPayCallbackHandler              |
| `tab`      | SavingNextButtonCallbackHandler        |
| `branchtab`| SavingsSelectBranchCallbackHandler     |
| `branch`   | SelectBranchCallbackHandler            |
| `slik`     | SlikSenderHandler                      |
| `services` | ServicesCallbackHandler                |
| `noop`     | NoContextCallbackHandler (default)     |

---

## Callback yang Sudah Ditangani

Semua callback sudah memiliki handler.
