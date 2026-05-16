package org.cekpelunasan.platform.telegram.bot;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.platform.telegram.command.CommandProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mendaftarkan daftar perintah bot ke Telegram secara otomatis.
 *
 * <p>Mengumpulkan semua {@link CommandProcessor} yang ada di Spring context, lalu mengirim
 * {@link TdApi.SetCommands} sehingga UI Telegram menampilkan menu autocomplete saat user
 * mengetik {@code /}. Hanya command yang punya {@code getDescription()} non-blank yang
 * didaftarkan, karena Telegram mensyaratkan description 1–256 karakter.</p>
 *
 * <p>Pendaftaran dijalankan satu kali per siklus aplikasi ketika authorization state TDLight
 * sudah {@code Ready}.</p>
 *
 * @see <a href="https://tdlight-team.github.io/tdlight-docs/tdlight.api/it/tdlight/jni/TdApi.SetCommands.html">TdApi.SetCommands</a>
 */
@Slf4j
@Component
public class BotCommandRegistrar {

    private final List<CommandProcessor> processors;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public BotCommandRegistrar(List<CommandProcessor> processors) {
        this.processors = processors;
    }

    /**
     * Membangun {@link TdApi.SetCommands} dari daftar processor lalu mengirimkannya ke Telegram.
     *
     * <p>Idempoten dalam satu siklus aplikasi: panggilan kedua diabaikan sehingga aman dipanggil
     * dari listener authorization state yang bisa tertrigger lebih dari sekali.</p>
     *
     * @param client koneksi TDLight aktif yang sudah authorized sebagai bot
     */
    public void register(SimpleTelegramClient client) {
        if (!registered.compareAndSet(false, true)) {
            return;
        }

        TdApi.BotCommand[] commands = processors.stream()
            .filter(p -> p.getDescription() != null && !p.getDescription().isBlank())
            .map(p -> new TdApi.BotCommand(stripSlash(p.getCommand()), p.getDescription()))
            .toArray(TdApi.BotCommand[]::new);

        if (commands.length == 0) {
            log.warn("No bot commands to register");
            return;
        }

        TdApi.SetCommands request = new TdApi.SetCommands(
            new TdApi.BotCommandScopeDefault(),
            "",
            commands
        );

        client.send(request, result -> {
            if (result.isError()) {
                registered.set(false);
                log.error("Failed to register {} bot commands: {}", commands.length, result.getError().message);
            } else {
                log.info("Registered {} bot commands to Telegram", commands.length);
            }
        });
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
