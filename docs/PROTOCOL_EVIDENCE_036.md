# S9 connection recheck, 0.3.6

Date: 2026-10-20. User observation: the S9 appears in the scan list, selecting it does not establish a connection, and no error is visible. No camera/phone runtime capture was supplied. These findings compare the shipped Lab client with baseline `c3460f0`; they do not establish which difference caused that device's failure.

The requested `reverse-engineer-anything` workflow reused the verified LUMIX Lab 3.1.0 APK and existing REA/JADX analysis. APK SHA-256: `6ba873110d201675a905f7dac39bcda3291674e7440a484d6d5bd1a78447115f`. Artifact provenance, signature limitations and engine identities are in [the original evidence report](PROTOCOL_EVIDENCE_035.md). Full local evidence remains under ignored `.analysis/`; no Panasonic implementation sources or APK are redistributed.

## Findings and reconstruction

| Question | Static observation | Change / remaining limit |
| --- | --- | --- |
| First-time pairing | The pairing fragment calls `z5.V.run`, then `z5.k2.i`, then ordinary GATT registration. Internal paired state is not Android bonding. | No separate pairing-only GATT write found in this traced path. Camera confirmation remains a hardware question. |
| MTU timing | `H5.d.onConnectionStateChange` schedules MTU 57 after 1,000 ms. | Wait one second; cancellation removes the delayed request. |
| Notifications | `H5.d.onServicesDiscovered` enables local notification routing for every NOTIFY characteristic. | Remove additional CCCD writes before `INITIAL_INFORM`. Direct method references in both DEX files contain no `writeDescriptor` or `createBond`; reflection/native behavior is outside that negative search. |
| Security capability | `I5.c.f` calls `WifiManager.isWpa3SaeSupported`; `util.v.R0` tests Harmony/Huawei compatibility. | Share the same decision between BLE and Android's Wi-Fi request. |
| Byte `02` command | `z5.i2.run` skips characteristic `36f99090-e79b-4060-9ac7-89f3f8bf88d5` when WPA3 is supported and Harmony is absent. The other branch writes `02`. | Remove the unconditional write. Calling this a WPA2 compatibility command is inferred from the correlated branch; its camera-side name is unknown. |
| Wi-Fi security | Auxiliary `I5.c.b` uses that predicate for WPA3 SAE versus WPA2 passphrase. | WPA3 on supported ordinary phones; WPA2 otherwise, including Harmony compatibility. No speculative retry with another mode. |
| AP readiness | The traced Lab path consumes `AP_STATE=01` as a notification. No AP_STATE read occurs in the inspected handover path. | Remove the extra read that could stall before a later notification. Keep action-write completion and AP readiness gates. |
| Camera address | Auxiliary `z5.s2.onAvailable`/`CameraWifiConnectFragment.M` obtains the DHCP gateway. | Retain gateway from the selected network; a missing gateway is not proven for the affected phone. |
| Certificate pin | `h6.d.verify` hashes the full peer leaf certificate DER with SHA-256. | Existing pinning matches this observation; no TLS weakening. |
| APP_INFO acceptance | Lab uses its own label; the analyzed client does not establish firmware acceptance rules for third-party labels. | Keep a consistent own UUID/AppInfo across BLE and HTTPS. Successful camera acceptance remains unverified. |
| Silent failure | App errors previously reached status text only. The Activity also silently rejected camera tasks while its worker was busy. | Persistent failure dialog, progress per operation, explicit rejection feedback and copyable bounded diagnostic events. Canceled workers cannot replace a newer attempt's dialog. This is our UI change, not reverse-engineered Lab behavior. |

## REA evidence ledger

Each focused method record below reports complete Java source delivery without smali fallback. Complete delivery does not prove decompiler correctness or application-wide coverage. Auxiliary methods above were inspected separately through the verified JADX Java API and are not mislabeled as REA results.

| Method / subject | Local JSON | Evidence ID |
| --- | --- | --- |
| `z5.V.run` | `recheck-first-pairing-evidence.json` | `ev_7ad5be5c7a46ce5f3da3e7ed8c15e6298e7381ebabd24597907e1934c696c472` |
| `z5.k2.i` | `recheck-connect-current-camera-evidence.json` | `ev_22fcc11fa8c1350d6fa97b4918c74b49f95c3956cda84105ebc7c7e067501e39` |
| `H5.d.onServicesDiscovered` | `recheck-services-registration-evidence.json` | `ev_16da82db254e71f0aedc75fe82698e95ec7ecffe563b6e664c47400c3454b1c5` |
| `H5.d.onConnectionStateChange` | `recheck-connection-state-evidence.json` | `ev_f591bef7d207170542401fad5b8ad78cebbfe8c9f75201cb0e14c5726e4f75c9` |
| `I5.c.f` | `recheck-wpa3-capability-evidence.json` | `ev_94734b98d828ca30a54223a195ed8f46ac72514aa2a9db13949a970737354e16` |
| `z5.i2.run` | `recheck-conditional-security-mode-evidence.json` | `ev_87b03156d1fee66ae892a5f25d399bc1c47518f3214584045304642d578a474d` |
| Fingerprint getter references | `recheck-pin-xrefs-evidence.json` | `ev_227f60e82431d04865e3f7654e160af6cc9873133ce51d37ed96078f00560a08` |
| `h6.d.verify` | `recheck-tls-pin-evidence.json` | `ev_6f273dd0d125331b9409bb4216619d37c2e30b21ec3fc460338c382a54681541` |

Reproduction uses the original report's CLI setup and `inspect-android-method <absolute-apk> <raw-class> <method> --json`. Evidence IDs identify saved local outputs, not public download links.

## SDK boundary and remaining work

Panasonic's [official LUMIX SDK](https://av.jpn.support.panasonic.com/support/global/cs/soft/tool/sdk.html) supports the S9 through the Windows/USB development kit. Its Ethernet support is listed for BGH1/BS1H. Download requires a camera serial number and license acceptance; no SDK binary was provided or analyzed. The Android Lab APK is the evidence target for this Bluetooth/Wi-Fi investigation.

Discovery, post-selection BLE setup and Wi-Fi security are answered at client-static level; third-party identity acceptance, OEM runtime behavior and a complete S9 connection remain unresolved without hardware evidence. The added diagnosis records only allowlisted event names, public characteristic UUIDs, numeric status codes, timing, app version and Android API. It excludes SSID, MAC/IP, keys, UUID identity, fingerprints, tokens and free-form exceptions. The next useful runtime evidence is the copied report from the affected phone.
