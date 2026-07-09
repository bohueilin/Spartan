# Spartan — Certificate pinning runbook

**Status: prepared, deliberately NOT enabled for 1.0.**

The 1.0 build ships in mock/sample mode by default (`USE_MOCK_* = true`): there is no live traffic
to pin, and a stale pin **bricks sync** for every installed user until an app update ships. Pinning
lands together with the first release that enables real integrations — never before.
Transport today: TLS-only (`usesCleartextTraffic=false`, `network_security_config.xml`
`cleartextTrafficPermitted=false`, system trust anchors).

## When to enable

Enable in the same release that flips `USE_MOCK_WHOOP=false` / `USE_MOCK_CALENDAR=false` for
production, after the OAuth apps are verified (see docs/RELEASE_CHECKLIST.md §5).

## How to enable (network_security_config.xml)

Pin the **intermediate CA** SPKI (not the leaf — leaves rotate every 60–90 days), plus a backup
pin, plus an expiration so a forgotten rotation degrades to system trust instead of bricking:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <domain-config>
        <domain includeSubdomains="false">api.prod.whoop.com</domain>
        <pin-set expiration="YYYY-MM-DD">           <!-- ~12 months out; calendar a rotation -->
            <pin digest="SHA-256">PRIMARY_INTERMEDIATE_SPKI_B64</pin>
            <pin digest="SHA-256">BACKUP_SPKI_B64</pin>
        </pin-set>
    </domain-config>
    <domain-config>
        <domain includeSubdomains="true">googleapis.com</domain>
        <pin-set expiration="YYYY-MM-DD">
            <!-- Google publishes its root/intermediate PKI at https://pki.goog/repository/ -->
            <pin digest="SHA-256">GTS_INTERMEDIATE_SPKI_B64</pin>
            <pin digest="SHA-256">BACKUP_SPKI_B64</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

## Capturing the current SPKI hashes (run at enable time, never hardcode from docs)

```bash
# Intermediate SPKI for a host (second cert in the presented chain):
openssl s_client -connect api.prod.whoop.com:443 -showcerts </dev/null 2>/dev/null \
  | awk '/BEGIN CERT/{n++} n==2' | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
```

For Google hosts prefer the published PKI (https://pki.goog/repository/) over a live capture —
Google rotates presented chains across regions.

## Rotation runbook

1. Calendar reminder **60 days before** `pin-set expiration`.
2. Re-capture SPKIs; if the intermediate changed, ship an update with old+new pins overlapping.
3. After the update reaches >95% of the installed base (Play Console → Statistics), drop the old pin.
4. Emergency (provider rotates unexpectedly): pins past `expiration` auto-degrade to system trust;
   if inside the window, ship a hotfix release and halt any staged rollout of the broken pin.

## Testing

- Instrumented test with the pin config against the live host (expect success) and against a
  MITM proxy (expect `SSLPeerUnverifiedException`).
- Never pin in debug builds (`debug-overrides` with system+user anchors keeps Charles/mitmproxy
  usable for development).
