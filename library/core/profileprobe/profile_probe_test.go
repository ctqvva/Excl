package libexclavecore

import (
	"context"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// Standalone transport harness: go test profileprobe/profile_probe.go profileprobe/profile_probe_test.go
type V2RayInstance struct {
	dial func(context.Context, string, string) (net.Conn, error)
}

func probeDial(i *V2RayInstance, ctx context.Context, network, address string) (net.Conn, error) {
	return i.dial(ctx, network, address)
}

func TestCountryValidation(t *testing.T) {
	for _, trace := range []string{"ip=1.2.3.4\nloc=de\n", "ip=2001:db8::1\nloc=JP\n"} {
		if _, err := parseProfileCountry(trace); err != nil {
			t.Fatal(err)
		}
	}
	for _, trace := range []string{"", "ip=bad\nloc=DE", "ip=1.2.3.4\nloc=XX", "ip=1.2.3.4\nloc=1A"} {
		if _, err := parseProfileCountry(trace); err == nil {
			t.Fatalf("accepted %q", trace)
		}
	}
	if profileBitRate(125000, time.Second) != 1000000 {
		t.Fatal("bit rate units")
	}
}

func TestNoDirectFallback(t *testing.T) {
	want := errors.New("profile dial failed")
	p := NewProfileProbe(&V2RayInstance{dial: func(context.Context, string, string) (net.Conn, error) { return nil, want }})
	defer p.Close()
	if _, err := p.Country(); !errors.Is(err, want) {
		t.Fatalf("%v", err)
	}
}

func TestCancelDuringBody(t *testing.T) {
	started := make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		w.(http.Flusher).Flush()
		close(started)
		<-r.Context().Done()
	}))
	defer server.Close()
	p := NewProfileProbe(&V2RayInstance{dial: (&net.Dialer{}).DialContext})
	defer p.Close()
	done := make(chan error, 1)
	go func() {
		resp, err := p.request("GET", server.URL, nil)
		if err == nil {
			defer resp.Body.Close()
			_, err = io.ReadAll(resp.Body)
		}
		done <- err
	}()
	<-started
	p.Close()
	select {
	case err := <-done:
		if err == nil {
			t.Fatal("cancelled request succeeded")
		}
	case <-time.After(time.Second):
		t.Fatal("cancel did not interrupt body read")
	}
}

func TestHTTPFailuresAndRedirects(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/redirect" {
			http.Redirect(w, r, "/ok", 302)
			return
		}
		w.WriteHeader(429)
	}))
	defer server.Close()
	p := NewProfileProbe(&V2RayInstance{dial: (&net.Dialer{}).DialContext})
	defer p.Close()
	for _, path := range []string{"/redirect", "/error"} {
		if _, err := p.request("GET", server.URL+path, strings.NewReader("")); err == nil {
			t.Fatal("accepted HTTP failure")
		}
	}
}
