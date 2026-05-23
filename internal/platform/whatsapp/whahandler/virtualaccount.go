package whahandler

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strings"

	"github.com/hikari-work/cek-pelunasan/internal/platform/whatsapp"
	"github.com/hikari-work/cek-pelunasan/internal/service/bill"
	"github.com/hikari-work/cek-pelunasan/internal/service/savings"
)

// VirtualAccount menangani perintah "{prefix}va {nomor}" — generate Virtual
// Account dari empat bank (Mandiri, BRI, Danamon, BNI) untuk nomor SPK
// kredit atau nomor rekening tabungan.
//
// Lookup order: Bills (kredit) dulu — kalau ada → balas dengan VA. Kalau
// tidak ada → cek Savings (tabungan); kalau ada → balas dengan VA + pesan
// tambahan bahwa nomor VA tabungan harus didaftarkan manual ke kantor.
type VirtualAccount struct {
	Bills   *bill.Service
	Savings *savings.Service
	Sender  *whatsapp.Sender
	Prefix  string // default "." kalau kosong
}

func (h *VirtualAccount) Match(m *whatsapp.IncomingMessage) bool {
	if m == nil {
		return false
	}
	return strings.HasPrefix(m.Body, prefixed(h.Prefix, "va")+" ")
}

func (h *VirtualAccount) Handle(ctx context.Context, m *whatsapp.IncomingMessage) {
	if h.Sender == nil || h.Bills == nil || h.Savings == nil {
		return
	}

	cmd := prefixed(h.Prefix, "va")
	number := strings.TrimSpace(strings.TrimPrefix(m.Body, cmd+" "))
	if number == "" {
		_, _ = h.Sender.SendText(ctx, m.ChatJID(),
			"❌ Format: "+cmd+" [nomor SPK atau nomor rekening]", &m.Info)
		return
	}

	name, accountNum, addr, isSavings, err := h.lookupAccount(ctx, number)
	if err != nil {
		slog.Error("va: lookup gagal", "number", number, "err", err)
		_, _ = h.Sender.SendText(ctx, m.ChatJID(),
			"❌ Terjadi kesalahan sistem. Silakan coba lagi.", &m.Info)
		return
	}
	if name == "" {
		_, _ = h.Sender.SendText(ctx, m.ChatJID(),
			"Nomor akun tidak ditemukan.", &m.Info)
		return
	}

	msg := buildVAMessage(name, accountNum, addr)
	if _, err := h.Sender.SendText(ctx, m.ChatJID(), msg, &m.Info); err != nil {
		slog.Warn("va: kirim pesan VA gagal", "err", err)
	}

	if isSavings {
		_, _ = h.Sender.SendText(ctx, m.ChatJID(),
			"Nomor _Virtual Account_ tersebut harus didaftarkan secara manual", nil)
	}
}

// lookupAccount cari Bills lalu Savings. Return name "" kalau tidak ada di
// keduanya. Address opsional di savings (legacy ngirim "" untuk address
// yang kosong, kita ikutin).
func (h *VirtualAccount) lookupAccount(ctx context.Context, number string) (name, accountNum, addr string, isSavings bool, err error) {
	b, err := h.Bills.GetByID(ctx, number)
	if err != nil {
		return "", "", "", false, err
	}
	if b != nil {
		return b.Name, b.NoSpk, b.Address, false, nil
	}

	s, err := h.Savings.FindByID(ctx, number)
	if err != nil {
		return "", "", "", false, err
	}
	if s != nil {
		return s.Name, s.TabID, s.Address, true, nil
	}
	return "", "", "", false, nil
}

func buildVAMessage(name, accountNum, addr string) string {
	var b strings.Builder
	b.WriteString("*Informasi Akun*\n\n")
	_, _ = fmt.Fprintf(&b, "No SPK: _%s_\n", accountNum)
	_, _ = fmt.Fprintf(&b, "Nama: _%s_\n", name)
	_, _ = fmt.Fprintf(&b, "Alamat: _%s_\n\n", addr)

	b.WriteString("*Virtual Account Numbers*\n\n")
	b.WriteString(generateVA("🏦 *Mandiri*", accountNum, vaMandiri))
	b.WriteString("\n\n")
	b.WriteString(generateVA("🏦 *BRI (BRIVA)*", accountNum, vaBRI))
	b.WriteString("\n\n")
	b.WriteString(generateVA("🏦 *Danamon*", accountNum, vaDanamon))
	b.WriteString("\n\n")
	b.WriteString(generateVA("🏦 *BNI*", accountNum, vaBNI))
	return b.String()
}

// vaFormatter format nomor VA spesifik per bank. Return error kalau panjang
// nomor tidak cukup (kurang dari 12 digit).
type vaFormatter func(accountNum string) (string, error)

func generateVA(label string, accountNum string, fn vaFormatter) string {
	out, err := fn(accountNum)
	if err != nil {
		return label + "\nFormat nomor tidak valid"
	}
	return label + "\n" + out
}

// Mandiri prefix "86219 1 " + segmen 4 digit awal + 6 digit (idx 6..12).
// Catatan di legacy, segmen ke-2 di-skip — bukan typo, ikut perilaku Java.
func vaMandiri(accountNum string) (string, error) {
	if len(accountNum) < 12 {
		return "", errVATooShort
	}
	return "86219 1 " + accountNum[:4] + " " + accountNum[6:12], nil
}

// BRI prefix "14654 " + 4 digit awal + 6 digit (idx 6..12).
func vaBRI(accountNum string) (string, error) {
	if len(accountNum) < 12 {
		return "", errVATooShort
	}
	return "14654 " + accountNum[:4] + " " + accountNum[6:12], nil
}

// Danamon 7997 + 4 digit awal + 2 digit (4..6) + 6 digit (6..12).
func vaDanamon(accountNum string) (string, error) {
	if len(accountNum) < 12 {
		return "", errVATooShort
	}
	return "7997 " + accountNum[:4] + " " + accountNum[4:6] + " " + accountNum[6:12], nil
}

// BNI 8743 + format sama dengan Danamon.
func vaBNI(accountNum string) (string, error) {
	if len(accountNum) < 12 {
		return "", errVATooShort
	}
	return "8743 " + accountNum[:4] + " " + accountNum[4:6] + " " + accountNum[6:12], nil
}

var errVATooShort = errors.New("nomor < 12 digit")
