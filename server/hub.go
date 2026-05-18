package main

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
)

var errClosed = errors.New("session closed")

const (
	idAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
	idLength   = 6
	// Max acceptable WS frame from phone. Audio chunks are ~10KB typically;
	// give headroom so a longer chunk on slow upload doesn't kill the session.
	maxReadBytes = 1 << 20 // 1 MiB
)

type Session struct {
	ID          string
	host        *websocket.Conn
	guest       *websocket.Conn
	mu          sync.Mutex
	hostWriteMu sync.Mutex
	closed      bool
}

func (s *Session) Host() *websocket.Conn {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.host
}

// WriteHost serializes writes to the host conn. The relay can receive writes
// to the host from both the host's own read goroutine (initial session msg)
// and the guest's goroutine (pairing status + relayed frames). coder/websocket
// does not support concurrent Writes on a single conn, so we lock here.
func (s *Session) WriteHost(ctx context.Context, msgType websocket.MessageType, data []byte) error {
	s.hostWriteMu.Lock()
	defer s.hostWriteMu.Unlock()
	host := s.Host()
	if host == nil {
		return errClosed
	}
	return host.Write(ctx, msgType, data)
}

func (s *Session) Guest() *websocket.Conn {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.guest
}

func (s *Session) AttachGuest(c *websocket.Conn) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.guest != nil || s.closed {
		return false
	}
	s.guest = c
	return true
}

func (s *Session) DetachGuest(c *websocket.Conn) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.guest == c {
		s.guest = nil
	}
}

type Hub struct {
	mu       sync.RWMutex
	sessions map[string]*Session
}

func NewHub() *Hub {
	return &Hub{sessions: make(map[string]*Session)}
}

func generateID() string {
	buf := make([]byte, idLength)
	if _, err := rand.Read(buf); err != nil {
		// rand.Read on Linux is effectively infallible; if it errors we're in
		// real trouble. Panic surfaces it instead of issuing predictable IDs.
		panic(err)
	}
	out := make([]byte, idLength)
	for i, b := range buf {
		out[i] = idAlphabet[int(b)%len(idAlphabet)]
	}
	return string(out)
}

func (h *Hub) create(host *websocket.Conn) *Session {
	h.mu.Lock()
	defer h.mu.Unlock()
	for {
		id := generateID()
		if _, exists := h.sessions[id]; !exists {
			s := &Session{ID: id, host: host}
			h.sessions[id] = s
			return s
		}
	}
}

func (h *Hub) lookup(id string) *Session {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.sessions[id]
}

func (h *Hub) remove(id string) {
	h.mu.Lock()
	if s, ok := h.sessions[id]; ok {
		s.mu.Lock()
		s.closed = true
		s.mu.Unlock()
		delete(h.sessions, id)
	}
	h.mu.Unlock()
}

func acceptOptions() *websocket.AcceptOptions {
	// MVP: accept any origin. The Tauri webview presents an opaque origin
	// (`tauri://localhost` or similar) and the mobile page is served from
	// this same host, so origin pinning has limited value here.
	return &websocket.AcceptOptions{InsecureSkipVerify: true}
}

func (h *Hub) HandleHost(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, acceptOptions())
	if err != nil {
		slog.Warn("host accept failed", "err", err)
		return
	}
	conn.SetReadLimit(maxReadBytes)

	session := h.create(conn)
	slog.Info("host connected", "session", session.ID, "remote", r.RemoteAddr)

	defer func() {
		h.remove(session.ID)
		if g := session.Guest(); g != nil {
			_ = g.Close(websocket.StatusGoingAway, "host left")
		}
		_ = conn.Close(websocket.StatusNormalClosure, "")
		slog.Info("host disconnected", "session", session.ID)
	}()

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	if err := writeJSONToHost(ctx, session, map[string]string{"type": "session", "id": session.ID}); err != nil {
		slog.Warn("host write session failed", "err", err)
		return
	}

	// We don't expect the host to send much, but reading from it is how we
	// detect a close. Anything text-shaped we forward to the guest.
	for {
		msgType, data, err := conn.Read(ctx)
		if err != nil {
			return
		}
		if g := session.Guest(); g != nil {
			writeCtx, writeCancel := context.WithTimeout(ctx, 5*time.Second)
			err := g.Write(writeCtx, msgType, data)
			writeCancel()
			if err != nil {
				slog.Warn("guest write from host failed", "err", err, "session", session.ID)
			}
		}
	}
}

func (h *Hub) HandleGuest(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("s")
	if id == "" {
		http.Error(w, "missing session id", http.StatusBadRequest)
		return
	}
	session := h.lookup(id)
	if session == nil {
		http.Error(w, "session not found", http.StatusNotFound)
		return
	}

	conn, err := websocket.Accept(w, r, acceptOptions())
	if err != nil {
		slog.Warn("guest accept failed", "err", err)
		return
	}
	conn.SetReadLimit(maxReadBytes)

	if !session.AttachGuest(conn) {
		_ = conn.Close(websocket.StatusPolicyViolation, "session busy or closed")
		return
	}

	slog.Info("guest connected", "session", id, "remote", r.RemoteAddr)

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	defer func() {
		session.DetachGuest(conn)
		_ = conn.Close(websocket.StatusNormalClosure, "")
		_ = writeJSONToHost(context.Background(), session, map[string]string{
			"type": "status", "state": "phone-disconnected",
		})
		slog.Info("guest disconnected", "session", id)
	}()

	if session.Host() != nil {
		_ = writeJSONToHost(ctx, session, map[string]string{
			"type": "status", "state": "phone-connected",
		})
	}

	for {
		msgType, data, err := conn.Read(ctx)
		if err != nil {
			return
		}
		if session.Host() == nil {
			return
		}
		writeCtx, writeCancel := context.WithTimeout(ctx, 5*time.Second)
		err = session.WriteHost(writeCtx, msgType, data)
		writeCancel()
		if err != nil {
			slog.Warn("host write from guest failed", "err", err, "session", id)
			return
		}
	}
}

func writeJSONToHost(ctx context.Context, s *Session, payload any) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	writeCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	return s.WriteHost(writeCtx, websocket.MessageText, data)
}
