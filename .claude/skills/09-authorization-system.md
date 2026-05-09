---
name: authorization-system
description: Sistem otorisasi AOP di project cek-pelunasan — @RequireAuth annotation, AuthorizationAspect, AuthorizedChats cache, dan cara menggunakannya
---

# Authorization System

Package: `org.cekpelunasan.annotation`, `org.cekpelunasan.aspect`, `org.cekpelunasan.core.service.auth`

---

## Komponen Utama

```
@RequireAuth (annotation)
       ↓ triggers
AuthorizationAspect (AOP @Around advice)
       ↓ checks
AuthorizedChats (in-memory cache)
       ↓ backed by
UserRepository (MongoDB)
```

---

## @RequireAuth Annotation
**File**: `annotation/RequireAuth.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {
    AccountOfficerRoles[] roles() default {};
    // roles kosong = semua role yang terdaftar boleh akses
    // roles diisi = hanya role tersebut yang boleh akses
}
```

**Cara pakai**:
```java
// Boleh diakses semua user terdaftar
@RequireAuth
public void handle(TdApi.Message message) { ... }

// Hanya ADMIN
@RequireAuth(roles = {AccountOfficerRoles.ADMIN})
public void handle(TdApi.Message message) { ... }

// AO dan ADMIN (PIMP tidak boleh)
@RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.ADMIN})
public void handle(TdApi.Message message) { ... }
```

---

## AuthorizationAspect
**File**: `aspect/AuthorizationAspect.java`

```java
@Aspect
@Component
public class AuthorizationAspect {

    @Around("@annotation(requireAuth)")
    public Object checkAuthorization(
        ProceedingJoinPoint joinPoint,
        RequireAuth requireAuth
    ) throws Throwable {

        // 1. Extract chatId dari parameter method
        Long chatId = extractChatId(joinPoint.getArgs());

        // 2. Cek apakah chatId ada di whitelist
        if (!authorizedChats.isAuthorized(chatId)) {
            messageService.sendMessage(chatId,
                "⛔ Akses ditolak. Daftarkan diri dengan /otor <kode>");
            return null; // method tidak dieksekusi
        }

        // 3. Cek role jika dispesifikasi
        AccountOfficerRoles[] requiredRoles = requireAuth.roles();
        if (requiredRoles.length > 0) {
            if (!authorizedChats.hasAnyRole(chatId, requiredRoles)) {
                messageService.sendMessage(chatId,
                    "⛔ Role Anda tidak memiliki akses ke fitur ini.");
                return null;
            }
        }

        // 4. Lanjutkan eksekusi method
        return joinPoint.proceed();
    }
}
```

**Cara extract chatId dari args**:
- Jika arg adalah `TdApi.Message` → `message.chatId`
- Jika arg adalah `TdApi.UpdateNewCallbackQuery` → `query.senderUserId`
- Jika arg adalah `Long` langsung → digunakan langsung

---

## AuthorizedChats
**File**: `core/service/auth/AuthorizedChats.java`

**Storage**: `ConcurrentHashMap<Long, User>` — chatId → User object

```java
@Service
public class AuthorizedChats {

    private final ConcurrentHashMap<Long, User> cache = new ConcurrentHashMap<>();

    // Dipanggil saat startup (PreRun.java)
    public void loadAll() {
        userRepository.findByIsActive(true)
            .subscribe(user -> cache.put(user.getChatId(), user));
    }

    // O(1) lookup
    public boolean isAuthorized(Long chatId) {
        return cache.containsKey(chatId);
    }

    // Cek role
    public boolean hasRole(Long chatId, AccountOfficerRoles role) {
        User user = cache.get(chatId);
        return user != null && user.getRole() == role;
    }

    // Cek salah satu dari beberapa role
    public boolean hasAnyRole(Long chatId, AccountOfficerRoles[] roles) {
        User user = cache.get(chatId);
        if (user == null) return false;
        for (AccountOfficerRoles role : roles) {
            if (user.getRole() == role) return true;
        }
        return false;
    }

    // Tambah user baru (dipanggil setelah /otor sukses)
    public void addUser(User user) {
        cache.put(user.getChatId(), user);
    }

    // Hapus user (setelah nonaktifkan)
    public void removeUser(Long chatId) {
        cache.remove(chatId);
    }

    // Update user di cache (setelah role/branch diubah)
    public void updateUser(User user) {
        cache.put(user.getChatId(), user);
    }
}
```

---

## PreRun (Startup Initialization)
**File**: `core/lifecycle/PreRun.java`

```java
@Component
@EventListener(ApplicationReadyEvent.class)
public class PreRun {

    public void onApplicationReady() {
        // 1. Load semua authorized user ke cache
        authorizedChats.loadAll();

        // 2. Init owner bot jika belum ada
        initOwnerIfAbsent();

        log.info("Bot ready. Authorized users: {}", authorizedChats.size());
    }
}
```

---

## Alur Registrasi User Baru

```
User kirim: /otor AO-001
       ↓
AuthCommandHandler.handle()
       ↓ UserService.findByCode("AO-001")
       ↓ jika ditemukan:
UserService.register(chatId, "AO-001", AO)
       ↓ simpan ke MongoDB
AuthorizedChats.addUser(newUser)
       ↓ tambah ke cache
Kirim: "✅ Registrasi berhasil. Selamat datang, AO-001!"
```

---

## Alur Nonaktifkan User (ADMIN)

```
Admin kirim: /delete-user 123456789
       ↓
DeleteUserAccessCommand.handle()  (@RequireAuth(roles={ADMIN}))
       ↓ UserService.deactivate(123456789)
       ↓ update isActive=false di MongoDB
AuthorizedChats.removeUser(123456789)
       ↓ hapus dari cache
Kirim: "✅ User 123456789 berhasil dinonaktifkan"
```

---

## Cache Consistency

Cache di-update setiap ada perubahan:
- **Register** → `addUser()`
- **Deactivate** → `removeUser()`
- **Change role** → `updateUser()`
- **Change branch** → `updateUser()`

Jika terjadi restart server, `PreRun.loadAll()` reload semua dari MongoDB.

---

## Catatan Keamanan

1. **No JWT** — autentikasi berdasarkan Telegram chatId yang unik per akun
2. **In-memory whitelist** — lebih cepat dari DB lookup per request
3. **Role-based** — granular control via `AccountOfficerRoles` enum
4. **AOP** — single point of enforcement, tidak tersebar di tiap handler
5. **ConcurrentHashMap** — thread-safe untuk concurrent bot requests
