# RUC Native Helper (C++)

Minimal native helper process for agent-side WebRTC bridge integration.

Current status:

- transport protocol: JSON lines over `stdin/stdout`
- implementation: stub (no libwebrtc yet)
- purpose: provide a stable contract between Java agent and future native bridge

## Build (Windows, CMake)

```powershell
cd native-helper
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
```

Binary path example:

`native-helper/build/Release/ruc_native_helper.exe`

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
