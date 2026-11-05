#!/usr/bin/env python3
"""Resolve Maven dependencies by walking POM files.

Used for the daemon-free manual build of Flick: Gradle cannot run in this
sandbox (loopback TCP is blocked), so dependencies are fetched with curl
through the normal egress proxy and compiled with kotlinc + aapt2 + d8.

Usage: resolve_deps.py <seeds-file> <out-dir>
Seeds file: one "group:artifact:version" per line.
Output: <out-dir>/*.jar (classes.jar extracted from AARs, plain JARs copied)
        and <out-dir>/classpath.txt (colon-separated list of those jars).
"""
import os
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def path_of(g, a, v, ext):
    return "/".join([g.replace(".", "/"), a, v, f"{a}-{v}.{ext}"])


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "flick-manual-build/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def fetch_pom(g, a, v):
    for base in (GOOGLE, CENTRAL):
        url = f"{base}/{path_of(g, a, v, 'pom')}"
        try:
            return fetch(url)
        except Exception:
            continue
    raise RuntimeError(f"POM not found: {g}:{a}:{v}")


def parse_pom(data):
    root = ET.fromstring(data)
    props = {}
    for p in root.findall("m:properties/*", NS):
        props[p.tag.split("}")[-1]] = (p.text or "").strip()
    parent = root.find("m:parent", NS)
    parent_ref = None
    if parent is not None:
        parent_ref = (
            (parent.findtext("m:groupId", namespaces=NS) or "").strip(),
            (parent.findtext("m:artifactId", namespaces=NS) or "").strip(),
            (parent.findtext("m:version", namespaces=NS) or "").strip(),
        )
    packaging = (root.findtext("m:packaging", namespaces=NS) or "jar").strip()
    dep_mgmt = {}
    dm = root.find("m:dependencyManagement/m:dependencies", NS)
    if dm is not None:
        for d in dm.findall("m:dependency", NS):
            dg = (d.findtext("m:groupId", namespaces=NS) or "").strip()
            da = (d.findtext("m:artifactId", namespaces=NS) or "").strip()
            dv = (d.findtext("m:version", namespaces=NS) or "").strip()
            if dg and da and dv:
                dep_mgmt[(dg, da)] = dv
    deps = []
    ds = root.find("m:dependencies", NS)
    if ds is not None:
        for d in ds.findall("m:dependency", NS):
            dg = (d.findtext("m:groupId", namespaces=NS) or "").strip()
            da = (d.findtext("m:artifactId", namespaces=NS) or "").strip()
            dv = (d.findtext("m:version", namespaces=NS) or "").strip()
            scope = (d.findtext("m:scope", namespaces=NS) or "compile").strip()
            optional = (d.findtext("m:optional", namespaces=NS) or "false").strip()
            if dg and da:
                deps.append((dg, da, dv, scope, optional))
    return packaging, props, parent_ref, dep_mgmt, deps


def resolve_props(text, props_chain):
    # Replace ${x} using the nearest properties map in the chain.
    out = text
    for _ in range(10):
        if "${" not in out:
            break
        start = out.index("${")
        end = out.index("}", start)
        key = out[start + 2:end]
        val = None
        for props in props_chain:
            if key in props:
                val = props[key]
                break
        if val is None:
            return None
        out = out[:start] + val + out[end + 1:]
    return out


def fetch_module(g, a, v):
    for base in (GOOGLE, CENTRAL):
        url = f"{base}/{path_of(g, a, v, 'module')}"
        try:
            return fetch(url)
        except Exception:
            continue
    return None


def available_at_target(module_data):
    """Follow a Gradle Module Metadata available-at redirect.

    Compose (Kotlin Multiplatform) artifacts publish the base module as an
    anchor; the real Android implementation lives in a '-android' module
    (e.g. material3 -> material3-android). Returns (group, artifact, version)
    or None.
    """
    import json
    try:
        m = json.loads(module_data.decode("utf-8"))
    except Exception:
        return None
    for variant in m.get("variants", []):
        avail = variant.get("available-at")
        if not avail:
            continue
        attrs = variant.get("attributes", {})
        if attrs.get("org.jetbrains.kotlin.platform.type") == "androidJvm":
            return (
                avail.get("group"),
                avail.get("module"),
                avail.get("version"),
            )
    return None


def collect(g, a, v, out, visited, props_chain):
    key = (g, a, v)
    if key in visited:
        return
    visited.add(key)
    # Redirect KMP anchor artifacts to their Android implementation.
    mod = fetch_module(g, a, v)
    if mod:
        target = available_at_target(mod)
        if target and all(target) and target != key:
            print(f"  redirect {g}:{a}:{v} -> {target[0]}:{target[1]}:{target[2]}")
            collect(*target, out, visited, props_chain)
            return
    data = fetch_pom(g, a, v)
    packaging, props, parent_ref, dep_mgmt, deps = parse_pom(data)
    chain = [props] + props_chain
    if parent_ref:
        try:
            pdata = fetch_pom(*parent_ref)
            _, pprops, _, pdm, _ = parse_pom(pdata)
            chain.append(pprops)
            for k, val in pdm.items():
                dep_mgmt.setdefault(k, val)
        except Exception as e:
            print(f"  (parent POM unavailable for {g}:{a}: {e})", file=sys.stderr)
    out[key] = packaging
    for dg, da, dv, scope, optional in deps:
        if scope in ("test", "provided") or optional == "true":
            continue
        if not dv:
            dv = dep_mgmt.get((dg, da), "")
        if not dv:
            print(f"  SKIP (no version): {dg}:{da} (from {g}:{a})", file=sys.stderr)
            continue
        dv = resolve_props(dv, chain)
        if dv is None:
            print(f"  SKIP (unresolved prop): {dg}:{da} (from {g}:{a})", file=sys.stderr)
            continue
        if dv.startswith("[") and dv.endswith("]") and "," not in dv:
            # Exact version range, e.g. [1.7.2] means exactly 1.7.2.
            dv = dv[1:-1]
        collect(dg, da, dv, out, visited, chain)


def download_artifact(g, a, v, packaging, out_dir):
    ext = "aar" if packaging == "aar" else "jar"
    data = None
    for base in (GOOGLE, CENTRAL):
        url = f"{base}/{path_of(g, a, v, ext)}"
        try:
            data = fetch(url)
            break
        except Exception:
            continue
    if data is None and ext == "aar":
        # Some artifacts ship only as JAR.
        for base in (GOOGLE, CENTRAL):
            url = f"{base}/{path_of(g, a, v, 'jar')}"
            try:
                data = fetch(url)
                ext = "jar"
                break
            except Exception:
                continue
    if data is None:
        raise RuntimeError(f"Artifact not found: {g}:{a}:{v} ({packaging})")
    dest = os.path.join(out_dir, f"{a}-{v}.{ext}")
    with open(dest, "wb") as f:
        f.write(data)
    if ext == "aar":
        with zipfile.ZipFile(dest) as z:
            names = z.namelist()
            if "classes.jar" not in names:
                # A stub/anchor AAR (e.g. a KMP base module that was not
                # redirected). Never let it into the classpath.
                raise RuntimeError(
                    f"AAR has no classes.jar (stub/anchor?): {g}:{a}:{v}"
                )
            jar_dest = os.path.join(out_dir, f"{a}-{v}.jar")
            with open(jar_dest, "wb") as f:
                f.write(z.read("classes.jar"))
            # Keep the AAR's resources for aapt2 link (e.g. material3 themes).
            res_entries = [n for n in names if n.startswith("res/") and not n.endswith("/")]
            if res_entries:
                res_dir = os.path.join(out_dir, f"{a}-{v}-res")
                for n in res_entries:
                    target = os.path.join(res_dir, n[4:])
                    os.makedirs(os.path.dirname(target), exist_ok=True)
                    with open(target, "wb") as f:
                        f.write(z.read(n))
        os.remove(dest)
        return jar_dest
    return dest


def main():
    seeds_file, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    seeds = []
    with open(seeds_file) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                g, a, v = line.split(":")
                seeds.append((g, a, v))
    resolved = {}
    visited = set()
    for g, a, v in seeds:
        print(f"Resolving {g}:{a}:{v} ...")
        collect(g, a, v, resolved, visited, [])
    print(f"Total artifacts: {len(resolved)}")
    # Dedupe: different POMs pin different versions of the same artifact
    # (e.g. activity 1.7.0 vs 1.9.3). Keep only the highest version per
    # artifact, or d8 will fail on duplicate classes.
    def vkey(v):
        import re
        parts = re.split(r"[.\-]", v)
        nums = []
        for p in parts:
            nums.append(int(p) if p.isdigit() else -1)
        # Pre-release qualifiers (alpha/beta/rc) sort below the release.
        qual = 0
        low = v.lower()
        if "alpha" in low:
            qual = -3
        elif "beta" in low:
            qual = -2
        elif "rc" in low:
            qual = -1
        return (tuple(nums), qual)
    by_ga = {}
    for (g, a, v) in resolved:
        by_ga.setdefault((g, a), []).append(v)
    for (g, a), versions in by_ga.items():
        if len(versions) > 1:
            keep = max(versions, key=vkey)
            for v in versions:
                if v != keep:
                    print(f"  dedupe: dropping {g}:{a}:{v} (keeping {keep})")
                    del resolved[(g, a, v)]
    jars = []
    for (g, a, v), packaging in sorted(resolved.items()):
        print(f"Downloading {g}:{a}:{v} ({packaging}) ...")
        jars.append(download_artifact(g, a, v, packaging, out_dir))
    with open(os.path.join(out_dir, "classpath.txt"), "w") as f:
        f.write(":".join(sorted(jars)))
    print("Done. Classpath written to classpath.txt")


if __name__ == "__main__":
    main()
