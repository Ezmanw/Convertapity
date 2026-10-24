#!/usr/bin/env python3
"""Builds ffmpeg-arm64.zip from Termux's Android (bionic) ffmpeg package and the shared libs it needs.

Layout of the zip:  bin/ffmpeg   lib/<every non-system .so ffmpeg loads>
The app runs bin/ffmpeg with LD_LIBRARY_PATH=lib.
"""
import os, re, shutil, subprocess, sys, tarfile, tempfile, urllib.request, zipfile

REPO = "https://packages.termux.dev/apt/termux-main"
PREFIX = "data/data/com.termux/files/usr"
# Provided by Android itself
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so", "libEGL.so",
          "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libmediandk.so",
          "libvulkan.so", "libnativewindow.so", "libcamera2ndk.so", "libjnigraphics.so", "libaaudio.so"}


def fetch(url):
    with urllib.request.urlopen(url) as r:
        return r.read()


def packages():
    text = fetch(f"{REPO}/dists/stable/main/binary-aarch64/Packages").decode()
    pkgs = {}
    for stanza in text.split("\n\n"):
        f = dict(re.findall(r"^([\w-]+): (.*)$", stanza, re.M))
        if "Package" in f:
            pkgs[f["Package"]] = f
    return pkgs


def deps(f):
    out = []
    for d in f.get("Depends", "").split(","):
        d = d.split("|")[0].strip()
        d = re.sub(r"\s*\(.*\)", "", d)
        if d:
            out.append(d)
    return out


def needed(path):
    out = subprocess.run(["readelf", "-d", path], capture_output=True, text=True).stdout
    return re.findall(r"\(NEEDED\).*\[(.+?)\]", out)


def main(out_zip):
    pkgs = packages()
    todo, seen = ["ffmpeg"], set()
    while todo:
        p = todo.pop()
        if p in seen or p not in pkgs:
            continue
        seen.add(p)
        todo += deps(pkgs[p])
    print(f"{len(seen)} packages")

    work = tempfile.mkdtemp()
    root = os.path.join(work, "root")
    os.makedirs(root)
    for p in sorted(seen):
        deb = os.path.join(work, p + ".deb")
        with open(deb, "wb") as fh:
            fh.write(fetch(f"{REPO}/{pkgs[p]['Filename']}"))
        x = os.path.join(work, "x_" + p)
        os.makedirs(x)
        subprocess.run(["ar", "x", deb], cwd=x, check=True)
        data = next(n for n in os.listdir(x) if n.startswith("data.tar"))
        with tarfile.open(os.path.join(x, data)) as t:
            t.extractall(root, filter="tar") if sys.version_info >= (3, 12) else t.extractall(root)

    usr = os.path.join(root, PREFIX)
    libdir = os.path.join(usr, "lib")
    stage = os.path.join(work, "stage")
    os.makedirs(os.path.join(stage, "bin"))
    os.makedirs(os.path.join(stage, "lib"))
    shutil.copy(os.path.join(usr, "bin", "ffmpeg"), os.path.join(stage, "bin", "ffmpeg"))

    queue, done = needed(os.path.join(usr, "bin", "ffmpeg")), set()
    while queue:
        so = queue.pop()
        if so in done or so in SYSTEM:
            continue
        done.add(so)
        src = os.path.join(libdir, so)
        if not os.path.exists(src):
            print(f"  assuming system lib: {so}")
            continue
        dst = os.path.join(stage, "lib", so)
        shutil.copy(os.path.realpath(src), dst)  # dereference symlinks
        queue += needed(dst)
    print(f"{len(done)} libs")

    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for d, _, files in os.walk(stage):
            for n in files:
                full = os.path.join(d, n)
                z.write(full, os.path.relpath(full, stage))
    print(f"wrote {out_zip} ({os.path.getsize(out_zip) // 1024 // 1024} MB)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "ffmpeg-arm64.zip")
