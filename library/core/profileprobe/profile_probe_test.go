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

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestBoundedTransferValidation(t *testing.T) {
	p := NewProfileProbe(&V2RayInstance{})
	defer p.Close()
	p.client.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Header.Get("Accept-Encoding") != "identity" {
			t.Fatal("compressed sample")
		}
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader("short"))}, nil
	})
	if _, err := p.Download(); err == nil {
		t.Fatal("truncated sample reported as a speed")
	}
	p.client.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		n, err := io.Copy(io.Discard, r.Body)
		if err != nil || n != probeUploadBytes || r.ContentLength != probeUploadBytes {
			t.Fatalf("upload payload: %d bytes, length %d, error %v", n, r.ContentLength, err)
		}
		return &http.Response{StatusCode: 200, Body: io.NopCloser(strings.NewReader("ok"))}, nil
	})
	if rate, err := p.Upload(); err != nil || rate <= 0 {
		t.Fatalf("upload: %v, %v", rate, err)
	}
}

func TestDeadlineDuringBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		w.(http.Flusher).Flush()
		<-r.Context().Done()
	}))
	defer server.Close()
	p := NewProfileProbe(&V2RayInstance{dial: (&net.Dialer{}).DialContext})
	defer p.Close()
	if p.client.Timeout != 20*time.Second {
		t.Fatal("missing production deadline")
	}
	p.client.Timeout = 50 * time.Millisecond
	resp, err := p.request("GET", server.URL, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if _, err = io.ReadAll(resp.Body); err == nil {
		t.Fatal("body read exceeded deadline")
	}
}
