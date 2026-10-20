# LUMIX Lab 3.1.0: BLE registration and Wi-Fi handover evidence

Investigation date: 2026-10-20. These are static observations from the available Android APK. The APK was not executed, and no S9 hardware connection was available. This document records protocol facts and reconstruction limits; it does not claim successful camera interoperability.

## Artifact and tools

- APK: `com.panasonic.jp.lumixlab.apk`, package `com.panasonic.jp.lumixlab`, reported version `3.1.0`.
- APK SHA-256: `6ba873110d201675a905f7dac39bcda3291674e7440a484d6d5bd1a78447115f`.
- JADX headless MCP JAR 0.7.1 SHA-256: `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`.
- REA Android bridge SHA-256: `a02b1ef0a4a40abd1ae310b9ee686ca163e0b59a3256df524c35b90ed17e9e63`.
- Engine source revision reported by REA: `5844800a486d2248fa949b74c9896285f5b54de2`.
- JDK used: Java 21.0.9. REA CLI package: `rea-agents@6.7.0`.

The APK, DEX files, decompiled code and complete local Evidence records remain in ignored `.analysis/`. No Panasonic implementation code is included in this document. Android build tools 35.0.0 `apksigner verify --print-certs` completed successfully. Signer SHA-1 `a6023163ad7413ee9e446a583dbb850cdf283051` matches the APKPure listing used in this investigation; signer SHA-256 is `6e8e62b2206759c021bf316feda8bfd814007f46aba8f1fd5097aa40c9017e03`. Output is retained as `.analysis/ble-protocol-apk-signature.txt`. This verifies the analyzed package's signature and the listing match, without independently proving Play Store origin or publisher identity.

## REA Evidence records

| Subject | Local record | Evidence ID |
| --- | --- | --- |
| Package identity and manifest | `package-evidence.json` | `ev_add6ecf2a80a4bc6bfd3ae0eff5c85715a5ef9dd6858411e65218897d10ca60e` |
| Registration chain, raw `z5.s1.run` | `ble-protocol-registration-chain-evidence.json` | `ev_676d2ad252c256cb28332ae676ee7ea5acbf496178228e5726e4459fb6c31873` |
| UUID creation and write, raw `z5.d2.run` | `ble-protocol-uuid-write-evidence.json` | `ev_69d841808f7ff512e1b5e25f64d327ca1bf70a6f59f80c5d6e03c8c42ad0d2de` |
| APP_INFO formatting/write, raw `z5.c2.run` | `ble-protocol-app-info-evidence.json` | `ev_46ef8092b55005683226a34549f2e7f9766ef98ce1a3f7c078490591e5e27822` |
| Four-byte encoding, raw `z5.k2.e` | `ble-protocol-codec-evidence.json` | `ev_ec9f714d1112ac1e48421ae8d1811194fb5d4a0567a1deac4586f53f24010e43` |
| Wi-Fi action request, `CameraWifiConnectFragment.onViewCreated` | `ble-protocol-wifi-handover-evidence.json` | `ev_1e23033862d8d7c86e5b9409b2031b5ba94d8e085243bcf475ced92e8cecfb9f` |
| Quoted HTTP bearer token, raw `util.v.J` | `ble-protocol-auth-bearer-evidence.json` | `ev_6271ad9f40e76bf5473f693142d66bc5329dd5d45f9f9566d1ae2e1c5eef67f4` |

All six focused method records report available Java bodies, complete source delivery, and no smali fallback. This does not establish that every decompiler inference is correct or that the complete application was analyzed.

Auxiliary analysis used the same verified JAR through its public Java API, separately from REA. Case-distinct obfuscated classes such as `z5.s1` and `z5.S1` require distinct output filenames on Windows. Corrected auxiliary output is in `.analysis/ble-protocol-java-stable/`, with Java-name hashes appended. Earlier `.analysis/ble-protocol-java/` files must not be used to disambiguate such names.

## Discovery and connection

Observed in raw `H5.j.j` (stable auxiliary file `H5.j_2187a9.java`, lines 453 onward) and `H5.g.onScanResult` (`H5.g_2187a6.java`, lines 39 onward):

1. Scan for advertised service `054ac620-3214-11e6-ac0d-0002a5d5c51b`.
2. Lab uses balanced scan mode, all-match callbacks and sticky matching.
3. Lab additionally validates Panasonic manufacturer ID `0x003a` and a six-byte manufacturer payload. Its decoder looks for status byte `0` or `3`; the path for `3` is passed onward with the pairing flag set. Bytes 2–5 are used as the public camera identity.
4. Advertising local names are parsed as a friendly name followed by line feed `0x0a` and one model-code byte. Lab strips the last two bytes. This explains why matching only a human-readable name or expecting a different service can miss a camera.
5. GATT is opened with LE transport and `autoConnect=false`. The app requests MTU 57 after connection, then discovers services after the successful MTU callback (`H5.d_2187a3.java`, lines 1315 and 1420 onward).

The discovered control service is **different from the advertising service**: `1fe71b85-8344-4e6a-bf1a-ac7acdb3f8af`. Characteristic resolution is in `H5.d.onServicesDiscovered`, lines 1485 onward. Lab locally enables notifications for every characteristic that advertises NOTIFY. Explicit CCCD writes are not present in that method; descriptor behavior on actual camera firmware remains untested.

## Relevant characteristics

Names are embedded in raw `H5.a`; UUID-to-field binding is corroborated by `H5.d.onServicesDiscovered`.

| Purpose | UUID | Observed operation |
| --- | --- | --- |
| INITIAL_INFORM | `239c7203-922f-43e9-bdf1-6d1fedf6f43f` | Read session encoding key |
| APP_UUID | `bff12e7f-5944-4eb6-a90f-48d8786157ea` | Encoded UUID write |
| APP_INFO | `ed8b1352-5aa7-4155-b7c9-521292dd1932` | Plain, fixed 32-byte application information |
| FRIENDLY_NAME | `05a797b6-9c2f-4d18-99a7-fa61a3880bfe` | Encoded phone name write |
| Conditional Wi-Fi compatibility command | `36f99090-e79b-4060-9ac7-89f3f8bf88d5` | Single byte `02`, with response, on the WPA2/Harmony path |
| WIFI_SSID | `657edba2-5bc8-42dd-974f-21871d7dbcd1` | Plain UTF-8 read |
| WIFI_PASSWORD | `42d509f7-f122-4816-92d1-5e402d9453cb` | Encoded read |
| READ_WIFI_FREQUENCY_BAND | `419010c2-3a85-47d6-9e71-a1ea58ced881` | Read |
| CAMERA_DESTINATION | `e08508ea-b4f8-4a22-8025-b1f22748e8e4` | Read |
| CAMERA_DESTINATION_SUB | `b2c2c10e-2702-4f75-9c0e-d676dd249b20` | Optional read |
| CER_FINGER_PRINT | `1be8a9e3-178b-46c4-b6b3-43d4a5311fc1` | Certificate fingerprint read |
| ACTION_MODE | `7644371c-1ddc-4b16-832f-f07c322e311b` | Single byte `01` requests default Wi-Fi mode, without response |
| AP_STATE | `dcba3a74-80bc-4919-8ef4-0c9f99cc20dd` | First notification byte `01` allows Wi-Fi join |
| ACTION_MODE_RESULT | `6c875728-7636-425e-818a-f1d93a39e075` | First notification byte `01` follows Lab's failure path |

The command's camera-side name is unknown. The renewed trace below establishes that it is conditional on the same capability branch as WPA2 selection. Calling it a universal registration completion, acceptance or pairing-status command is unsupported.

## Ordered registration exchange

The ordinary non-USB path in raw `z5.s1.run` chains `z5.S1`, `z5.d2`, `z5.c2`, `z5.e2`, then `z5.x1` and `z5.i2`:

1. Read INITIAL_INFORM. Save the returned bytes as the session key; the codec uses its first four bytes. This key is **not** the four-byte manufacturer camera identity.
2. Write encoded APP_UUID **with response**. Lab creates a random UUID once, removes hyphens, persists it under `DATA_STORE_UUID`, converts the 32 hex digits into 16 raw bytes, packs and encodes those bytes.
3. Write APP_INFO **with response**. Lab uses the ASCII text `LUMIX LUT Creators APP ` plus its version name, truncates to 32 bytes and zero-fills the remainder. A reconstructed client using its own identity follows the observed format, but acceptance of arbitrary application text is not established by static analysis.
4. Write encoded FRIENDLY_NAME **with response**. Lab uses Android `Build.MODEL` as the source name. The same packing and encoding is applied.
5. **Conditional:** write the single byte `02` to `36f99090-e79b-4060-9ac7-89f3f8bf88d5` **with response** when the phone lacks WPA3-SAE support or Lab detects Huawei/Harmony. On a WPA3-capable non-Harmony phone, Lab skips this write. This is one byte with value two, not two zero bytes. The initial investigation established its value and order but had not resolved this condition; the renewed trace below corrects that omission.
6. Read WIFI_SSID, WIFI_PASSWORD, READ_WIFI_FREQUENCY_BAND, CAMERA_DESTINATION, optionally CAMERA_DESTINATION_SUB, then CER_FINGER_PRINT. Lab subsequently marks Wi-Fi information as read and continues with other camera metadata.

Stable auxiliary corroboration: `z5.S1_6d00211.java:21`, `z5.M1_6d00157.java:199`, `z5.e2_6d00440.java:32`, `z5.i2_6d004bc.java:23`, `z5.w1_6d0066d.java:17`, `z5.v1_6d0064e.java:17`. The fingerprint decoder is also in `z5.M1.c`, case 0.

No distinct camera-side “registration accepted” notification was established along this inspected chain. GATT completion, Wi-Fi information availability and a successful later authenticated connection must be reported as separate stages. No explicit `createBond` call was observed in the inspected central/registration classes; this bounded observation does not rule out Android-driven bonding or other application paths.

## Packing and byte encoding

Packing, independently described from raw `z5.k2.d` and `z5.e2.run`:

- Let `n` be the payload length, and let `z = (4 - n mod 4) mod 4`.
- Allocate `roundUpToMultipleOf4(n + z + 1)` bytes initialized to `ff`.
- Set byte 0 to `n`, copy payload bytes starting at byte 1, and append `z` zero bytes immediately after the payload.
- A 16-byte UUID therefore packs to 20 bytes: length `10`, 16 raw UUID bytes, then three `ff` bytes.

For each four-byte plaintext block `[p0,p1,p2,p3]` and key `[k0,k1,k2,k3]`, the wire block is `[p3 XOR k0, p2 XOR k1, p1 XOR k2, p0 XOR k3]`. Decryption yields `[c3 XOR k3, c2 XOR k2, c1 XOR k1, c0 XOR k0]`. This is reversible byte obfuscation, not a claim of cryptographic security.

Password decoding applies the inverse transform, takes byte 0 as the payload length, then extracts that many bytes. Lab's helper accepts printable ASCII 33–126 until NUL (`I5.d.a`); SSID is decoded separately as trimmed UTF-8. Stable auxiliary password source: `z5.M1_6d00157.java`, lines 388 onward. Bounds checks in a new implementation are a defensive design choice.

## Wi-Fi handover and result handling

After credentials are available, `CameraWifiConnectFragment.onViewCreated` writes a single byte `01` to ACTION_MODE without response. The default mode is explicitly initialized to 1; other UI operations can supply other mode bytes. The source also establishes that mode 5 is a separate path, so it must not be substituted for the default.

AP_STATE first byte `01` permits the UI to join the camera WLAN after Wi-Fi information has been read. ACTION_MODE_RESULT first byte `01` has a different meaning: `H5.d.onCharacteristicChanged` stores it, `z5.o1.run` broadcasts it after a one-second debounce, and `z5.p2.onReceive` routes value 1 to the failure dialog. No interpretation of other result codes is established here.

Corroborating auxiliary locations: `H5.d_2187a3.java:188`, `H5.d_2187a3.java:207`, `z5.o1_6d00575.java:21`, `z5.p2_6d00595.java:21`, `CameraWifiConnectFragment_746b2795.java:129` and `:531`.

Hardware-dependent unknowns: camera confirmation timing, accepted third-party APP_INFO strings, S9 firmware differences, notifications/CCCD behavior, Wi-Fi activation latency, OS network permissions, and end-to-end HTTPS authentication. These require a user test with the S9; static reconstruction cannot verify them.

## Renewed audit after the 0.3.5 connection report

The user reports that the S9 appears in the scan but does not connect, with no error displayed. This makes post-selection diagnostics necessary; the static comparison alone cannot identify the exact stage reached on the user's phone.

First-time pairing has now been traced independently: `CameraBluetoothConnectionPairingInProgressFragment.Q` schedules raw `z5.V.run` case 0, which creates the internal camera record, marks its application state `BLE_STATE_PAIRED`, then calls `z5.k2.i`. That method passes the device and advertised camera identity to `H5.j.d`. Service discovery subsequently enters the same `z5.s1.run` BLE exchange described above. The inspected first-time path does not introduce another pairing-only GATT write. The internal `BLE_STATE_PAIRED` assignment does not itself establish Android system bonding.

Lab's Add Camera scan uses the second scan flag set to true, rejecting advertising status 0 and accepting the status-3 pairing path. Version 0.3.5's broader scanner does not preserve that distinction. This is a concrete UI eligibility difference; a causal link to the user's failure remains unproven.

Two additional post-selection differences from baseline commit `c3460f0` were verified:

- Lab enables notifications locally on every NOTIFY characteristic and starts the key read without CCCD writes. The 0.3.5 session instead queued descriptor writes for two characteristics before reading INITIAL_INFORM. Complete direct Bluetooth method-reference inventories of both DEX files contain neither `BluetoothGatt.writeDescriptor` nor `BluetoothDevice.createBond`. Reflection/native behavior remains outside that negative result's scope.
- Lab waits 1,000 ms after the connected callback before requesting MTU 57. The 0.3.5 session requested it immediately. Matching the observed delay is a compatibility adjustment; hardware evidence is still needed to establish whether timing contributed to the failure.

The renewed capability trace resolves the conditional command: raw `I5.c.f` returns `WifiManager.isWpa3SaeSupported()`, while raw `util.v.R0` detects Huawei/Harmony variants. Raw `z5.i2.run` skips `02` when WPA3 is supported and Harmony is not detected. `I5.c.b` uses that same predicate to choose `setWpa3Passphrase`; the other branch uses `setWpa2Passphrase`. Therefore 0.3.5's unconditional `02` and forced WPA2 differed from Lab's normal WPA3 path. The camera-side meaning “select WPA2 compatibility” is an inference from this correlated branch, not an embedded characteristic label.

No evidence established an APP_INFO whitelist in camera firmware. Lab sends its own fixed application label; the camera's treatment of another label is unknown. This audit also found no independently verified fingerprint decoding defect: ordinary ASCII hexadecimal fingerprint data is compatible with the existing decoder. Omitted band/destination reads populate client metadata and subsequent capability branches; no camera-side requirement to read those values before authentication can be established from this client alone.

New complete REA method Evidence records in ignored `.analysis/`:

| Method | File | Evidence ID |
| --- | --- | --- |
| `z5.V.run` | `recheck-first-pairing-evidence.json` | `ev_7ad5be5c7a46ce5f3da3e7ed8c15e6298e7381ebabd24597907e1934c696c472` |
| `z5.k2.i` | `recheck-connect-current-camera-evidence.json` | `ev_22fcc11fa8c1350d6fa97b4918c74b49f95c3956cda84105ebc7c7e067501e39` |
| `H5.d.onServicesDiscovered` | `recheck-services-registration-evidence.json` | `ev_16da82db254e71f0aedc75fe82698e95ec7ecffe563b6e664c47400c3454b1c5` |
| `H5.d.onConnectionStateChange` | `recheck-connection-state-evidence.json` | `ev_f591bef7d207170542401fad5b8ad78cebbfe8c9f75201cb0e14c5726e4f75c9` |
| `I5.c.f` | `recheck-wpa3-capability-evidence.json` | `ev_94734b98d828ca30a54223a195ed8f46ac72514aa2a9db13949a970737354e16` |
| `z5.i2.run` | `recheck-conditional-security-mode-evidence.json` | `ev_87b03156d1fee66ae892a5f25d399bc1c47518f3214584045304642d578a474d` |

Each reports available Java source with complete delivery and no fallback markers. `.analysis/recheck-pairing-findings.json` links the source comparison, auxiliary decompilation and bounded negative results. The APK and tool identities remain unchanged.

## HTTPS authentication check

The existing `LumixLabClient.authenticate` matches the observed authentication schema and header formatting:

- Raw `h6.a.p` carries the Retrofit POST annotation for `auth` and JSON request/accept headers (`h6.a_301741.java:246`). Its body is `AuthRequestBean`.
- `AuthRequestBean` explicitly maps JSON properties `AppInfo`, `Uuid` and `FriendlyName`. Its constructor appends a space and the application version to the supplied application label. Lab's resulting value is `LUMIX LUT Creators APP 3.1.0`, matching its BLE APP_INFO text before fixed-width padding. The HTTP value contains no BLE padding or XOR encoding.
- Lab supplies its persisted, hyphen-free 32-hex UUID and phone `Build.MODEL`, matching the identities registered through BLE (`z5.k2_6d004fa.java:258`). A reconstructed client should reuse its own persisted identity across both transports.
- `AuthResponseBean` explicitly maps `Data.AuthToken` and `Header.ResponseCode` (`com.panasonic.jp.lumixlab.bean.AuthResponseBean_5758318.java:9` onward).
- The Authorization value includes literal surrounding apostrophes: `'Bearer TOKEN'`. This unusual formatting is directly supported by the REA `util.v.J` Evidence above; it is not a standards-based guess.

The client's own application label/version and friendly name follow this structure. Static evidence cannot establish whether a particular firmware accepts third-party application labels. The protocol schema and quoted bearer formatting are verified; successful HTTPS authentication on the S9 remains untested. The camera base-URL string `https://192.168.54.1/` is embedded in the APK, but this follow-up did not trace its complete factory selection logic.

## Reproduction

With the verified JAR and a full JDK configured, focused REA CLI queries are reproducible without starting the APK:

```powershell
$env:JAVA_HOME = 'C:\DevTools\jdk-21'
$env:REA_JADX_MCP_JAR = (Resolve-Path '.analysis\jadx-headless-mcp-0.7.1-all.jar').Path
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'z5.k2' 'e' --format json
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'z5.s1' 'run' --format json
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'z5.d2' 'run' --format json
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'z5.c2' 'run' --format json
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'com.panasonic.jp.lumixlab.controller.fragment.CameraWifiConnectFragment' 'onViewCreated' --format json
npx -y rea-agents@6.7.0 inspect-android-method '.analysis\com.panasonic.jp.lumixlab.apk' 'com.panasonic.jp.lumixlab.util.v' 'J' --format json
```

Use raw DEX class names in queries; the decompiler's generated display aliases vary. Compare artifact digests before reusing Evidence. Local Evidence records are complete static tool outputs; their identifiers are not publicly resolvable links.
