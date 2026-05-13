package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.MinBungaSession;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.log.DataUpdateLogService;
import org.cekpelunasan.core.service.minbunga.MinBungaSessionService;
import org.cekpelunasan.core.service.users.UserService;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.MinBungaCalendarBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinBungaCommand extends AbstractCommandHandler {

    private final UserService userService;
    private final BillService billService;
    private final MinBungaSessionService sessionService;
    private final DataUpdateLogService dataUpdateLogService;
    private final MinBungaCalendarBuilder calendarBuilder;

    private static final ZoneOffset WIB = ZoneOffset.ofHours(7);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy", new Locale("id", "ID"));

    @Override
    public String getCommand() {
        return "/minbunga";
    }

    @Override
    public String getDescription() {
        return "Menampilkan tagihan minimal bunga berdasarkan tanggal penagihan yang dipilih.";
    }

    @Override
    @RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        // Cek apakah data TAGIHAN sudah diupdate hari ini
        LocalDate today = LocalDate.now(WIB);
        boolean isUpToDate = dataUpdateLogService.getLastUpdateDate("TAGIHAN")
            .map(d -> d.isEqual(today))
            .orElse(false);

        if (!isUpToDate) {
            String lastUpdate = dataUpdateLogService.getLastUpdateDate("TAGIHAN")
                .map(d -> d.format(DATE_FORMAT))
                .orElse("belum pernah");
            sendMessage(chatId,
                "❌ *Data TAGIHAN belum diperbarui hari ini.*\n" +
                "_Terakhir diperbarui: " + lastUpdate + "_\n" +
                "_Mohon import ulang data sebelum menggunakan fitur ini._",
                client);
            return Mono.empty();
        }

        return userService.findUserByChatId(chatId)
            .switchIfEmpty(Mono.fromRunnable(() ->
                sendMessage(chatId, "❌ *User tidak ditemukan*", client)
            ))
            .flatMap(user -> {
                if (user.getRoles() == null) return Mono.empty();

                return switch (user.getRoles()) {
                    case AO -> handleAo(chatId, user.getUserCode(), client);
                    case PIMP, ADMIN -> handlePimpAdmin(chatId, user.getUserCode(), client);
                };
            })
            .then();
    }

    private Mono<Void> handleAo(long chatId, String userCode, SimpleTelegramClient client) {
        return sessionService.getSession(chatId)
            .doOnNext(prev -> deletePreviousIfAny(chatId, prev, client))
            .then(sessionService.getOrCreate(chatId, userCode, "AO"))
            .flatMap(session -> Mono.fromRunnable(() -> {
                TdApi.ReplyMarkupInlineKeyboard calendar =
                    calendarBuilder.buildCalendar(userCode, new ArrayList<>(), false);
                long msgId = telegramMessageService.sendTextWithKeyboard(chatId,
                    "📅 *Pilih Tanggal Penagihan*\n\n" +
                    "_Pilih satu atau beberapa tanggal target penagihan._\n" +
                    "_Bot akan menampilkan nasabah yang DayLate-nya tidak melebihi 90 hari pada tanggal tersebut._",
                    calendar, client);
                if (msgId > 0) sessionService.setMessageId(chatId, msgId).subscribe();
            }))
            .then();
    }

    private Mono<Void> handlePimpAdmin(long chatId, String userCode, SimpleTelegramClient client) {
        return sessionService.getSession(chatId)
            .doOnNext(prev -> deletePreviousIfAny(chatId, prev, client))
            .then(billService.lisAllBranch())
            .flatMap(branches -> Mono.fromRunnable(() -> {
                TdApi.ReplyMarkupInlineKeyboard keyboard = buildBranchKeyboard(branches);
                long msgId = telegramMessageService.sendTextWithKeyboard(chatId,
                    "🏦 *Pilih Cabang*\n\n_Pilih cabang yang akan dicek tagihan minimal bunganya._",
                    keyboard, client);
                if (msgId > 0) {
                    sessionService.getOrCreate(chatId, "", "BRANCH")
                        .then(sessionService.setMessageId(chatId, msgId))
                        .subscribe();
                }
            }))
            .then();
    }

    private void deletePreviousIfAny(long chatId, MinBungaSession prev, SimpleTelegramClient client) {
        if (prev != null && prev.getMessageId() != null) {
            telegramMessageService.delete(chatId, prev.getMessageId(), client);
        }
    }

    private TdApi.ReplyMarkupInlineKeyboard buildBranchKeyboard(Set<String> branches) {
        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        List<TdApi.InlineKeyboardButton> currentRow = new ArrayList<>();

        for (String branch : branches) {
            currentRow.add(button(branch, "minbungabranch_" + branch));
            if (currentRow.size() == 3) {
                rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
                currentRow = new ArrayList<>();
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow.toArray(new TdApi.InlineKeyboardButton[0]));
        }

        return new TdApi.ReplyMarkupInlineKeyboard(rows.toArray(new TdApi.InlineKeyboardButton[0][]));
    }

    private TdApi.InlineKeyboardButton button(String text, String callbackData) {
        TdApi.InlineKeyboardButton btn = new TdApi.InlineKeyboardButton();
        btn.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = callbackData.getBytes(StandardCharsets.UTF_8);
        btn.type = type;
        return btn;
    }
}
