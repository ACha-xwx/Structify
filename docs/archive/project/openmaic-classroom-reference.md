# OpenMAIC classroom reference

The existing reference checkout is `G:/Code/OpenMAIC` (package version 1.1.1).
Open `Structify-OpenMAIC.code-workspace` in an editor supporting multi-root
workspaces to browse both repositories. Each folder retains its own Git
repository; OpenMAIC is not copied into Structify's application or dependency
tree.

## Source walkthrough

1. `../OpenMAIC/app/classroom/[id]/page.tsx` mounts
   `components/classroom/ClassroomSurface.tsx`, which loads the course and
   renders `components/stage.tsx`.
2. `components/stage/scene-renderer.tsx` dispatches slide, quiz, interactive,
   and PBL scenes. These scene types share the classroom shell.
3. `lib/server/classroom-generation.ts` calls outline generation, scene
   content generation, and scene action generation. Actions provide teaching
   behavior in addition to the scene's visible content.
4. `lib/playback/engine.ts` manages idle, playing, paused, and live states,
   lecture/discussion transitions, and playback position.
5. `lib/orchestration/director-graph.ts` implements one director-to-agent
   cycle per request. The client serializes requests for a multi-agent
   discussion. Single-agent dispatch uses code logic; multi-agent dispatch
   can use a model. Results stream through SSE.

This is a code-based walkthrough, not proof that configured models or all
media services work on this machine.

## Local runtime

The configured reference runs at `http://127.0.0.1:3030`. From Structify run
`./scripts/start-openmaic-reference.ps1` to start it again with Docker Desktop
running. The script does not start a second server on an occupied port.

- Compose project: `openmaic-structify-demo`; PostgreSQL listens only on
  `127.0.0.1:55432`, with a dedicated persistent Docker volume.
- The ignored `G:/Code/OpenMAIC/.env.local` contains a generated database
  password, database URL, fixed local owner identity, and model selection.
  Model credentials are inherited from `DEEPSEEK_API_KEY`, not written there.
- Next listens only on loopback. Single-user identity keeps the same example
  course visible across browsers in this local reference installation.
- Docker initially failed on an inaccessible runtime socket. The transient
  `AppData/Local/Docker/run` directory was renamed as a backup and recreated;
  no Docker volumes, images, containers, or user databases were reset.

Open `/classroom/structify-stack-reference-demo` for the manually authored
stack example: two teaching slides and one multiple-choice exercise. This
sample is explicitly labeled and is not evidence of model generation quality.
Press **T** to open the question composer. A real `/api/chat` smoke request
using `deepseek:deepseek-flash` returned HTTP 200 and streamed a correct short
answer about why the last pushed value is popped first. This was one live
answer request, separate from the synthetic browser fixture checks. Optional
image, ASR, and paid TTS providers were not configured or verified.

## Relevance to Structify

Compare the shared classroom shell, content-scoped discussion, playback
transitions and explicit teaching actions. Keep Structify's reviewed-outline,
fragment publication, permissions, execution budgets, lease fencing and quota
accounting boundaries. OpenMAIC's frontend and persistence stack are separate
systems; the multi-root workspace does not integrate either at runtime.
