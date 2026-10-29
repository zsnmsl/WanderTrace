"""Inspect release packaging without installing or running the module on a device."""
import argparse
import hashlib
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk")
args = parser.parse_args()
data = open(args.apk, "rb").read()
with zipfile.ZipFile(args.apk) as apk:
    names = apk.namelist()
    assert "assets/xposed_init" not in names, "legacy and modern entries must not coexist"
    entry = apk.read("META-INF/xposed/java_init.list").decode().strip()
    assert entry == "com.wandertrace.xposed.XposedInit", entry
    assert apk.read("META-INF/xposed/scope.list").decode().strip() == "system"
    properties=dict(line.split("=",1) for line in apk.read("META-INF/xposed/module.prop").decode().splitlines() if "=" in line)
    assert properties.get("minApiVersion") == "102" and properties.get("targetApiVersion") == "102"
    assert properties.get("autoHotReload") == "true"
    assert "lib/arm64-v8a/libwander_native.so" in names, "system sensor hook missing"
    assert not any("libAMapSDK" in name or name.endswith("/libapssdk.so") for name in names), "Android map SDK still packaged"
    assert "assets/licenses/Leaflet.ChineseTmsProviders-MIT.txt" in names, "tile-source reuse license missing"
    assert "assets/licenses/Leaflet-BSD-2-Clause.txt" in names, "map renderer license missing"
    assert all("assets/map/" + name in names for name in ("index.html", "leaflet.js", "leaflet.css", "map.js")), "bundled map renderer missing"
    assert "assets/licenses/osmdroid-Apache-2.0.txt" in names, "reuse license missing"
    assert "assets/licenses/WanderTrace-GPL-3.0.txt" in names, "project license missing"
    classes = []
    defined = []
    for name in names:
        if not name.endswith(".dex"):
            continue
        dex = apk.read(name)
        count, offset = struct.unpack_from("<II", dex, 56)
        strings=[]
        for i in range(count):
            pos = struct.unpack_from("<I", dex, offset + 4*i)[0]
            while dex[pos] & 0x80:
                pos += 1
            pos += 1
            end = dex.index(0, pos)
            value = dex[pos:end].decode("utf-8", errors="replace")
            strings.append(value)
            if value.startswith("L") and value.endswith(";"):
                classes.append(value)
        type_count,type_offset=struct.unpack_from("<II",dex,64)
        types=[strings[struct.unpack_from("<I",dex,type_offset+4*i)[0]] for i in range(type_count)]
        class_count,class_offset=struct.unpack_from("<II",dex,96)
        defined.extend(types[struct.unpack_from("<I",dex,class_offset+32*i)[0]] for i in range(class_count))
    for forbidden in ["Lcom/wandertrace/probe/", "Lorg/robolectric/", "Lorg/junit/", "Landroidx/test/"]:
        assert not any(c.startswith(forbidden) for c in classes), "test/probe class packaged: " + forbidden
    assert "Lcom/wandertrace/xposed/SystemHooker;" in classes
    assert "Lcom/wandertrace/bridge/NativeBridge;" in defined
    assert not any(c.startswith("Lio/github/libxposed/api/") for c in defined), "framework API bundled in module"
    assert not any(c.startswith("Lde/robv/android/xposed/") for c in classes), "legacy API reference remains"
    assert not any(c.startswith("Lcom/amap/") or c.startswith("Lcom/autonavi/") for c in defined), "Android map SDK classes remain"
    assert "Lcom/wandertrace/MapSearchClient;" in defined
    print("Release verified: API 102 single entry, system-only scope, auto hot reload, resident native bridge, single Web key and tile-source license.")
    print("No probe, Robolectric, JUnit or AndroidX test classes in DEX.")
print("SHA-256:", hashlib.sha256(data).hexdigest())
