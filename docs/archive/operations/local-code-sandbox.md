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

This local service exercises real compilation through `/api/v1/code/runs`.
Successful local execution does not establish the cause of the production 504:
the production provider and address have not yet been inspected.

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
unpaused in a finally block. Production returned a different, plain-text 504,
so matching its root cause still requires production configuration and logs.
