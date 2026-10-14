# Profile country and speed tests

Open **Connection test → Country check / Speed test** to test the current group,
**Test selected profile** to test the selected profile, or use an individual
profile's share menu. Tap saved results on a profile card to reopen its tester.
The sheet uses a dedicated Material 3 Expressive theme, even when the host uses
an older app theme. The Android 5/6 legacy flavor uses a Material 3 fallback
because its compatible Material library does not include Expressive themes.
Press **Run test** to start; **Stop** cancels native requests.
Closing the sheet or destroying its view also cancels the run.

Tests launch an isolated `forTest` core instance for each profile. Requests dial
through that instance's dispatcher, with the existing underlying-network resolver
and system-TUN protection path. Probes add no HTTP/SOCKS/IPC listener and do not fall back to direct HTTP on
failure. Existing protocol/plugin plumbing is retained. Full custom configurations
have inbounds, API and metrics removed from the temporary test configuration
to avoid opening production listeners or colliding with the active service. Browser-forwarded transports are skipped.
Chains, balancers and custom configurations retain their configured outbound
behavior; a balancer's result describes the exit used for that test, not every
member. Custom routing can intentionally choose a different exit for the endpoint.

Cloudflare's HTTPS trace endpoint supplies the observed public IP and country
code. This is egress geolocation, not the configured server's physical location.
Speed tests then receive an 8 MiB response and send a 2 MiB random body to
`speed.cloudflare.com`. Tests are sequential to avoid competing with one another.
Each request has a 20-second deadline. Redirects and non-200 responses fail.
Download timing covers the response body; upload timing includes acknowledgement.
Rates are decimal Mbps. These bounded samples are estimates, not a line-capacity
benchmark. Each phase reports its own failure without blocking the next phase. Completed
measurements are retained if a later phase fails.

The provider sees the tested exit IP. Tests are manual and the sheet shows the
provider and transfer budget before starting. No scheduled/background tests run.
Results (country, public IP, rates, timestamp and a hash of the profile bean) are
stored in private preferences, outside Room entities and exported profiles.
Profile edits and front/landing proxy changes invalidate saved results; clearing
results also prevents an in-flight test from restoring them. A newly started test
replaces the previous sample as its phases complete. Network changes can make
saved results stale; their timestamp remains visible.

Native verification:

```sh
cd library/core
go test -race profileprobe/profile_probe.go profileprobe/profile_probe_test.go
./build.sh
cd ../..
./gradlew :app:compileOssDebugKotlin
```

Both core build scripts copy the probe sources into the temporary remote module
before binding. Do not distribute an APK with an old core AAR: it must expose
`NewProfileProbe`, `ProfileProbe`, and `ProfileCountry`.
