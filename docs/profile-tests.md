# Profile country and speed tests

Use **Connection test → URL test** to check the current list's latency and exit
country, or **Speed test** to measure download/upload rates for available profiles.
Long-press a profile for the same actions. Speed testing is enabled after a
successful URL check. Progress and results appear directly in the main cards;
batch progress appears in the toolbar. **Cancel tests** in the list menu stops
the batch. No test window opens. The speed-test menu identifies the 10 MiB budget.
URL tests preserve the last speed sample, hidden while a profile is unavailable.
Clear results cancels the batch and removes latency and diagnostic data.

Long press opens the action menu for every profile. Manual groups retain drag
reordering through a handle; subscription groups do not show drag handles.

Tests launch an isolated `forTest` core instance per profile and use its native
outbound dispatcher, underlying-network resolver and system-TUN protection path.
No new local listener is added and failed HTTP requests do not fall back to direct
HTTP. Existing protocol/plugin plumbing is retained. The temporary full-custom
configuration has production inbounds, API and metrics removed to avoid opening
listeners or colliding with the active service. Browser forwarding is unsupported
and is not marked as an unavailable server. Chains/balancers retain configured
behavior; a balancer result describes the exit used for that test. Custom routing
can intentionally choose another exit for the endpoint.

URL checks use the configured test URL and preserve HEAD/204 semantics. Their
country lookup uses Cloudflare's HTTPS trace endpoint to obtain the observed
public IP and country. This is egress geolocation, not the server's physical
location. Country failure does not turn a successful URL check into a failed
latency result. Bulk URL checks use six workers, as before.

Speed tests use Cloudflare, receive 8 MiB and send a 2 MiB random body per profile.
The menu identifies the transfer budget. Speed tests run one profile
at a time to avoid competing with one another. URL requests have a five-second
deadline; country and transfer requests have a 20-second deadline. URL failures
stop further requests for that profile. After a successful URL check, the other
phases retain their successful results when a later phase fails. Cancelling,
clearing results, editing profiles, or destroying the fragment's view cancels
native requests. Card state belongs to its run, so late cancelled workers cannot
clear newer progress or restore cleared data. Download timing covers the body;
upload timing includes acknowledgement. Rates are decimal Mbps and are bounded sample
estimates, not line-capacity benchmarks. Tests are manual; no scheduled tests run.

HTTP dial contexts are short-lived; native outbound sessions have a separate
lifetime which ends when the connection closes or the probe is cancelled. This
prevents net/http from closing the native pipe after connection establishment.

Results live in private preferences, outside Room entities and exported profiles.
Configuration/topology changes invalidate results. Epochs prevent in-flight tests
from restoring cleared results. Latency updates write only existing test fields
and leave profile configuration and traffic untouched. Saved speed samples may
become stale when the network changes.

Native verification:

```sh
cd library/core
go test -race profileprobe/profile_probe.go profileprobe/profile_probe_test.go
cd ../..
./library/core/build.sh
./gradlew :app:testOssDebugUnitTest :app:compileOssDebugKotlin
```

Both core build scripts copy the probe sources into the temporary remote module
before binding and publish the AAR into `app/libs`. If Kotlin reports missing
`ProfileProbe`/`urlTest` bindings, rebuild in the affected checkout with
`./run lib core` or `./library/core/build.sh`. A rebuilt AAR in another worktree
does not update this checkout. Gradle checks the local bindings before compilation.
