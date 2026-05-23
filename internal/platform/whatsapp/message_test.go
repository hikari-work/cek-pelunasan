package whatsapp

import (
	"strings"
	"testing"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
)

func stringPtr(s string) *string {
	return &s
}

func TestFromEvent_Conversation(t *testing.T) {
	evt := &events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:    types.JID{User: "62811", Server: "s.whatsapp.net"},
				Sender:  types.JID{User: "62811", Server: "s.whatsapp.net"},
				IsGroup: false,
			},
			ID:       "ABC",
			PushName: "Budi",
		},
		Message: &waE2E.Message{
			Conversation: stringPtr("  .p 010600001234  "),
		},
	}

	m := fromEvent(evt)
	if m == nil {
		t.Fatal("expected message, got nil")
	}
	if m.Body != ".p 010600001234" {
		t.Errorf("Body = %q, want trimmed", m.Body)
	}
	if m.MediaKind != "" {
		t.Errorf("MediaKind = %q, want empty", m.MediaKind)
	}
	if m.RepliedToID != "" {
		t.Errorf("RepliedToID = %q, want empty", m.RepliedToID)
	}
	if m.PushName != "Budi" {
		t.Errorf("PushName = %q", m.PushName)
	}
}

func TestFromEvent_ExtendedTextWithReply(t *testing.T) {
	evt := &events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:   types.JID{User: "62811", Server: "s.whatsapp.net"},
				Sender: types.JID{User: "62811", Server: "s.whatsapp.net"},
			},
			ID: "X",
		},
		Message: &waE2E.Message{
			ExtendedTextMessage: &waE2E.ExtendedTextMessage{
				Text: stringPtr(".email"),
				ContextInfo: &waE2E.ContextInfo{
					StanzaID: stringPtr("REPLIED_ID"),
				},
			},
		},
	}

	m := fromEvent(evt)
	if m == nil {
		t.Fatal("expected message, got nil")
	}
	if m.Body != ".email" {
		t.Errorf("Body = %q", m.Body)
	}
	if m.RepliedToID != "REPLIED_ID" {
		t.Errorf("RepliedToID = %q", m.RepliedToID)
	}
}

func TestFromEvent_DocumentWithCaption(t *testing.T) {
	evt := &events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:   types.JID{User: "62811", Server: "s.whatsapp.net"},
				Sender: types.JID{User: "62811", Server: "s.whatsapp.net"},
			},
			ID: "Y",
		},
		Message: &waE2E.Message{
			DocumentMessage: &waE2E.DocumentMessage{
				Caption: stringPtr("invoice"),
			},
		},
	}

	m := fromEvent(evt)
	if m == nil {
		t.Fatal("expected message, got nil")
	}
	if m.MediaKind != "document" {
		t.Errorf("MediaKind = %q, want document", m.MediaKind)
	}
	if m.Body != "invoice" {
		t.Errorf("Body = %q, want caption", m.Body)
	}
	if m.IsTextOnly() {
		t.Error("IsTextOnly() should be false for document")
	}
}

func TestFromEvent_StickerNoBody(t *testing.T) {
	evt := &events.Message{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:   types.JID{User: "62811", Server: "s.whatsapp.net"},
				Sender: types.JID{User: "62811", Server: "s.whatsapp.net"},
			},
			ID: "Z",
		},
		Message: &waE2E.Message{
			StickerMessage: &waE2E.StickerMessage{},
		},
	}

	m := fromEvent(evt)
	if m == nil {
		t.Fatal("expected message even without body when media present")
	}
	if m.MediaKind != "sticker" {
		t.Errorf("MediaKind = %q", m.MediaKind)
	}
}

func TestFromEvent_EmptyMessageReturnsNil(t *testing.T) {
	evt := &events.Message{
		Info:    types.MessageInfo{},
		Message: &waE2E.Message{},
	}
	if m := fromEvent(evt); m != nil {
		t.Errorf("expected nil for empty message, got %+v", m)
	}
}

func TestFromEvent_NilGuards(t *testing.T) {
	if fromEvent(nil) != nil {
		t.Error("nil event should return nil")
	}
	if fromEvent(&events.Message{Message: nil}) != nil {
		t.Error("event with nil Message should return nil")
	}
}

func TestDetectMime(t *testing.T) {
	cases := map[string]string{
		"laporan.pdf":  "application/pdf",
		"data.csv":     "text/csv",
		"foto.jpg":     "image/jpeg",
		"unknown":      "application/octet-stream",
		"data.unknown": "application/octet-stream",
	}
	for in, wantPrefix := range cases {
		got := detectMime(in, nil)
		// Pakai HasPrefix karena beberapa mime type Go include "; charset=utf-8".
		if !strings.HasPrefix(got, wantPrefix) {
			t.Errorf("detectMime(%q) = %q, want prefix %q", in, got, wantPrefix)
		}
	}
}
