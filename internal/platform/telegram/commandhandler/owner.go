package commandhandler

import (
	"context"
	"regexp"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"github.com/hikari-work/cek-pelunasan/internal/platform/telegram"
	"github.com/hikari-work/cek-pelunasan/internal/service/auth"
)

// Owner /owner — fallback handler untuk pesan non-command:
//
//   - "/owner": tampilkan info kontak owner & instruksi
//   - user authorized + ada pola 12 digit: tampilkan menu services (Pelunasan/Tabungan)
//   - selain itu (non-owner): forward pesan ke owner agar bisa direspons manual
//
// Padanan InteractWithOwnerHandler — tetapi bagian "/id" sudah di handler ID terpisah.
type Owner struct {
	OwnerID int64
	Authed  *auth.AuthorizedChats
}

var twelveDigitPattern = regexp.MustCompile(`\d{12}`)

func (h *Owner) Command() string     { return "/owner" }
func (h *Owner) Description() string { return "Hubungi owner bot" }

func (h *Owner) Handle(ctx context.Context, b *telegram.Bot, msg *tgbotapi.Message) {
	chatID := msg.Chat.ID
	text := strings.TrimSpace(msg.Text)

	if text == h.Command() {
		_, _ = b.SendText(chatID,
			"Kirim pesan apapun ke bot ini, pesan akan diteruskan ke owner.\n"+
				"Owner akan membalas langsung kalau memang perlu.")
		return
	}

	if h.Authed.IsAuthorized(chatID) && twelveDigitPattern.MatchString(text) {
		num := twelveDigitPattern.FindString(text)
		kb := selectServicesKeyboard(num)
		_, _ = b.SendTextWithKeyboard(chatID, "Pilih salah satu action dibawah ini", kb)
		return
	}

	if h.OwnerID == 0 || chatID == h.OwnerID {
		return
	}
	forward := tgbotapi.NewForward(h.OwnerID, chatID, msg.MessageID)
	_, _ = b.API.Send(forward)
}

// selectServicesKeyboard padanan DirectMessageButton.selectServices.
// Data callback: "services_<Pelunasan|Tabungan>_<num>".
func selectServicesKeyboard(num string) tgbotapi.InlineKeyboardMarkup {
	return tgbotapi.NewInlineKeyboardMarkup(
		tgbotapi.NewInlineKeyboardRow(
			tgbotapi.NewInlineKeyboardButtonData("Pelunasan", "services_Pelunasan_"+num),
			tgbotapi.NewInlineKeyboardButtonData("Tabungan", "services_Tabungan_"+num),
		),
	)
}

// IsTwelveDigit dipakai router untuk fallback kalau pesan punya 12 digit
// tapi bukan command. Tidak dipakai di sini, sekedar contoh untuk dokumentasi.
func IsTwelveDigit(s string) bool {
	return twelveDigitPattern.MatchString(s)
}
