package org.cekpelunasan.platform.telegram.command.handler;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cekpelunasan.annotation.RequireAuth;
import org.cekpelunasan.core.entity.AccountOfficerRoles;
import org.cekpelunasan.core.entity.simulasiangsuran.SimulasiAngsuranResult;
import org.cekpelunasan.core.service.bill.BillService;
import org.cekpelunasan.core.service.simulasiangsuran.SimulasiAngsuranService;
import org.cekpelunasan.platform.telegram.command.AbstractCommandHandler;
import org.cekpelunasan.utils.SimulasiAngsuranFormatter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulasiAngsuranCommand extends AbstractCommandHandler {

    private final BillService billService;
    private final SimulasiAngsuranService simulasiAngsuranService;
    private final SimulasiAngsuranFormatter formatter;

    @Override
    public String getCommand() {
        return "/simangsuran";
    }

    @Override
    public String getDescription() {
        return "Simulasi angsuran minimal agar kredit tetap di kolektibilitas 02.";
    }

    @Override
    @RequireAuth(roles = {AccountOfficerRoles.AO, AccountOfficerRoles.PIMP, AccountOfficerRoles.ADMIN})
    public Mono<Void> process(TdApi.UpdateNewMessage update, SimpleTelegramClient client) {
        return super.process(update, client);
    }

    @Override
    public Mono<Void> process(long chatId, String text, SimpleTelegramClient client) {
        String[] parts = text.trim().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            sendMessage(chatId, "❌ *Format salah.*\nGunakan: `/simangsuran <no_spk>`", client);
            return Mono.empty();
        }

        String spk = parts[1].trim();

        return billService.getBillById(spk)
            .switchIfEmpty(Mono.fromRunnable(() ->
                sendMessage(chatId, "❌ *SPK tidak ditemukan:* `" + spk + "`", client)
            ))
            .flatMap(bill -> Mono.fromRunnable(() -> {
                SimulasiAngsuranResult result = simulasiAngsuranService.hitung(bill);
                String pesan = formatter.format(result, bill);
                sendMessage(chatId, pesan, client);
            }))
            .then();
    }
}
