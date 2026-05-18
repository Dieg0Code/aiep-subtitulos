package main

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

func newTestServer(t *testing.T) (string, *Hub) {
	t.Helper()
	hub := NewHub()
	mux := http.NewServeMux()
	mux.HandleFunc("GET /ws/host", hub.HandleHost)
	mux.HandleFunc("GET /ws/guest", hub.HandleGuest)
	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)
	return srv.URL, hub
}

func wsURL(httpURL, path string) string {
	return "ws" + strings.TrimPrefix(httpURL, "http") + path
}

type sessionMsg struct {
	Type string `json:"type"`
	ID   string `json:"id"`
}

type statusMsg struct {
	Type  string `json:"type"`
	State string `json:"state"`
}

func connectHost(t *testing.T, base string) (*websocket.Conn, string) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, wsURL(base, "/ws/host"), nil)
	if err != nil {
		t.Fatalf("dial host: %v", err)
	}
	conn.SetReadLimit(maxReadBytes)
	_, data, err := conn.Read(ctx)
	if err != nil {
		t.Fatalf("read host session: %v", err)
	}
	var m sessionMsg
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("unmarshal session: %v", err)
	}
	if m.Type != "session" || len(m.ID) != idLength {
		t.Fatalf("bad session msg: %+v", m)
	}
	return conn, m.ID
}

func connectGuest(t *testing.T, base, id string) *websocket.Conn {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	conn, _, err := websocket.Dial(ctx, wsURL(base, "/ws/guest?s="+id), nil)
	if err != nil {
		t.Fatalf("dial guest: %v", err)
	}
	conn.SetReadLimit(maxReadBytes)
	return conn
}

func TestGenerateID_LengthAlphabet(t *testing.T) {
	allowed := map[byte]bool{}
	for i := 0; i < len(idAlphabet); i++ {
		allowed[idAlphabet[i]] = true
	}
	for i := 0; i < 1000; i++ {
		id := generateID()
		if len(id) != idLength {
			t.Fatalf("len(id) = %d, want %d", len(id), idLength)
		}
		for j := 0; j < len(id); j++ {
			if !allowed[id[j]] {
				t.Fatalf("invalid char %q in %q", id[j], id)
			}
		}
	}
}

func TestGenerateID_Uniqueness(t *testing.T) {
	seen := make(map[string]struct{}, 10000)
	for i := 0; i < 10000; i++ {
		id := generateID()
		if _, dup := seen[id]; dup {
			t.Fatalf("duplicate at iter %d: %s", i, id)
		}
		seen[id] = struct{}{}
	}
}

func TestHub_HostReceivesSessionID(t *testing.T) {
	base, _ := newTestServer(t)
	conn, id := connectHost(t, base)
	defer conn.Close(websocket.StatusNormalClosure, "")
	if len(id) != idLength {
		t.Errorf("id length = %d, want %d", len(id), idLength)
	}
}

func TestHub_GuestStatusOnConnect(t *testing.T) {
	base, _ := newTestServer(t)
	host, id := connectHost(t, base)
	defer host.Close(websocket.StatusNormalClosure, "")

	guest := connectGuest(t, base, id)
	defer guest.Close(websocket.StatusNormalClosure, "")

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	_, data, err := host.Read(ctx)
	if err != nil {
		t.Fatalf("host read: %v", err)
	}
	var s statusMsg
	if err := json.Unmarshal(data, &s); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if s.Type != "status" || s.State != "phone-connected" {
		t.Errorf("got %+v, want phone-connected", s)
	}
}

func TestHub_BinaryAndTextRelay(t *testing.T) {
	base, _ := newTestServer(t)
	host, id := connectHost(t, base)
	defer host.Close(websocket.StatusNormalClosure, "")
	guest := connectGuest(t, base, id)
	defer guest.Close(websocket.StatusNormalClosure, "")

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	// Drain phone-connected
	if _, _, err := host.Read(ctx); err != nil {
		t.Fatalf("drain status: %v", err)
	}

	payload := []byte{0xCA, 0xFE, 0xBA, 0xBE}
	if err := guest.Write(ctx, websocket.MessageBinary, payload); err != nil {
		t.Fatalf("guest write binary: %v", err)
	}
	mt, data, err := host.Read(ctx)
	if err != nil {
		t.Fatalf("host read binary: %v", err)
	}
	if mt != websocket.MessageBinary || !bytes.Equal(data, payload) {
		t.Fatalf("binary mismatch: type=%v len=%d", mt, len(data))
	}

	txt := []byte(`{"kind":"caption","text":"hola","isFinal":true}`)
	if err := guest.Write(ctx, websocket.MessageText, txt); err != nil {
		t.Fatalf("guest write text: %v", err)
	}
	mt, data, err = host.Read(ctx)
	if err != nil {
		t.Fatalf("host read text: %v", err)
	}
	if mt != websocket.MessageText || !bytes.Equal(data, txt) {
		t.Fatalf("text mismatch: type=%v body=%q", mt, data)
	}
}

func TestHub_GuestStatusOnDisconnect(t *testing.T) {
	base, _ := newTestServer(t)
	host, id := connectHost(t, base)
	defer host.Close(websocket.StatusNormalClosure, "")

	guest := connectGuest(t, base, id)

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	if _, _, err := host.Read(ctx); err != nil {
		t.Fatalf("drain connected: %v", err)
	}

	if err := guest.Close(websocket.StatusNormalClosure, ""); err != nil {
		t.Fatalf("guest close: %v", err)
	}

	_, data, err := host.Read(ctx)
	if err != nil {
		t.Fatalf("host read disconnected: %v", err)
	}
	var s statusMsg
	if err := json.Unmarshal(data, &s); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if s.Type != "status" || s.State != "phone-disconnected" {
		t.Errorf("got %+v, want phone-disconnected", s)
	}
}

func TestHub_SecondGuestRejected(t *testing.T) {
	base, _ := newTestServer(t)
	host, id := connectHost(t, base)
	defer host.Close(websocket.StatusNormalClosure, "")

	guest1 := connectGuest(t, base, id)
	defer guest1.Close(websocket.StatusNormalClosure, "")

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	// Drain phone-connected for guest1
	if _, _, err := host.Read(ctx); err != nil {
		t.Fatalf("drain connected: %v", err)
	}

	guest2 := connectGuest(t, base, id)
	defer guest2.Close(websocket.StatusInternalError, "")

	_, _, err := guest2.Read(ctx)
	if err == nil {
		t.Fatalf("expected guest2 read to fail")
	}
	if cs := websocket.CloseStatus(err); cs != websocket.StatusPolicyViolation {
		t.Errorf("close status = %v, want PolicyViolation (1008)", cs)
	}

	// Host should not have received a duplicate phone-connected.
	readCtx, readCancel := context.WithTimeout(context.Background(), 250*time.Millisecond)
	defer readCancel()
	_, _, err = host.Read(readCtx)
	if err == nil {
		t.Fatalf("host got unexpected message after rejected guest")
	}
}

func TestHub_GuestMissingSession(t *testing.T) {
	base, _ := newTestServer(t)
	resp, err := http.Get(base + "/ws/guest?s=NOPE12")
	if err != nil {
		t.Fatalf("http get: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp.StatusCode)
	}
}

func TestHub_GuestEmptySessionID(t *testing.T) {
	base, _ := newTestServer(t)
	resp, err := http.Get(base + "/ws/guest")
	if err != nil {
		t.Fatalf("http get: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", resp.StatusCode)
	}
}

func TestHub_HostDisconnectKillsGuest(t *testing.T) {
	base, hub := newTestServer(t)
	host, id := connectHost(t, base)
	guest := connectGuest(t, base, id)
	defer guest.Close(websocket.StatusInternalError, "")

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if _, _, err := host.Read(ctx); err != nil {
		t.Fatalf("drain connected: %v", err)
	}

	_ = host.Close(websocket.StatusNormalClosure, "")

	_, _, err := guest.Read(ctx)
	if err == nil {
		t.Fatalf("expected guest read to fail after host close")
	}
	if cs := websocket.CloseStatus(err); cs != websocket.StatusGoingAway {
		t.Errorf("close status = %v, want GoingAway (1001)", cs)
	}

	deadline := time.Now().Add(1 * time.Second)
	for time.Now().Before(deadline) {
		if hub.lookup(id) == nil {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Errorf("session %s still in hub after host disconnect", id)
}
