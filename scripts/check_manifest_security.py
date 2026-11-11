"""Check the merged Android manifest after a Gradle manifest task."""
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("usage: python scripts/check_manifest_security.py <merged-AndroidManifest.xml>")
path = Path(sys.argv[1])
a = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(path).getroot()
application = root.find("application")
if application is None:
    raise SystemExit("FAIL: application element missing")
expected = {
    "ProxyBroadcastReceiver": 1,
    "ProxyActivity": 50,
    "TransparentProxyActivity": 50,
    "ProxyPendingActivity": 50,
    "ProxyService": 50,
    "ProxyContentProvider": 50,
}
counts = Counter()
failures = []
for entry in application:
    name = entry.get(a + "name", "")
    if not name.startswith("top.niunaijun.blackbox.proxy."):
        continue
    name = name.rsplit(".", 1)[-1].split("$", 1)[0]
    if name in expected:
        counts[name] += 1
        if entry.get(a + "exported") != "false":
            failures.append("unprotected proxy: " + entry.get(a + "name", ""))
    elif name == "ProxyJobService":
        counts[name] += 1
        if entry.get(a + "permission") != "android.permission.BIND_JOB_SERVICE":
            failures.append("job service missing BIND_JOB_SERVICE")
for name, count in expected.items():
    if counts[name] != count:
        failures.append(f"{name}: expected {count}, got {counts[name]}")
if counts["ProxyJobService"] != 50:
    failures.append("expected 50 protected job services")
if application.get(a + "allowBackup") != "false":
    failures.append("application backup should be disabled")
if failures:
    raise SystemExit("FAIL: " + "; ".join(failures))
print(f"PASS: {path}: 251 non-exported proxies, 50 protected job services, backup disabled")
