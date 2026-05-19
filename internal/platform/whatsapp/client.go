package whatsapp

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"sync"

	"github.com/mdp/qrterminal/v3"
	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
)

// Client membungkus *whatsmeow.Client supaya layer atas tidak perlu tahu
// detail QR pairing, reconnect, atau cara whatsmeow expose event.
//
// Lifecycle:
//
//	c, err := NewClient(store, ClientOptions{...})
//	if err != nil { ... }
//	defer c.Close()
//	if err := c.Start(ctx); err != nil { ... } // QR pairing kalau perlu
//	... gunakan c.WAClient untuk send/event handler ...
type Client struct {
	WAClient *whatsmeow.Client

	store *Store
	qr    QRWriter
	log   *slog.Logger

	startOnce sync.Once
	startErr  error
}

// ClientOptions parameter opsional saat init client.
type ClientOptions struct {
	// DeviceName muncul di "Linked devices" di HP user. Kalau kosong,
	// pakai default whatsmeow.
	DeviceName string

	// LogLevel untuk log internal whatsmeow (DEBUG/INFO/WARN/ERROR).
	LogLevel string

	// QRWriter optional — kalau nil, QR dicetak ASCII ke os.Stdout.
	QRWriter QRWriter
}

// QRWriter dipanggil setiap kali whatsmeow generate kode QR baru
// (rotasi setiap ~30 detik sampai discan dari HP).
type QRWriter interface {
	WriteQR(code string)
}

type stdoutQR struct{}

func (stdoutQR) WriteQR(code string) {
	qrterminal.GenerateHalfBlock(code, qrterminal.L, os.Stdout)
}

// NewClient bangun client whatsmeow yang siap dipakai.
// Belum konek — panggil Start untuk membuka koneksi (dan QR kalau perlu).
func NewClient(s *Store, opts ClientOptions) (*Client, error) {
	if s == nil || s.Container == nil || s.Device == nil {
		return nil, errors.New("whatsapp client: store belum di-init")
	}

	if name := opts.DeviceName; name != "" {
		// SetOSInfo memengaruhi label device di "Linked devices" HP user.
		store.SetOSInfo(name, [3]uint32{1, 0, 0})
	}

	clientLog := waLog.Stdout("WA", normalizeLogLevel(opts.LogLevel), true)

	qr := opts.QRWriter
	if qr == nil {
		qr = stdoutQR{}
	}

	c := &Client{
		WAClient: whatsmeow.NewClient(s.Device, clientLog),
		store:    s,
		qr:       qr,
		log:      slog.With("component", "whatsapp"),
	}
	c.WAClient.AddEventHandler(c.lifecycleHandler)
	return c, nil
}

// Start bangun koneksi ke WhatsApp.
//
//   - Kalau device sudah pernah pair → langsung Connect.
//   - Kalau belum → daftar QR channel dulu, lalu Connect, lalu loop kode QR
//     ke QRWriter sampai user scan dari HP atau ctx batal.
//
// Aman dipanggil sekali. Pemanggilan kedua jadi no-op (return error
// dari panggilan pertama, atau nil kalau sukses).
func (c *Client) Start(ctx context.Context) error {
	c.startOnce.Do(func() {
		c.startErr = c.start(ctx)
	})
	return c.startErr
}

func (c *Client) start(ctx context.Context) error {
	if c.store.IsLoggedIn() {
		c.log.Info("whatsapp: device sudah pair, connecting", "jid", c.store.Device.ID.String())
		if err := c.WAClient.Connect(); err != nil {
			return fmt.Errorf("connect whatsmeow: %w", err)
		}
		return nil
	}

	c.log.Info("whatsapp: belum pair, mulai QR flow")
	qrChan, err := c.WAClient.GetQRChannel(ctx)
	if err != nil {
		return fmt.Errorf("get QR channel: %w", err)
	}
	if err := c.WAClient.Connect(); err != nil {
		return fmt.Errorf("connect whatsmeow: %w", err)
	}

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case evt, ok := <-qrChan:
			if !ok {
				return errors.New("QR channel ditutup tanpa pairing")
			}
			switch evt.Event {
			case whatsmeow.QRChannelEventCode:
				c.log.Info("whatsapp: scan QR di HP — WhatsApp → Linked devices → Link a device")
				c.qr.WriteQR(evt.Code)
			case whatsmeow.QRChannelSuccess.Event:
				c.log.Info("whatsapp: paired", "jid", c.store.Device.ID.String())
				return nil
			case whatsmeow.QRChannelTimeout.Event:
				return errors.New("QR pairing timeout — coba ulang")
			case whatsmeow.QRChannelClientOutdated.Event:
				return errors.New("whatsmeow client outdated — update dependency")
			case whatsmeow.QRChannelScannedWithoutMultidevice.Event:
				return errors.New("WhatsApp multi-device belum aktif di HP")
			case whatsmeow.QRChannelEventError:
				if evt.Error != nil {
					return fmt.Errorf("QR error: %w", evt.Error)
				}
				return errors.New("QR error tidak diketahui")
			}
		}
	}
}

// Close putuskan koneksi. Aman dipanggil meski Start belum sukses.
// Tidak menutup *Store — caller pegang lifecycle store sendiri.
func (c *Client) Close() {
	if c == nil || c.WAClient == nil {
		return
	}
	c.WAClient.Disconnect()
}

// Sender return helper untuk kirim/edit/react/upload pesan via client ini.
// Aman dipanggil meski Start belum sukses — method Sender akan return error
// "client nil" sampai koneksi terbentuk.
func (c *Client) Sender() *Sender {
	if c == nil {
		return nil
	}
	return NewSender(c.WAClient)
}

// lifecycleHandler hanya log connection state untuk visibilitas operasional.
// Dispatch event ke router adalah tanggung jawab adapter di task berikutnya.
func (c *Client) lifecycleHandler(rawEvt any) {
	switch evt := rawEvt.(type) {
	case *events.Connected:
		c.log.Info("whatsapp: connected")
	case *events.Disconnected:
		c.log.Warn("whatsapp: disconnected — whatsmeow akan retry otomatis")
	case *events.LoggedOut:
		c.log.Error("whatsapp: logged out", "reason", evt.Reason.String())
	case *events.PairSuccess:
		c.log.Info("whatsapp: pair success", "id", evt.ID.String(), "platform", evt.Platform)
	case *events.StreamError:
		c.log.Warn("whatsapp: stream error", "code", evt.Code)
	}
}
