package org.cekpelunasan.handler.command.handler;

import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.repository.UserRepository;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class RegisterUsers implements CommandProcessor {
    private final BillService billService;
    private final UserService userService;
    private final UserRepository userRepository;

    public RegisterUsers(BillService billService, UserService userService, UserRepository userRepository) {
        this.billService = billService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public String getCommand() {
        return "/otor";
    }

    @Override
    public String getDescription() {
        return """
                Gunakan Command Ini untuk mendaftarkan user
                Berdasarkan User ID, Pimpinan atau AO
                """;
    }

    @Override
    public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
        return CompletableFuture.runAsync(() -> {
            long chatId = update.getMessage().getChatId();
            String[] parts = update.getMessage().getText().split(" ");

            if (parts.length < 2) {
                sendMessage(chatId, getHelp(), telegramClient);
                return;
            }

            String target = parts[1];
            Optional<User> userOptional = userService.findUserByChatId(chatId);

            if (userOptional.isEmpty()) {
                sendMessage(chatId, "User tidak ditemukan", telegramClient);
                return;
            }

            User user = userOptional.get();

            if (user.getRoles() != null) {
                sendMessage(chatId, "⚠ *Anda sudah terdaftar sebagai " + user.getRoles() + "*", telegramClient);
                return;
            }

            if (target.length() == 3 && isValidAO(target)) {
                registerUser(user, AccountOfficerRoles.AO, target, "AO", chatId, telegramClient);
                return;
            }

            if (isNumber(target) && isValidBranch(target)) {
                registerUser(user, AccountOfficerRoles.PIMP, target, "Pimpinan", chatId, telegramClient);
                return;
            }

            sendMessage(chatId, "❌ *Format tidak valid*\n\nContoh: /otor 1234567890", telegramClient);
        });
    }

    private void registerUser(User user, AccountOfficerRoles role, String code, String label, Long chatId, TelegramClient telegramClient) {
        user.setUserCode(code);
        user.setRoles(role);
        userRepository.save(user);
        sendMessage(chatId, "✅ User berhasil didaftarkan sebagai *" + label + "*", telegramClient);
    }

    public String getHelp() {
        return """
                Gunakan /otor <kode cabang> atau
                /otor <kode ao>
                """;
    }

    public boolean isNumber(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean isValidBranch(String branchCode) {
        return billService.listAllBrach().contains(branchCode);
    }

    public boolean isValidAO(String aoCode) {
        return billService.findAllAccountOfficer().contains(aoCode);
    }
}

