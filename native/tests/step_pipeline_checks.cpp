#include "../step_clock.h"
#include "../step_delivery.h"
#include <cassert>
#include <cstdio>
#include <limits>
#include <vector>

static std::vector<int64_t> collect(wander::StepClock& clock, const std::vector<int64_t>& times, double target) {
    std::vector<int64_t> result;
    for (auto t : times) {
        auto span = clock.advance(t, true, 1, target);
        for (uint64_t i = 0; i < span.count; ++i) result.push_back(span.timestamp(i));
    }
    return result;
}
int main() {
    constexpr int64_t second = 1000000000LL;
    wander::StepClock regular, delayed;
    regular.advance(second, true, 1, 120); delayed.advance(second, true, 1, 120);
    std::vector<int64_t> times;
    for (int i = 1; i <= 600; ++i) times.push_back(second + i * second / 10);
    auto a = collect(regular, times, 120), b = collect(delayed, {61 * second}, 120);
    assert(a.size() == 110 && a.size() == b.size());
    assert(std::abs(a.front() - (second + std::llround(std::sqrt(10.0) * second))) <= 2);
    for (size_t i = 0; i < a.size(); ++i) {
        assert(std::abs(a[i] - b[i]) <= 2);
        assert(i == 0 || a[i] > a[i-1]);
        if (i >= 11) assert(std::abs((a[i] - a[i-1]) - second / 2) <= 2);
    }
    assert(regular.cadence() == 120 && delayed.cadence() == 120);
    wander::StepClock transitions;
    transitions.advance(0, true, 1, 120);
    assert(transitions.advance(10 * second, true, 1, 120).count == 10);
    assert(transitions.advance(15 * second, true, 1, 180).count == 12);
    auto finalRamp = transitions.advance(30 * second, true, 1, 0);
    assert(finalRamp.count == 23 && transitions.cadence() == 0);
    assert(finalRamp.timestamp(22) == 30 * second);
    auto slowing = delayed.advance(71 * second, true, 1, 0);
    assert(slowing.count == 10 && delayed.cadence() == 0);
    for (uint64_t i = 1; i < slowing.count; ++i) assert(slowing.timestamp(i) > slowing.timestamp(i-1));
    assert(delayed.advance(72 * second, false, 1, 120).count == 0);
    assert(delayed.advance(73 * second, true, 2, 120).count == 0);
    assert(delayed.advance(74 * second, true, 2, 120).count == 0 && delayed.cadence() == 12);
    assert(delayed.advance(70 * second, true, 2, 120).count == 0 && delayed.cadence() == 0);

    wander::StepClock ramp;
    ramp.advance(0, true, 1, 120);
    assert(ramp.advance(10 * second, true, 1, 120).count == 10);
    assert(ramp.advance(12 * second, true, 1, 240).count == 4 && ramp.cadence() == 144);
    ramp.advance(13 * second, true, 1, std::numeric_limits<double>::quiet_NaN());
    assert(std::isfinite(ramp.cadence()));
    wander::StepClock longPause;
    longPause.advance(0, true, 1, 240);
    auto day = longPause.advance(86400 * second, true, 1, 240);
    assert(day.count == 345560); // 20-second ramp + the complete remaining interval.
    assert(day.timestamp(day.count-1) <= 86400 * second);

    struct Item { int id; };
    wander::StepDelivery<Item> queue;
    for (int i = 0; i < 3; ++i) queue.push({i});
    assert(queue.flush([](const Item&) {return wander::SendResult::Retry;}) == wander::SendResult::Retry);
    assert(queue.size() == 3);
    std::vector<int> sent;
    auto send = [&](const Item& item) {sent.push_back(item.id);return wander::SendResult::Sent;};
    queue.flush(send, 1); assert(queue.size() == 2 && sent == std::vector<int>{0});
    assert(queue.flush([](const Item&) {return wander::SendResult::Error;}) == wander::SendResult::Error);
    assert(queue.size() == 2);
    queue.flush(send); assert(queue.size() == 0 && sent == (std::vector<int>{0,1,2}));
    for (int i = 0; i < 520; ++i) queue.push({i});
    assert(queue.size() == 512 && queue.dropped() == 8);
    sent.clear();queue.flush(send, 512);
    assert(sent.front() == 8 && sent.back() == 519 && sent.size() == 512);
    queue.push({520});
    assert(queue.flush([](const Item&) {return wander::SendResult::Closed;}) == wander::SendResult::Closed);
    assert(queue.size() == 1);queue.clear();assert(queue.size() == 0);
    queue.push({0});queue.push({1});queue.push({2});
    queue.eraseIf([](const Item& item) {return item.id == 1;});
    sent.clear();queue.flush(send);
    assert(sent == (std::vector<int>{0,2}));
    std::puts("Step pipeline checks passed: full elapsed-time integral, event timestamps, linear ramps, bounded catch-up, retry, ordering, overflow and cancellation.");
}
