package libexclavecore

import (
	"bytes"
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"
)

const probeDownloadBytes = 8 * 1024 * 1024
const probeUploadBytes = 2 * 1024 * 1024

// ProfileProbe uses the instance's dispatcher directly. It never opens a listener
// and never falls back to the device's HTTP transport.
type ProfileProbe struct {
	ctx       context.Context
	cancel    context.CancelFunc
	client    *http.Client
	transport *http.Transport
}

type ProfileCountry struct {
	IP          string
	CountryCode string
}

func NewProfileProbe(instance *V2RayInstance) *ProfileProbe {
	ctxRoot, cancel := context.WithCancel(context.Background())
	transport := &http.Transport{
		ForceAttemptHTTP2:   true,
		DisableCompression:  true,
		DisableKeepAlives:   true,
		TLSHandshakeTimeout: 10 * time.Second,
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			// net/http cancels its dial context after handing off the connection.
			// Core dispatch uses the context for the entire outbound session.
			session, stop := context.WithCancel(ctxRoot)
			stopDial := context.AfterFunc(ctx, stop)
			conn, err := probeDial(instance, session, network, address)
			if !stopDial() || session.Err() != nil {
				stop()
				if conn != nil {
					conn.Close()
				}
				if ctx.Err() != nil {
					return nil, ctx.Err()
				}
				return nil, session.Err()
			}
			if err != nil {
				stop()
				return nil, err
			}
			return &profileProbeConn{Conn: conn, cancel: stop}, nil
		},
	}
	return &ProfileProbe{
		ctx: ctxRoot, cancel: cancel, transport: transport,
		client: &http.Client{
			Transport:     transport,
			Timeout:       20 * time.Second,
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
	}
}

type profileProbeConn struct {
	net.Conn
	cancel context.CancelFunc
}

func (c *profileProbeConn) Close() error {
	c.cancel()
	return c.Conn.Close()
}

// UrlTest preserves the existing HEAD-test semantics, including 204 responses.
func (p *ProfileProbe) UrlTest(link string, timeout int32) (int32, error) {
	ctx, cancel := context.WithTimeout(p.ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, "HEAD", link, nil)
	if err != nil {
		return 0, err
	}
	start := time.Now()
	resp, err := p.client.Do(req)
	if err != nil {
		return 0, err
	}
	resp.Body.Close()
	return int32(time.Since(start).Milliseconds()), nil
}

// Close is safe during a request, including while downloading/uploading.
func (p *ProfileProbe) Close() {
	p.cancel()
	p.transport.CloseIdleConnections()
}

func (p *ProfileProbe) request(method, url string, body io.Reader) (*http.Response, error) {
	req, err := http.NewRequestWithContext(p.ctx, method, url, body)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept-Encoding", "identity")
	req.Header.Set("Cache-Control", "no-cache")
	resp, err := p.client.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		resp.Body.Close()
		return nil, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	return resp, nil
}

func (p *ProfileProbe) Country() (*ProfileCountry, error) {
	resp, err := p.request("GET", "https://speed.cloudflare.com/cdn-cgi/trace", nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(io.LimitReader(resp.Body, 65537))
	if err != nil {
		return nil, err
	}
	if len(data) > 65536 {
		return nil, errors.New("country response too large")
	}
	return parseProfileCountry(string(data))
}

func parseProfileCountry(trace string) (*ProfileCountry, error) {
	result := new(ProfileCountry)
	for _, line := range strings.Split(trace, "\n") {
		key, value, _ := strings.Cut(strings.TrimSpace(line), "=")
		switch key {
		case "ip":
			result.IP = value
		case "loc":
			result.CountryCode = strings.ToUpper(value)
		}
	}
	if net.ParseIP(result.IP) == nil {
		return nil, errors.New("invalid exit IP")
	}
	code := result.CountryCode
	if len(code) != 2 || code[0] < 'A' || code[0] > 'Z' || code[1] < 'A' || code[1] > 'Z' || code == "XX" {
		return nil, errors.New("country unavailable")
	}
	return result, nil
}

// Download returns bits per second, after receiving the complete bounded body.
func (p *ProfileProbe) Download() (float64, error) {
	resp, err := p.request("GET", fmt.Sprintf("https://speed.cloudflare.com/__down?bytes=%d&nonce=%d", probeDownloadBytes, time.Now().UnixNano()), nil)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	start := time.Now()
	n, err := io.Copy(io.Discard, io.LimitReader(resp.Body, probeDownloadBytes+1))
	if err != nil {
		return 0, err
	}
	if n != probeDownloadBytes {
		return 0, errors.New("incomplete download")
	}
	return profileBitRate(n, time.Since(start)), nil
}

// Upload includes request/response latency and requires successful acknowledgement.
func (p *ProfileProbe) Upload() (float64, error) {
	data := make([]byte, probeUploadBytes)
	if _, err := rand.Read(data); err != nil {
		return 0, err
	}
	start := time.Now()
	resp, err := p.request("POST", "https://speed.cloudflare.com/__up", bytes.NewReader(data))
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	_, err = io.Copy(io.Discard, io.LimitReader(resp.Body, 65536))
	if err != nil {
		return 0, err
	}
	return profileBitRate(int64(len(data)), time.Since(start)), nil
}

func profileBitRate(n int64, elapsed time.Duration) float64 {
	if elapsed <= 0 || n <= 0 {
		return 0
	}
	return float64(n) * 8 / elapsed.Seconds()
}
