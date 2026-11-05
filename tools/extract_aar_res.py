#!/usr/bin/env python3
"""Extract res/ from specific AARs for the aapt2 link step.

Usage: extract_aar_res.py <out-dir> group:artifact:version [group:artifact:version ...]
Downloads each AAR (Google Maven first, then Central) and extracts its res/
tree into <out-dir>/<artifact>-<version>-res/.

Only needed for AARs whose resources the app references at link time
(currently: material3 themes). The main resolver (resolve_deps.py) also
extracts res/ when it runs with the current script; this helper covers
runs done before that change and any extra AAR link errors name later.
"""
import os
import sys
import urllib.request
import zipfile

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "flick-manual-build/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def main():
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    for spec in sys.argv[2:]:
        g, a, v = spec.split(":")
        path = "/".join([g.replace(".", "/"), a, v, f"{a}-{v}.aar"])
        data = None
        for base in (GOOGLE, CENTRAL):
            try:
                data = fetch(f"{base}/{path}")
                break
            except Exception:
                continue
        if data is None:
            print(f"FAILED: {spec} (no AAR found)")
            continue
        tmp = os.path.join(out_dir, f"{a}-{v}.aar")
        with open(tmp, "wb") as f:
            f.write(data)
        res_dir = os.path.join(out_dir, f"{a}-{v}-res")
        with zipfile.ZipFile(tmp) as z:
            count = 0
            for n in z.namelist():
                if n.startswith("res/") and not n.endswith("/"):
                    target = os.path.join(res_dir, n[4:])
                    os.makedirs(os.path.dirname(target), exist_ok=True)
                    with open(target, "wb") as f:
                        f.write(z.read(n))
                    count += 1
        os.remove(tmp)
        print(f"{spec}: extracted {count} resource files -> {res_dir}")


if __name__ == "__main__":
    main()
