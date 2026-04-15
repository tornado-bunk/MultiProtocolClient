# Bundled iPerf binaries

Put prebuilt executable binaries in ABI-specific folders:

- `app/src/main/assets/iperf/arm64-v8a/iperf3`
- `app/src/main/assets/iperf/arm64-v8a/iperf`
- `app/src/main/assets/iperf/armeabi-v7a/iperf3`
- `app/src/main/assets/iperf/armeabi-v7a/iperf`
- `app/src/main/assets/iperf/x86/iperf3`
- `app/src/main/assets/iperf/x86/iperf`
- `app/src/main/assets/iperf/x86_64/iperf3`
- `app/src/main/assets/iperf/x86_64/iperf`

Supported filename variants:

- `iperf3` / `iperf`
- `iperf3.bin` / `iperf.bin`
- `libiperf3.so` / `libiperf.so`

At runtime the app:

1. Selects the best binary based on `Build.SUPPORTED_ABIS`
2. Extracts it to app sandbox (`files/iperf-bin/<abi>/`)
3. Applies executable permissions (`setExecutable` + fallback `chmod 755`)
4. Runs the absolute extracted path

Versions:
- iperf3: 3.21
- iperf: 2.2.1