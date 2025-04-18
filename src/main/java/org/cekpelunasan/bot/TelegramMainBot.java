package org.cekpelunasan.bot;

import org.cekpelunasan.entity.Repayment;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.service.AuthorizedChats;
import org.cekpelunasan.service.RepaymentService;
import org.cekpelunasan.service.UserService;
import org.cekpelunasan.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TelegramMainBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    private AuthorizedChats authorizedChats;

    private static final Logger log = LoggerFactory.getLogger(TelegramMainBot.class);

    private final TelegramClient telegramClient;
    private final String botToken;
    private final String ownerId;
    private final RepaymentService repaymentService;
    private final UserService userService;

    public TelegramMainBot(@Value("${telegram.bot.token}") String botToken,
                           @Value("${telegram.bot.owner}") String ownerId,
                           RepaymentService repaymentService, UserService userService) {
        this.botToken = botToken;
        this.ownerId = ownerId;
        this.repaymentService = repaymentService;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.userService = userService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatId = update.getMessage().getChatId().toString();
            String text = update.getMessage().getText();
            if (text.startsWith(".id")) {
                sendMessage(chatId, "ID Anda: " + chatId);
                log.info("User Id {} Generated Id", chatId);
                return;
            }
            if (!text.startsWith("/") && !chatId.equals(ownerId)) {
                forwardUserMessageToOwner(update);
                log.info("User {} Send Message To Admin", chatId);
            }
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            if (authorizedChats.isAuthorized(update.getMessage().getChatId()) || userService.findUser(update.getMessage().getChatId()) != null) {
                handleCommandMessage(update);
                if (!authorizedChats.isAuthorized(update.getMessage().getChatId())) {
                    authorizedChats.addAuthorizedChat(update.getMessage().getChatId());
                }
            } else {
                sendUnauthorizedMessage(update.getMessage().getChatId().toString());
                log.info("User {} Unauthorized", update.getMessage().getChatId());
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update);
            log.info("User {} Send Callback", update.getCallbackQuery().getFrom().getId());
        }

    }

    private void handleCommandMessage(Update update) {
        String chatId = update.getMessage().getChatId().toString();
        String text = update.getMessage().getText();

        if (chatId.equals(ownerId)) {
            if (text.startsWith("/upload ")) {
                handleUploadCommand(chatId, text);
                log.info("Updated Database");
            } else if (text.startsWith("/broadcast ")) {
                handleBroadcastUser(text.replace("/broadcast ", ""));
                log.info("Broadcast Message");
            } else if (update.getMessage().isReply()) {
                forwardReplyToOriginalUser(update);
                log.info("Forward Message To User");
            } else if (text.startsWith("/auth ")) {
                Long newUsers = Long.parseLong(text.replace("/auth ", "").trim());
                try {
                    userService.insertNewUser(newUsers);
                    log.info("User {} Authenticated", newUsers);
                } catch (Exception e) {
                    sendMessage(chatId, "Error");
                    log.error("Error authenticating user: {}", e.getMessage());
                }
            }
        }

        if (text.equals("/start")) {
            sendMessage(chatId, """
👋 *Halo! Selamat datang di Bot Pelunasan.*

Bot ini bukan tempat tanya jodoh, ya. Saya cuma bisa bantu cek *pelunasan*

Berikut beberapa perintah yang bisa kamu pakai:

/pl <No SPK> - Cek pelunasan nasabah
/fi <Nama> - Cari nasabah by nama
/id - Buat kamu yang belum diizinkan pakai bot ini
/help - Kalau kamu butuh bimbingan hidup (atau cuma mau lihat perintah)

📌 *Kalau kamu belum terdaftar*, jangan baper. Ketik `.id`, kirim ke admin, dan sabar tunggu restu. 🧘‍♂️

📌 Kalau mau curhat bisa langsung ke admin ya, kirim aja disini, siapa tahu mau ramalan zodiak kamu

Yuk, langsung aja dicoba. Jangan cuma dibaca doang. 😉
"""
);
            log.info("User {} Started Bot", chatId);
        } else if (text.equals("/help")) {
            handleHelpCommand(chatId);
            log.info("User {} Help", chatId);
        } else if (text.equals("/status")) {
            handleStatusCommand(chatId);
            log.info("User {} Status", chatId);
        } else if (text.startsWith("/pl")) {
            handlePlCommand(chatId, text);
            log.info("User {} Search By SPK {}", chatId, text.replace("/pl ", ""));
        } else if (text.startsWith("/fi")) {
            handleFindName(chatId, text);
            log.info("User {} Search By Name {}", chatId, text.replace("/fi ", ""));
        }
    }
    public void handleHelpCommand(String chatId) {
        String helpMessage = """
        🆘 *Panduan Penggunaan Bot Pelunasan* 🆘

        Berikut ini adalah daftar perintah yang dapat kamu gunakan:

        🔹 */pl [nomor]* — Cari nasabah berdasarkan nomor SPK.
        Contoh: `/pl Budi`

        🔹 */fi [nomor]* — Cari nasabah berdasarkan Nama.
        Contoh: `/fi 117204000001`

        🔹 */upload [link]* — Unduh data hasil pencarian dalam format CSV.
        Contoh: `/csv Budi`

        🔹 */next* dan */prev* — Navigasi halaman hasil pencarian.
        Gunakan setelah pencarian untuk pindah halaman.

        🔹 */status* — Tampilkan status bot, termasuk load sistem dan koneksi database.

        🔹 */help* — Tampilkan pesan bantuan ini.

        ℹ️ *Catatan*: Gunakan kata kunci yang spesifik untuk hasil pencarian terbaik.
        
        🔐 Data yang ditampilkan bersifat pribadi. Gunakan dengan bijak.

        🙏 Terima kasih telah menggunakan Pelunasan Bot!
        """;

        sendMessage(chatId, helpMessage);
    }
    private void sendUnauthorizedMessage(String chatId) {
        String message = """
        🚫 *Anda tidak diizinkan menggunakan bot ini.*

        Silakan ketik `.id` untuk mengetahui *Chat ID* Anda, lalu kirimkan ID tersebut ke bot ini.

        Admin akan memverifikasi dan memberikan akses jika diperlukan.

        🕒 Mohon tunggu balasan dari admin. Terima kasih 🙏
        """;

        sendMessage(chatId, message);
    }



    private void handleBroadcastUser(String text) {
        List<User> users = userService.findAllUsers();
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    telegramClient.execute(SendMessage.builder()
                            .chatId(user.getChatId().toString())
                            .text(text)
                            .build());
                    Thread.sleep(ThreadLocalRandom.current().nextInt(500, 2000));
                } catch (TelegramApiException | InterruptedException e) {
                    log.error("Error broadcasting message: {}", e.getMessage());
                }
            });
        }
        executorService.shutdown();
    }

    private void forwardUserMessageToOwner(Update update) {
        try {
            ForwardMessage forwardMessage = ForwardMessage.builder()
                    .chatId(ownerId)
                    .fromChatId(update.getMessage().getChatId())
                    .messageId(update.getMessage().getMessageId())
                    .build();
            telegramClient.execute(forwardMessage);
        } catch (TelegramApiException e) {
            log.error("Error forwarding message: {}", e.getMessage());
        }
    }

    private void forwardReplyToOriginalUser(Update update) {
        try {
            CopyMessage copyMessage = CopyMessage.builder()
                    .chatId(update.getMessage().getReplyToMessage().getForwardFrom().getId())
                    .fromChatId(ownerId)
                    .messageId(update.getMessage().getMessageId())
                    .build();
            telegramClient.execute(copyMessage);
        } catch (TelegramApiException e) {
            log.error("Error copying message: {}", e.getMessage());
        }
    }

    private void handleCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        String chatId = update.getCallbackQuery().getMessage().getChatId().toString();

        if (data.contains("pelunasan_")) handlePelunasanCallback(update, chatId, data);
        else if (data.contains("page_")) handlePaginationCallback(update, chatId, data);
    }

    private void handlePelunasanCallback(Update update, String chatId, String data) {
        long start = System.currentTimeMillis();
        try {
            Long customerId = Long.parseLong(data.split("_")[1]);
            Repayment repayment = repaymentService.findRepaymentById(customerId);
            if (repayment == null) {
                sendMessage(chatId, "Data tidak ditemukan untuk ID: " + customerId);
                return;
            }

            Map<String, Long> penalty = new PenaltyUtils().penalty(repayment.getStartDate(), repayment.getPenaltyLoan(), repayment.getProduct());
            String result = new RepaymentCalculator().calculate(repayment, penalty);

            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .text(result + "\n\nEksekusi dalam " + (System.currentTimeMillis() - start) + "ms")
                    .messageId(update.getCallbackQuery().getMessage().getMessageId())
                    .replyMarkup(new BackKeybaordUtils().backButton(data))
                    .parseMode("Markdown")
                    .build());

        } catch (Exception e) {
            sendMessage(chatId, "Terjadi kesalahan saat memproses data.");
            log.error("Error pelunasan callback", e);
        }
    }

    private void handlePaginationCallback(Update update, String chatId, String data) {
        long start = System.currentTimeMillis();
        int page = Integer.parseInt(data.split("_")[2]);
        String query = data.split("_")[1];
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        Page<Repayment> repayments = repaymentService.findName(query, page, 5);
        if (repayments.isEmpty()) {
            sendMessage(chatId, "Data tidak ditemukan.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder("\uD83D\uDCC4 Halaman " + (page + 1) + " dari " + repayments.getTotalPages() + "\n\n");
        resultQueryFindNameMethod(repayments, messageBuilder);

        try {
            telegramClient.execute(EditMessageText.builder()
                    .messageId(messageId)
                    .text(messageBuilder + "\n\nEksekusi dalam " + (System.currentTimeMillis() - start) + "ms")
                    .replyMarkup(new ButtonListForName().dynamicButtonName(repayments, page, query))
                    .chatId(chatId)
                            .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.info("Error edit message pagination: {}", e.getMessage());
        }
    }

    private void resultQueryFindNameMethod(Page<Repayment> repayments, StringBuilder messageBuilder) {
        repayments.getContent().forEach(dto -> messageBuilder.append("📄 *Informasi Nasabah*\n")
                .append("🔢 *No SPK*      : `").append(dto.getCustomerId()).append("`\n")
                .append("👤 *Nama*        : ").append(dto.getName()).append("\n")
                .append("🏡 *Alamat*      : ").append(dto.getAddress()).append("\n")
                .append("💰 *Plafond*     : ").append(formatRupiah(dto.getPlafond())).append("\n\n"));

    }

    private void handleFindName(String chatId, String text) {
        long start = System.currentTimeMillis();
        String name = text.replace("/fi ", "");
        if (text.equals("/fi")) {
            String message = """
            ❌ **Informasi** ❌
            
            Command `/fi` untuk mecari nasabah berdasarkan nama. Contoh: `/fi Budi`. Bukan Mencari yang tidak ada...
            """;
            sendMessage(chatId, message);
            return;
        }
        Page<Repayment> repayments = repaymentService.findName(name, 0, 5);

        if (repayments.isEmpty()) {
            String messageEmpty = String.format("""
                    ❌ **Informasi** ❌
                    
                    Gawat, data `%s` tidak ditemukan, Periksa Ejaan Anda, atau gunakan kata lebih sedikit
                    """, name);
            sendMessage(chatId, messageEmpty);
            return;
        }

        StringBuilder messageBuilder = new StringBuilder("\uD83D\uDCC4 Halaman 1 dari " + repayments.getTotalPages() + "\n\n");
        resultQueryFindNameMethod(repayments, messageBuilder);

        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(messageBuilder + "\n\nEksekusi dalam " + (System.currentTimeMillis() - start) + "ms")
                    .replyMarkup(new ButtonListForName().dynamicButtonName(repayments, 0, name))
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Gagal kirim pesan pagination", e);
        }
    }

    private void handleUploadCommand(String chatId, String text) {
        long start = System.currentTimeMillis();
        String link = text.split(" ", 2)[1];
        String fileName = link.substring(link.lastIndexOf("/") + 1);

        sendMessage(chatId, "Sedang mengunduh file: " + fileName);
        if (downloadAndProcessCsv(link, fileName)) {
            sendMessage(chatId, "✅ File berhasil diproses ke database: " + fileName + "\n\nEksekusi dalam " + (System.currentTimeMillis() - start) + "ms");
        } else {
            sendMessage(chatId, "❌ Gagal memproses file: " + fileName);
        }
    }

    private void handleStatusCommand(String chatId) {
        long start = System.currentTimeMillis();
        Repayment latest = repaymentService.findAll();
        String systemLoad = new SystemUtils().getSystemUtils();
        String status = String.format(
                """
                        🔧 **Status Bot - Pelunasan Bot** 🔧
                        
                        Bot sedang **aktif** dan siap menerima perintah. Berikut adalah informasi terkini:
                        
                        - **Waktu Terakhir Update**: 📅 *%s*
                        - **Jumlah Pengguna Terdaftar**: 📊 *%d*
                        - **Total Data Pelunasan**: 📦 *%d*
                        - **Load System**: ⚙️ *%s*
                        
                        Jika kamu ingin mencoba fitur lainnya, ketik `/help` untuk mendapatkan panduan lengkap! 🚀
                        
                        🔋 *Bot Dalam Keadaan Sehat* 
                        Eksekusi dalam %s ms""",
                latest.getCreatedAt(), userService.countUsers(), repaymentService.countAll(), systemLoad, System.currentTimeMillis()-start
        );
        sendMessage(chatId, status);
    }

    private void handlePlCommand(String chatId, String text) {
        long start = System.currentTimeMillis();
        try {
            Long customerId = Long.parseLong(text.split(" ")[1]);
            Repayment repayment = repaymentService.findRepaymentById(customerId);
            if (repayment == null) {
                sendMessage(chatId, "Data tidak ditemukan untuk ID: " + customerId);
                return;
            }

            Map<String, Long> penalty = new PenaltyUtils().penalty(repayment.getStartDate(), repayment.getPenaltyLoan(), repayment.getProduct());
            String result = new RepaymentCalculator().calculate(repayment, penalty);
            sendMessage(chatId, result + "\n\nEksekusi dalam " + (System.currentTimeMillis() - start) + "ms");
        } catch (ArrayIndexOutOfBoundsException e) {
            sendMessage(chatId, """
                    ❌ **Informasi** ❌
                    
                    Gunakan `/pl <No SPK>` untuk mencari SPK dan melakukan penghitungan Pelunasan. Sekali lagi, mencari SPK bukan mencari PL...
                    """);
            log.error("Mengirimkan Perintah PL", e);
        }
    }

    private boolean downloadAndProcessCsv(String fileUrl, String fileName) {
        try (InputStream inputStream = new URL(fileUrl).openStream()) {
            Path output = Paths.get("files", fileName);
            Files.createDirectories(output.getParent());
            Files.copy(inputStream, output, StandardCopyOption.REPLACE_EXISTING);
            if (fileName.endsWith(".csv")) {
                repaymentService.parseCsvAndSaveIntoDatabase(output);
            }
            return true;
        } catch (Exception e) {
            log.warn("Gagal download/proses file: {}", e.getMessage());
            return false;
        }
    }

    private void sendMessage(String chatId, String message) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(message)
                    .parseMode("Markdown")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Gagal kirim pesan", e);
        }
    }

    private String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }
}