#include "../wander_native.cpp"
#include <cassert>
#include <cstdio>
static std::vector<Event> forwarded;
static ssize_t capture(void*, const void* data, size_t bytes) {
    auto* events = static_cast<const Event*>(data);
    forwarded.assign(events, events + bytes / sizeof(Event));
    return bytes;
}
int main() {
    originalWrite = capture;
    sensorTypes[191] = 19; sensorTypes[181] = 18;
    Event event{}; event.version = sizeof(Event); event.sensor = 191; event.type = 19;
    event.value.stepCounter = 100;
    assert(writeHook(nullptr, &event, sizeof(event)) == sizeof(event));
    assert(rawCounter == 100 && counter == 100 && forwarded[0].value.stepCounter == 100);
    int sockets[2]; assert(socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_NONBLOCK, 0, sockets) == 0);
    channels[reinterpret_cast<void*>(1)] = Channel{sockets[0], {{191,19},{181,18}}};
    tick(true, 120, 1, 1000000000LL);
    int detectors = 0, counters = 0;
    for (int i = 1; i <= 100; i++) {
        tick(true, 120, 1, 1000000000LL + i * 100000000LL);
        Event out[8]; ssize_t n;
        while ((n = recv(sockets[1], out, sizeof(out), MSG_DONTWAIT)) > 0) {
            assert(n % sizeof(Event) == 0);
            for (int j=0; j < n / sizeof(Event); j++) {
                if (out[j].type==19) { counters++; assert(out[j].value.stepCounter >= 100); }
                if (out[j].type==18) { detectors++; assert(out[j].value.data[0] == 1); }
            }
        }
    }
    assert(std::abs(cadence - 120) < 0.001);
    assert(counter >= 109 && counter <= 110 && detectors >= 9 && detectors <= 10);
    assert(counters == detectors);
    forwarded.clear(); event.value.stepCounter = 101;
    writeHook(nullptr, &event, sizeof(event)); assert(forwarded.empty());
    tick(false, 0, 1, 12000000000LL);
    writeHook(nullptr, &event, sizeof(event));
    assert(forwarded.size() == 1 && forwarded[0].value.stepCounter >= 109);
    Event accel{}; accel.sensor=3; accel.type=1; accel.value.data[0]=9.8f;
    writeHook(nullptr, &accel, sizeof(accel)); assert(forwarded[0].value.data[0] == 9.8f);
    printf("Native checks passed: 104-byte ABI, linear cadence, counter/detector wire events, real-event suppression, stop restoration, unrelated sensors.\n");
    close(sockets[0]); close(sockets[1]);
}
