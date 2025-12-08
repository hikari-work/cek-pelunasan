package org.cekpelunasan.handler.command.handler;

import lombok.RequiredArgsConstructor;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.entity.AccountOfficerRoles;
import org.cekpelunasan.entity.Bills;
import org.cekpelunasan.entity.User;
import org.cekpelunasan.handler.callback.pagination.PaginationBillsByNameCallbackHandler;
import org.cekpelunasan.handler.command.CommandProcessor;
import org.cekpelunasan.service.Bill.BillService;
import org.cekpelunasan.service.users.UserService;
import org.cekpelunasan.service.telegram.TelegramMessageService;
import org.cekpelunasan.utils.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class FindByDueDate implements CommandProcessor {
 private final UserService userService;
 private final BillService billService;
 private final DateUtils dateUtils;
 private final TelegramMessageService telegramMessageService;


	@Override
	public String getCommand() {
		return "/jb";
	}

	@Override
	public String getDescription() {
		return "📅 *Cek tagihan jatuh tempo hari ini*.\nGunakan command ini untuk melihat daftar tagihan Anda yang jatuh tempo hari ini.";
	}

	@Override
	@RequireAuth(roles = {AccountOfficerRoles.ADMIN, AccountOfficerRoles.AO, AccountOfficerRoles.PIMP})
	public CompletableFuture<Void> process(Update update, TelegramClient telegramClient) {
		return CommandProcessor.super.process(update, telegramClient);
	}

	@Override
	@Async
	public CompletableFuture<Void> process(long chatId, String text, TelegramClient telegramClient) {
		return CompletableFuture.runAsync(() -> {

			Optional<User> userOpt = userService.findUserByChatId(chatId);
   			if (userOpt.isEmpty()) {
                telegramMessageService.sendText(chatId, "❌ *User tidak ditemukan*", telegramClient);
                return;
            }

			User user = userOpt.get();
			String userCode = user.getUserCode();
			LocalDateTime today = LocalDateTime.now();

			Page<Bills> billsPage = null;
			if (user.getRoles() != null) {
				billsPage = switch (user.getRoles()) {
					case AO -> billService.findDueDateByAccountOfficer(userCode, dateUtils.converterDate(today), 0, 5);
					case PIMP -> billService.findBranchAndPayDown(userCode, dateUtils.converterDate(today), 0, 5);
					default -> Page.empty();
				};
			}
			if (billsPage != null && billsPage.isEmpty()) {
                telegramMessageService.sendText(chatId, "❌ *Data tidak ditemukan*", telegramClient);
                return;
            }

			StringBuilder builder = new StringBuilder("Halaman 1 dari " + (billsPage != null ? billsPage.getTotalPages() : 0) + "\n📋 *Daftar Tagihan Jatuh Tempo Hari Ini:*\n\n");
			if (billsPage != null) {
				billsPage.forEach(bills -> builder.append(messageBuilder(bills)));
			}

			InlineKeyboardMarkup markup = null;
			if (billsPage != null) {
				markup = new PaginationBillsByNameCallbackHandler()
					.dynamicButtonName(billsPage, 0, userCode);
			}

            telegramMessageService.sendTextWithKeyboard(chatId, builder.toString(), markup, telegramClient);
        });
    }

	public String messageBuilder(Bills bills) {
		return String.format("""
				👤 *%s*
				┌──────────────────────
				│ 📎 *INFORMASI KREDIT*
				│ ├─ 🔖 SPK      : `%s`
				│ ├─ 📍 Alamat   : %s
				│ └─ 📅 Jth Tempo: %s
				│
				│ 💰 *RINCIAN*
				│ ├─ 💸 Tagihan  : %s
				│ └─ 👨‍💼 AO       : %s
				└──────────────────────
				
				ℹ️ _Tap SPK untuk menyalin_
				""",
			bills.getName(),
			bills.getNoSpk(),
			formatAddress(bills.getAddress()),
			formatDate(bills.getPayDown()),
			formatRupiah(bills.getFullPayment()),
			bills.getAccountOfficer()
		);
	}

	private String formatAddress(String address) {
		return address.length() > 30 ? address.substring(0, 27) + "..." : address;
	}

	private String formatDate(String date) {
		return date != null ? date : "Tidak tersedia";
	}

	private String formatRupiah(long amount) {
		return String.format("Rp %,d", amount);
	}

    
}