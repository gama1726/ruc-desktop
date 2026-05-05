# RUC Native Helper (C++)

Native helper process for agent-side WebRTC bridge integration.

Current status:

- transport protocol: JSON lines over `stdin/stdout`
- implementation: real `offer/answer/ice` handling via `libdatachannel`
- purpose: terminate WebRTC on native side and exchange signaling with Java agent

## Build (Windows, CMake)

```powershell
cd native-helper
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

Binary path example:

`native-helper/build/Release/ruc_native_helper.exe`

Build notes:

- CMake needs internet access on first configure step (FetchContent downloads dependencies).
- Toolchain must include C++17 compiler and git.

## Protocol

Input frames:

- `{"type":"offer","payload":{...}}`
- `{"type":"ice-candidate","payload":{...}}`
- `{"type":"shutdown"}`

Output frames:

- `{"type":"helper-ready","payload":{...}}`
- `{"type":"answer","payload":{...}}`
- `{"type":"ice-candidate","payload":{...}}`
- `{"type":"log","payload":{...}}`

Important payload fields:

- `answer.payload.mode = "webrtc"` when helper produced real SDP
- `ice-candidate.payload.mode = "webrtc"` for real ICE candidates
