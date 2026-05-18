package whatsapp

// Webhook dari gateway WhatsApp. Field-set apa adanya — yang kita butuh
// cuma event, from, payload.body, payload.id, isGroup. Sisanya diabaikan.
type Webhook struct {
	Event   string         `json:"event"`
	From    string         `json:"from"`
	Payload *WebhookPayload `json:"payload,omitempty"`
	Media   any            `json:"media,omitempty"`
}

type WebhookPayload struct {
	ID   string `json:"id"`
	Body string `json:"body"`
}

// CleanSenderID mengembalikan bagian "<phone>@..." yang diparse jadi nomor saja.
// Format gateway: "62812xxxxxxx@s.whatsapp.net" untuk DM, "...@g.us" untuk grup.
// Kalau di grup, From biasanya "<group>@g.us" sedangkan pengirim asli ada di
// field tersendiri yang tidak kita perlukan untuk routing dasar.
func (w *Webhook) CleanSenderID() string {
	if w == nil || w.From == "" {
		return ""
	}
	if at := indexByte(w.From, '@'); at > 0 {
		return w.From[:at]
	}
	return w.From
}

// IsGroupChat pesan dari grup ditandai oleh suffix @g.us.
func (w *Webhook) IsGroupChat() bool {
	return w != nil && hasSuffix(w.From, "@g.us")
}

func indexByte(s string, c byte) int {
	for i := 0; i < len(s); i++ {
		if s[i] == c {
			return i
		}
	}
	return -1
}

func hasSuffix(s, suf string) bool {
	return len(s) >= len(suf) && s[len(s)-len(suf):] == suf
}
