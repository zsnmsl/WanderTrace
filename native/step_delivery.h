#pragma once
#include <cstddef>
#include <cstdint>
#include <deque>

namespace wander {
enum class SendResult { Sent, Retry, Closed, Error };

// A bounded per-client queue. Congestion does not silently consume an event;
// overflow drops the oldest event and is explicitly reported to diagnostics.
template<class Event> class StepDelivery {
    std::deque<Event> pending;
    uint64_t droppedCount = 0;
public:
    static constexpr size_t capacity = 512;
    size_t size() const { return pending.size(); }
    uint64_t dropped() const { return droppedCount; }
    void recordDropped(uint64_t count) { droppedCount += count; }
    void clear() { pending.clear(); }
    template<class Predicate> void eraseIf(Predicate predicate) {
        for (auto i = pending.begin(); i != pending.end();) {
            if (predicate(*i)) i = pending.erase(i); else ++i;
        }
    }
    void push(const Event& event) {
        if (pending.size() == capacity) { pending.pop_front(); ++droppedCount; }
        pending.push_back(event);
    }
    template<class Sender> SendResult flush(Sender sender, size_t budget = 64) {
        while (!pending.empty() && budget--) {
            SendResult result = sender(pending.front());
            if (result != SendResult::Sent) return result;
            pending.pop_front();
        }
        return SendResult::Sent;
    }
};
}
