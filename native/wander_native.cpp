#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <cstdio>
#include <cstdlib>
#include <sys/socket.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <cstdint>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <map>
#include <mutex>
#include <string>
#include <vector>
#include "dobby.h"

// AOSP sensors_event_t wire ABI used by SensorService and BitTube (104 bytes).
struct Event {
    int32_t version, sensor, type, reserved0;
    int64_t timestamp;
    union { float data[16]; uint64_t stepCounter; } value;
    uint32_t flags, reserved1[3];
};
static_assert(sizeof(Event) == 104, "sensor event ABI");
using Activate = int (*)(void*, void*, int, int);
using Write = ssize_t (*)(void*, const void*, size_t);
using GetFd = int (*)(const void*);
using DecStrong = void (*)(const void*, const void*);
static Activate originalActivate;
static Write originalWrite;
static GetFd getSendFd;
static DecStrong decStrong;
static std::mutex mutex;
static std::map<int, int> sensorTypes;
struct Channel { int fd; std::map<int, int> handles; };
static std::map<void*, Channel> channels;
static bool installed, enabled, emitted;
static int64_t lastTick, sessionId = -1;
static double cadence, fractional;
static uint64_t rawCounter, counter, counterOffset, packets;

static int64_t bootNanos() {
    timespec t{}; clock_gettime(CLOCK_BOOTTIME, &t);
    return int64_t(t.tv_sec) * 1000000000LL + t.tv_nsec;
}

// Non-trivial return value matches Android sp<BitTube>'s indirect-return ABI.
struct Strong {
    void* ptr = nullptr;
    Strong() = default;
    Strong(const Strong&) = delete;
    ~Strong() { if (ptr && decStrong) decStrong(ptr, this); }
};
using GetChannel = Strong (*)(const void*);

// Read symbols from already loaded ELF objects. This avoids private-library
// dlopen restrictions in an injected module's Android linker namespace.
static void* loadedSymbol(const char* library, const char* name) {
    FILE* maps = fopen("/proc/self/maps", "re");
    if (!maps) return nullptr;
    char line[1024]; uintptr_t base = 0;
    std::string suffix = std::string("/") + library;
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, end, offset; char perms[8];
        if (strstr(line, suffix.c_str()) && sscanf(line, "%lx-%lx %7s %lx", &start, &end, perms, &offset) == 4
                && offset == 0 && perms[0] == 'r') { base = start; break; }
    }
    fclose(maps);
    if (!base) return nullptr;
    const auto* eh = reinterpret_cast<const Elf64_Ehdr*>(base);
    if (memcmp(eh->e_ident, ELFMAG, SELFMAG) || eh->e_ident[EI_CLASS] != ELFCLASS64) return nullptr;
    const auto* ph = reinterpret_cast<const Elf64_Phdr*>(base + eh->e_phoff);
    const Elf64_Dyn* dynamic = nullptr;
    for (int i=0; i<eh->e_phnum; i++) if (ph[i].p_type == PT_DYNAMIC) dynamic = reinterpret_cast<const Elf64_Dyn*>(base + ph[i].p_vaddr);
    if (!dynamic) return nullptr;
    const Elf64_Sym* symbols = nullptr; const char* strings = nullptr;
    const uint32_t *hash = nullptr, *gnu = nullptr; size_t stringSize = 0;
    auto pointer = [base](uintptr_t p) { return p < base ? p + base : p; };
    for (const auto* d=dynamic; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_SYMTAB: symbols = reinterpret_cast<const Elf64_Sym*>(pointer(d->d_un.d_ptr)); break;
            case DT_STRTAB: strings = reinterpret_cast<const char*>(pointer(d->d_un.d_ptr)); break;
            case DT_STRSZ: stringSize = d->d_un.d_val; break;
            case DT_HASH: hash = reinterpret_cast<const uint32_t*>(pointer(d->d_un.d_ptr)); break;
            case DT_GNU_HASH: gnu = reinterpret_cast<const uint32_t*>(pointer(d->d_un.d_ptr)); break;
        }
    }
    if (!symbols || !strings) return nullptr;
    size_t count = hash ? hash[1] : 0;
    if (!count && gnu) {
        uint32_t bucketsCount=gnu[0], first=gnu[1], bloomCount=gnu[2];
        const auto* buckets = reinterpret_cast<const uint32_t*>(reinterpret_cast<const uint64_t*>(gnu + 4) + bloomCount);
        const auto* chains = buckets + bucketsCount;
        uint32_t last = 0; for (uint32_t i=0;i<bucketsCount;i++) last=std::max(last,buckets[i]);
        if (last < first) count = first;
        else { count = last; while (count < 100000 && !(chains[count++ - first] & 1)) {} }
    }
    for (size_t i=0;i<count && i<100000;i++) {
        if (symbols[i].st_value && symbols[i].st_name < stringSize && !strcmp(strings + symbols[i].st_name, name))
            return reinterpret_cast<void*>(base + symbols[i].st_value);
    }
    return nullptr;
}

static int activateHook(void* device, void* ident, int handle, int active) {
    int result = originalActivate(device, ident, handle, active);
    if (result != 0) return result;
    std::lock_guard<std::mutex> guard(mutex);
    auto type = sensorTypes.find(handle);
    if (type == sensorTypes.end()) return result;
    if (!active) {
        auto c = channels.find(ident);
        if (c != channels.end()) {
            c->second.handles.erase(handle);
            if (c->second.handles.empty()) { close(c->second.fd); channels.erase(c); }
        }
        return result;
    }
    auto c = channels.find(ident);
    if (c == channels.end()) {
        void** vtable = *reinterpret_cast<void***>(ident);
        Dl_info info{};
        // The primary ISensorEventConnection vtable slot is verified against this
        // device's BnSensorEventConnection::onTransact (GET_SENSOR_CHANNEL case).
        if (!dladdr(vtable[4], &info) || !info.dli_fname
                || !strstr(info.dli_fname, "libsensorservice.so")) return result;
        Strong tube = reinterpret_cast<GetChannel>(vtable[4])(ident);
        if (!tube.ptr) return result;
        int fd = fcntl(getSendFd(tube.ptr), F_DUPFD_CLOEXEC, 0);
        if (fd < 0) return result;
        c = channels.emplace(ident, Channel{fd, {}}).first;
    }
    c->second.handles[handle] = type->second;
    return result;
}

static ssize_t writeHook(void* tube, const void* data, size_t bytes) {
    if (!data || bytes == 0 || bytes % sizeof(Event)) return originalWrite(tube, data, bytes);
    std::vector<Event> filtered;
    bool changed = false;
    {
        std::lock_guard<std::mutex> guard(mutex);
        const auto* events = static_cast<const Event*>(data);
        for (size_t i = 0; i < bytes / sizeof(Event); i++) {
            Event event = events[i];
            auto sensor = sensorTypes.find(event.sensor);
            if (sensor == sensorTypes.end() || event.type != sensor->second) {
                filtered.push_back(event); continue;
            }
            if (event.type == 19) {
                rawCounter = event.value.stepCounter;
                if (!emitted) counter = std::max(counter, rawCounter);
                if (!enabled) {
                    counterOffset = std::max(counterOffset, counter > rawCounter ? counter - rawCounter : 0);
                    event.value.stepCounter = rawCounter + counterOffset;
                    counter = std::max(counter, event.value.stepCounter);
                    changed |= counterOffset != 0;
                }
            }
            if (enabled) { changed = true; continue; }
            filtered.push_back(event);
        }
    }
    if (!changed) return originalWrite(tube, data, bytes);
    if (!filtered.empty()) {
        ssize_t sent = originalWrite(tube, filtered.data(), filtered.size() * sizeof(Event));
        if (sent < 0) return sent;
    }
    return static_cast<ssize_t>(bytes);
}

static std::string install(const std::vector<int>& handles, const std::vector<int>& types) {
    std::lock_guard<std::mutex> guard(mutex);
    if (installed) return "ready";
    size_t count = handles.size();
    if (!count || count != types.size()) return "non-wakeup step sensors unavailable";
    void* activate = loadedSymbol("libsensorservice.so", "_ZN7android12SensorDevice8activateEPvii");
    void* write = loadedSymbol("libsensor.so", "_ZN7android7BitTube5writeEPKvm");
    getSendFd = reinterpret_cast<GetFd>(loadedSymbol("libsensor.so", "_ZNK7android7BitTube9getSendFdEv"));
    decStrong = reinterpret_cast<DecStrong>(loadedSymbol("libutils.so", "_ZNK7android7RefBase9decStrongEPKv"));
    auto* transact = static_cast<uint32_t*>(loadedSymbol("libsensor.so",
            "_ZN7android23BnSensorEventConnection10onTransactEjRKNS_6ParcelEPS1_j"));
    bool verified = false;
    if (transact) for (int i = 0; i < 192; i++) {
        // LDR X9,[X8,#0x20], followed soon by BLR X9: stable sp return call site.
        if (transact[i] == 0xf9401109u) {
            for (int j = 1; j <= 3; j++) if (transact[i+j] == 0xd63f0120u) verified = true;
        }
    }
    if (!activate || !write || !getSendFd || !decStrong || !verified)
        return "unsupported native ABI; no hooks installed";
    for (int i = 0; i < count; i++) sensorTypes[handles[i]] = types[i];
    if (DobbyHook(write, reinterpret_cast<dobby_dummy_func_t>(writeHook), reinterpret_cast<dobby_dummy_func_t*>(&originalWrite)) != 0)
        return "BitTube hook failed";
    if (DobbyHook(activate, reinterpret_cast<dobby_dummy_func_t>(activateHook), reinterpret_cast<dobby_dummy_func_t*>(&originalActivate)) != 0) {
        DobbyDestroy(write); return "SensorDevice hook failed";
    }
    installed = true;
    __android_log_print(ANDROID_LOG_INFO, "WanderNative", "SensorService global native hooks ready (%zu sensors)", count);
    return "ready";
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wandertrace_xposed_NativeSensors_nativeInstall(JNIEnv* env, jclass, jintArray h, jintArray t) {
    int n=env->GetArrayLength(h);
    if (n != env->GetArrayLength(t)) return env->NewStringUTF("invalid handles");
    std::vector<int> handles(n),types(n);
    env->GetIntArrayRegion(h,0,n,handles.data()); env->GetIntArrayRegion(t,0,n,types.data());
    return env->NewStringUTF(install(handles,types).c_str());
}

static std::string tick(bool active, double target, int64_t session, int64_t now) {
    std::lock_guard<std::mutex> guard(mutex);
    if (session != sessionId) { cadence = fractional = 0; sessionId = session; lastTick = now; }
    double seconds = lastTick ? std::clamp((now - lastTick) / 1e9, 0.0, 1.0) : 0;
    lastTick = now;
    enabled = active;
    if (!active) { cadence = fractional = 0; }
    else {
        target = std::isfinite(target) ? std::clamp(double(target), 0.0, 240.0) : 0;
        double next = cadence + std::clamp(double(target) - cadence, -12 * seconds, 12 * seconds);
        fractional += (cadence + next) * 0.5 * seconds / 60;
        cadence = next;
        auto steps = static_cast<uint64_t>(fractional); fractional -= steps;
        if (steps) {
            counter += steps; emitted = true;
            for (auto c = channels.begin(); c != channels.end();) {
                std::vector<Event> events;
                for (auto [handle, type] : c->second.handles) {
                    for (uint64_t i = 0; i < (type == 18 ? steps : 1); i++) {
                        Event event{}; event.version = sizeof(Event); event.sensor = handle;
                        event.type = type; event.timestamp = now;
                        if (type == 19) event.value.stepCounter = counter; else event.value.data[0] = 1;
                        events.push_back(event);
                    }
                }
                ssize_t sent = send(c->second.fd, events.data(), events.size() * sizeof(Event), MSG_DONTWAIT | MSG_NOSIGNAL);
                if (sent < 0 && (errno == EPIPE || errno == ECONNRESET || errno == EBADF)) {
                    close(c->second.fd); c = channels.erase(c);
                } else { if (sent > 0) packets++; ++c; }
            }
        }
    }
    char status[256]; snprintf(status, sizeof(status), "{\"channels\":%zu,\"cadence\":%.2f,\"counter\":%llu,\"packets\":%llu}",
            channels.size(), cadence, static_cast<unsigned long long>(counter), static_cast<unsigned long long>(packets));
    return status;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wandertrace_xposed_NativeSensors_nativeTick(JNIEnv* env, jclass, jboolean active, jdouble target, jlong session) {
    return env->NewStringUTF(tick(active, target, session, bootNanos()).c_str());
}

// The persistent bridge owns this library independently of reloadable Java module generations.
extern "C" JNIEXPORT jstring JNICALL
Java_com_wandertrace_bridge_NativeBridge_nativeInstall(JNIEnv* env, jclass cls, jintArray h, jintArray t) {
    return Java_com_wandertrace_xposed_NativeSensors_nativeInstall(env, cls, h, t);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_wandertrace_bridge_NativeBridge_nativeTick(JNIEnv* env, jclass cls, jboolean active, jdouble target, jlong session) {
    return Java_com_wandertrace_xposed_NativeSensors_nativeTick(env, cls, active, target, session);
}
