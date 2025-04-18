package org.cekpelunasan.handler.command;

import org.cekpelunasan.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CommandHandler {

    private final Map<String, CommandProcessor> processor;
    private final UserService userService;

    private final List<Long> authorizedChats;

    @Autowired CommandHandler(List<CommandProcessor> processorList, UserService userService, List<Long> authorizedChats) {
        this.processor = processorList.stream().collect(Collectors.toMap(CommandProcessor::getCommand, p -> p));
        this.userService = userService;
        this.authorizedChats = authorizedChats;
    }

    public void handle(Update update, TelegramClient telegramClient) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String command = update.getMessage().getText().split(" ")[0];
            CommandProcessor commandProcessor = processor.getOrDefault(command, processor.get("/id"));
            commandProcessor.process(update, telegramClient);
        }
    }
    public boolean isAuthorized(Long chatId) {
        if (userService.findUser(chatId) != null) {
            addAuthorizedChat(chatId);
        }
        return authorizedChats.contains(chatId);
    }
    public void addAuthorizedChat(Long chatId) {
        authorizedChats.add(chatId);
    }
    public String sendUnauthorizedMessage() {
        return  """
                🚫 *Anda tidak diizinkan menggunakan bot ini.*

                Silakan ketik `.id` untuk mengetahui *Chat ID* Anda, lalu kirimkan ID tersebut ke bot ini.

                Admin akan memverifikasi dan memberikan akses jika diperlukan.

                🕒 Mohon tunggu balasan dari admin. Terima kasih 🙏
                """;
    }

    public String sendWelcomeMessage() {
        return """
                💌 Anda sudah diizinkan menggunakan Bot Pelunasan Ini
                
                Silakan untuk mengecek `/help` untuk tahu cara penggunaannya
                """;
    }

}
