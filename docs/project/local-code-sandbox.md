# Local Docker code sandbox

The development Piston API listens at `http://127.0.0.1:2001/api/v2`.
The container is `structify-piston-local`; runtimes persist in the named volume
`structify-piston-local-data`. The unrelated PostgreSQL container is unchanged.

The container follows the privileged deployment required by upstream Piston.
Only the loopback interface is published; do not expose this package-management
API to the public network.

```powershell
docker compose -f deployment/docker-compose.sandbox-local.yml up -d
Invoke-RestMethod http://127.0.0.1:2001/api/v2/packages -Method Post -ContentType application/json -Body '{"language":"gcc","version":"10.2.0"}'
$env:PISTON_BASE_URL='http://127.0.0.1:2001/api/v2'
./scripts/start-studyroom-preview.ps1
```

Stop the existing Spring preview before restarting it: two processes must never
open the development H2 file together. Database sandbox configuration, if any,
takes precedence over this environment variable. `GET /api/v2/runtimes` shows
the runtimes whose installation has completed.

This local service exercises real compilation through `/api/v1/code/runs`, and it is
the way to work on the Piston path itself - the gateway's HTTP/1.1 handling, provider
failover, and any language the API container cannot compile. It is a development
machine's sandbox, not the shape production runs in; see the production section below,
which is where the production 504 was actually found and fixed.

## Verification on 2026-10-23

Docker Desktop restarted successfully. The downloaded GCC 10.2 runtime's `cc1`
crashed when invoked directly. For this local diagnostic volume only, its compile
script was backed up as `compile.original` and the C command switched to
`env -u LD_LIBRARY_PATH PATH=/usr/bin:/bin /usr/bin/gcc`, using the container's
Debian GCC 8.3. Runtime metadata still labels the package 10.2; this is not a
GCC 10.2 compatibility verification or a production runtime release.

The Java sandbox client previously received plain HTTP 400 from Piston while a
direct HTTP/1.1 request worked. Explicit HTTP/1.1 fixed the Spring-to-Piston path.
The gateway test checks that requests do not contain an h2c Upgrade header.

Through Vite `/api/v1/code/runs`, a C program reading 21 returned `result=42`;
invalid source returned `compile_error`; an infinite loop returned
`runtime_error` after 3.21 seconds. Pausing only the sandbox container produced
HTTP 504 with `COMPILER_UPSTREAM_TIMEOUT` after 15.14 seconds. The container was
unpaused in a finally block.

The plain-text 504 production returned was a different failure, and it is now
explained: see below.

## Production path: C no longer leaves the host (2026-10-24)

The production 504 was not a Piston problem. C was being posted to the public Judge0
instance over a slow public link, where a submission that compiles and runs in
milliseconds spent 5.5 to 5.9 seconds in the network and, when the public instance was
busy, outlived the 15 second gateway deadline and reached the learner as a 504.

C is now compiled and run inside the API container by
`backend/spring/src/main/java/com/feng/dsagent/compiler/LocalCodeRunner.java`, as a
child process beside the service, whenever `SANDBOX_ENABLED` is on. The image already
carried gcc 15.2.0 for the interactive console, so no new dependency was added. Python
still goes through the gateway, which remains the only reason to point
`PISTON_BASE_URL` anywhere.

Measured on production after the `v2.2.1` release, through `/api/v1/code/runs`:

| Program | Result | Server-side time |
| --- | --- | --- |
| Reads a number, prints Chinese | `链表长度=42` | 167 ms |
| Invalid source | `compile_error`, gcc's own words | 30 ms |
| Infinite loop | `runtime_error`, killed at the local deadline | 5.36 s |

Three things keep it honest rather than merely local. A program that outlives the
deadline is killed, not left spinning. Its work directory is deleted with it. And a
broken toolchain answers 503 instead of quietly falling back upstream, because that
fallback is the path this exists to remove.

The container keeps its 320 MiB limit. Four simultaneous builds measured 298 of
320 MiB with no OOM kill, because the cgroup reading includes reclaimable page cache
and the kernel gives cache back instead of killing anything; the runner also starts no
more compiles at once than the host has cores. The four containers already total the
896 MiB that the low-memory profile allows, so raising one of them would be refused by
preflight rather than help.
