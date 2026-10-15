import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");

function grepCount(file, regex) {
  try {
    const src = fs.readFileSync(path.join(ROOT, file), "utf8");
    const m = src.match(regex);
    return m ? m.length : 0;
  } catch { return 0; }
}
function fileExists(rel) { try { fs.accessSync(path.join(ROOT, rel)); return true; } catch { return false; } }
function countFiles(dir, ext) {
  try {
    return fs.readdirSync(path.join(ROOT, dir)).filter(f => !ext || f.endsWith(ext)).length;
  } catch { return 0; }
}

const lyricsProviders = fs.readdirSync(path.join(ROOT, "shared/src/jvmSharedMain/kotlin/com/music/konnekt/data/lyrics"))
  .filter(f => f.endsWith(".kt") && !["LyricLine.kt","LyricSections.kt","LyricAlignments.kt","LyricGaps.kt","LyricsQuery.kt","LyricsHttp.kt","LyricsRepository.kt","LyricsSource.kt","LyricsTranslation.kt","ProviderLyrics.kt","LrcWriter.kt","SyllableRuns.kt","TranslationLanguages.kt","TtmlLyrics.kt","EnhancedLrc.kt","BackgroundVocals.kt"].includes(f))
  .map(f => f.replace(".kt",""));

const playbackFiles = fs.readdirSync(path.join(ROOT, "app/src/main/java/com/music/konnekt/playback"))
  .filter(f => f.endsWith(".kt")).map(f => f.replace(".kt",""));

const facets = [
  {
    id: "backend",
    nav: "Serveur Listen Together",
    title: "Serveur Listen Together (Go)",
    headline: "11 routes · 13 frames · 15 contrôles",
    sub: "backend/ — Go 1.27 + gorilla/websocket",
    hero: { v: "11", l: "routes HTTP + WS", s: "REST + WebSocket + presence" },
    sections: [
      { kind: "tiles", tiles: [
        { v: "11", l: "routes", s: "7 REST + 2 presence + 1 invite + 1 WS" },
        { v: "13", l: "types de frames", s: "welcome/state/queue/members/control…" },
        { v: "15", l: "actions de contrôle", s: "play/pause/seek/queue/kick…" },
        { v: "6", l: "packages Go", s: "clock/codes/config/hub/party/presence" },
      ]},
      { kind: "tree", title: "Routes HTTP & WebSocket — 11 endpoints", sub: "backend/main.go → mux.HandleFunc",
        items: [
          { label: "GET /", badge: "info", children: [{ label: "/", badge: "GET" }, { label: "healthz", badge: "GET" }, { label: "api/time", badge: "GET" }] },
          { label: "api/parties", badge: "REST", children: [
            { label: "POST /api/parties", badge: "POST" },
            { label: "POST /api/parties/{code}/join", badge: "POST" },
            { label: "GET  /api/parties/{code}", badge: "GET" },
            { label: "GET  /api/parties/{code}/preview", badge: "GET" },
            { label: "POST /api/parties/{code}/leave", badge: "POST" },
          ]},
          { label: "api/presence", badge: "REST", children: [{ label: "POST /api/presence", badge: "POST" }, { label: "GET /api/stats/live", badge: "GET" }] },
          { label: "invite", badge: "page", children: [{ label: "GET /invite/{code}", badge: "GET" }] },
          { label: "ws/parties/{code}", badge: "WS", children: [{ label: "GET /ws/parties/{code}", badge: "WS" }] },
        ]
      },
      { kind: "groups", title: "Frames WebSocket par rôle", groups: [
        { title: "Serveur → Client", chips: ["welcome", "state", "queue", "members", "pong", "activity", "error", "bye"] },
        { title: "Client → Serveur", chips: ["ping", "control", "sync", "syncQueue", "report"] },
        { title: "Contrôles hostOnly (12)", chips: ["play", "pause", "seek", "setTrack", "setQueue", "queueAdd", "queueRemove", "queueClear", "queueMove", "next", "previous", "setAutoplay"] },
        { title: "Contrôles host-only exclusif", accent: true, chips: ["kick", "setMaxMembers", "setHostOnlyControl"] },
      ]},
      { kind: "loclist", title: "Fichiers serveur par taille", sub: "backend/",
        rows: [
          { label: "main.go", meta: "1269 loc · mux + handlers + invite HTML", bar: 1269 },
          { label: "party/party.go", meta: "925 loc · Party + PlaybackState + Store", bar: 925 },
          { label: "party/party_test.go", meta: "486 loc", bar: 486 },
          { label: "main_test.go", meta: "457 loc", bar: 457 },
          { label: "protocol/protocol.go", meta: "~120 loc · Frame/Control constants", bar: 120 },
          { label: "hub/hub.go", meta: "167 loc · SafeConn hub", bar: 167 },
          { label: "config/config.go", meta: "~80 loc · limites & env", bar: 80 },
          { label: "presence/presence.go", meta: "~150 loc · Tracker 10k installs", bar: 150 },
          { label: "clock/clock.go + codes/codes.go", meta: "~60 loc · NowMs + codes 6 chars", bar: 60 },
        ]
      },
    ]
  },
  {
    id: "pipeline",
    nav: "Sources & Paroles",
    title: "Sources musicales & Paroles synchronisées",
    headline: lyricsProviders.length + " fournisseurs de paroles · 4 sources audio · 3 clients InnerTube",
    sub: "shared/ + app/data — course parallèle & résolution de flux",
    hero: { v: String(lyricsProviders.length), l: "fournisseurs paroles", s: "race parallèle 2.5s" },
    sections: [
      { kind: "tiles", tiles: [
        { v: String(lyricsProviders.length), l: "providers paroles", s: "BiniLyrics → LrcLib → Musixmatch…" },
        { v: "4", l: "sources audio", s: "addon > module JS > JioSaavn > YouTube" },
        { v: "3", l: "clients InnerTube", s: "WEB / ANDROID / iOS + InnerTubeX" },
        { v: "15", l: "fichiers lyrics", s: "shared/data/lyrics/*.kt" },
      ]},
      { kind: "groups", title: "Fournisseurs de paroles — priorité de course", groups: [
        { title: "Tier 1 — ISRC identify (2.5s)", accent: true, chips: ["BiniLyrics"] },
        { title: "Tier 2 — LRC communautaire", chips: ["LrcRed", "LrcLib", "PaxSenix", "Megalobiz", "SimpMusicLyrics"] },
        { title: "Tier 3 — Premium / enrichi", chips: ["Musixmatch", "Genius", "KuGou", "YouTubeLyrics"] },
        { title: "Tier 4 — Fallback & utils", chips: ["BetterLyrics", "Unison", "LyricsPlus", "LrcWriter", "TtmlLyrics"] },
        { title: "Pipeline & orchestration", chips: ["LyricsRepository", "LyricsTranslation", "LyricLine", "SyllableRuns", "TranslationLanguages"] },
      ]},
      { kind: "groups", title: "Sources audio — ordre de résolution (SourceRegistry)", groups: [
        { title: "Priorité 1 — Addon externe", chips: ["AddonSource"] },
        { title: "Priorité 2 — Module JS (QuickJS/GraalVM)", chips: ["ModuleSource", "QuickJsExecutor"] },
        { title: "Priorité 3 — JioSaavn", chips: ["JioSaavnSource"] },
        { title: "Priorité 4 — YouTube (fallback)", accent: true, chips: ["YouTubeSource", "InnerTubeXResolver", "NewPipeExtractor"] },
        { title: "Résolution & cache", chips: ["SourceResolver", "StreamResolver", "StreamContainer", "StreamChoice", "AudioCache"] },
      ]},
      { kind: "loclist", title: "Couche Innertube — 5 fichiers jvmSharedMain", sub: "shared/src/jvmSharedMain/kotlin/.../data/innertube/",
        rows: [
          { label: "InnertubeParser.kt", meta: "1524 loc · parsers d'étagères", bar: 1524 },
          { label: "Innertube.kt", meta: "1262 loc · client WEB_REMIX + SAPISIDHASH", bar: 1262 },
          { label: "StreamResolver.kt", meta: "1100 loc · multi-client + cipher", bar: 1100 },
          { label: "InnerTubeXResolver.kt", meta: "~400 loc · InnerTubeX bridge", bar: 400 },
          { label: "PlayerClient.kt", meta: "~300 loc · catalogue WEB/Android/iOS", bar: 300 },
          { section: "potoken — BotGuard PoToken" },
          { label: "PoTokenGenerator.kt", meta: "WebView (Android) / JavaFX WebKit (desktop)", bar: 200, muted: true },
          { label: "potoken/ — 5 fichiers", meta: "PoTokenResult/Page/Exception/JavaScriptUtil", bar: 350, muted: true },
        ]
      },
    ]
  },
  {
    id: "playback",
    nav: "Lecture & Interface",
    title: "Lecture audio & Interface Compose",
    headline: "28 composants playback · 18 écrans · 11 fichiers natifs C++",
    sub: "app/playback + sharedUi + desktopApp + native/analyzer",
    hero: { v: "365", l: "fichiers UI+playback", s: "Compose + Media3 + FFmpeg" },
    sections: [
      { kind: "tiles", tiles: [
        { v: "28", l: "fichiers playback", s: "processors + cache + queue + party" },
        { v: "18", l: "écrans Compose", s: "4 sharedUi + 14 Android" },
        { v: "23", l: "composants player", s: "NowPlaying/Controls/Lyrics/Queue…" },
        { v: "6", l: "fichiers C++ analyse", s: "audio/tempo/mel/vocal/resampler" },
      ]},
      { kind: "tree", title: "Arborescence des écrans — 18 destinations", sub: "sharedUi/screens + app/ui/screens + desktopApp",
        items: [
          { label: "sharedUi (cross-platform)", badge: "KMP", children: [
            { label: "HomeScreen", badge: "page" },
            { label: "SearchScreen", badge: "page" },
            { label: "LibraryScreen", badge: "page" },
            { label: "ExploreScreen", badge: "page" },
          ]},
          { label: "app — Android", badge: "android", children: [
            { label: "DetailScreen", badge: "page" },
            { label: "HistoryScreen", badge: "page" },
            { label: "LocalMusicScreen", badge: "page" },
            { label: "SpotifyLibraryScreen", badge: "page" },
            { label: "ListenTogetherScreen", badge: "page" },
            { label: "SourcesScreen", badge: "page" },
            { label: "SettingsSheet", badge: "sheet" },
            { label: "EqualizerScreen", badge: "page" },
            { label: "DiscordScreen", badge: "page" },
            { label: "AccountAndScrobblingScreen", badge: "page" },
          ]},
          { label: "desktopApp — Desktop", badge: "desktop", children: [
            { label: "DesktopApp (root)", badge: "page" },
            { label: "DesktopPlayerHost/Sheet", badge: "page" },
            { label: "DesktopPlaylistUi", badge: "page" },
            { label: "DesktopEqualizerUi", badge: "page" },
            { label: "DesktopDownloadManagerUi", badge: "page" },
          ]},
          { label: "player — NowPlaying (sharedUi)", badge: "player", children: [
            { label: "NowPlayingScreen", badge: "page" },
            { label: "PlayerLyrics + LyricClock", badge: "api" },
            { label: "PlayerQueue + PlayerControls", badge: "api" },
            { label: "ArtworkMeshBackdrop + MeshGradient", badge: "api" },
          ]},
        ]
      },
      { kind: "loclist", title: "Pipeline audio — processeurs & cache", sub: "app/playback/ + desktop/audio",
        rows: [
          { label: "PlaybackService.kt", meta: "8337 loc · MediaLibraryService + double ExoPlayer", bar: 8337 },
          { label: "CrossfadeController.kt", meta: "3035 loc · crossfade beat-aware", bar: 3035 },
          { label: "DesktopAudioEngine.kt", meta: "1710 loc · FFmpeg/javacpp", bar: 1710 },
          { label: "DesktopApp.kt", meta: "7391 loc · état racine desktop", bar: 7391 },
          { label: "QueueCoordinator.kt + Autoplay.kt", meta: "~800 loc · file d'attente + autoplay", bar: 800 },
          { label: "TransitionFilterProcessor.kt", meta: "~350 loc · DSP transition", bar: 350 },
          { label: "EqualizerProcessor.kt + LoudnessProcessor.kt", meta: "~400 loc · EQ + loudness", bar: 400 },
          { label: "AudioCache.kt + DynamicLruCacheEvictor.kt", meta: "~300 loc · cache LRU", bar: 300 },
          { label: "FfmpegAudioDecoder/Renderer (vendored)", meta: "4 fichiers · FFmpeg JNI", bar: 600, muted: true },
          { label: "DSD (dsd/) — DSD decoders", meta: "3 fichiers · DSD natif", bar: 250, muted: true },
        ]
      },
      { kind: "loclist", title: "Analyse Automix — C++ natif", sub: "native/analyzer/ → konnekt_analysis.so/.dll",
        rows: [
          { label: "audio_analysis.cpp", meta: "691 loc · AnalyzeAudio pipeline complet", bar: 691 },
          { label: "tempo_analysis.cpp", meta: "553 loc · onset 180s + PLL beats", bar: 553 },
          { label: "mel_spectrogram.cpp", meta: "219 loc · Mel 11.025 kHz", bar: 219 },
          { label: "vocal_spectrogram.cpp", meta: "137 loc · heuristique vocale", bar: 137 },
          { label: "resampler.cpp", meta: "125 loc · resampling", bar: 125 },
          { label: "ffmpeg/ffmpeg_jni.cc", meta: "472 loc · pont JNI FFmpeg", bar: 472 },
          { label: "analyzer/audio_analysis.h", meta: "150 loc · AnalysisResult struct", bar: 150 },
        ]
      },
    ]
  },
];

fs.writeFileSync(path.join(import.meta.dirname, "facets.json"), JSON.stringify({ facets }, null, 2));
console.log(`facets: ${facets.length} facets → facets.json`);
for (const f of facets) console.log(`  ${f.id}: ${f.sections.length} sections`);
