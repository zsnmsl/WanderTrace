#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>

namespace wander {
// One analytic interval: timestamps are computed lazily, without allocating one
// object for every step during a long scheduler pause.
struct StepSpan {
    int64_t begin = 0, end = 0;
    double initial = 0, target = 0, slope = 0, ramp = 0, fraction = 0;
    uint64_t count = 0;

    int64_t timestamp(uint64_t index) const {
        double wanted = double(index + 1) - fraction;
        double rampSteps = (initial * ramp + .5 * slope * ramp * ramp) / 60;
        double t;
        if (wanted <= rampSteps + 1e-10) {
            if (std::abs(slope) < 1e-12) t = 60 * wanted / initial;
            else {
                double discriminant = std::max(0.0, initial * initial + 120 * slope * wanted);
                t = 120 * wanted / (initial + std::sqrt(discriminant));
            }
        } else t = ramp + 60 * (wanted - rampSteps) / target;
        t = std::clamp(t, 0.0, double(end - begin) / 1e9);
        return begin + std::llround(t * 1e9);
    }
};

class StepClock {
    int64_t last = -1, session = 0;
    double cadenceValue = 0, fraction = 0;
public:
    double cadence() const { return cadenceValue; }
    StepSpan advance(int64_t now, bool active, int64_t currentSession, double target) {
        StepSpan span;
        span.begin = last; span.end = now;
        if (!active || last < 0 || currentSession != session || now < last) {
            cadenceValue = fraction = 0; session = currentSession; last = now;
            return span;
        }
        target = std::isfinite(target) ? std::clamp(target, 0.0, 240.0) : 0;
        double seconds = double(now - last) / 1e9;
        span.initial = cadenceValue; span.target = target; span.fraction = fraction;
        span.slope = target > cadenceValue ? 12 : target < cadenceValue ? -12 : 0;
        double rampRequired = std::abs(target - cadenceValue) / 12;
        span.ramp = std::min(seconds, rampRequired);
        double rampEnd = cadenceValue + span.slope * span.ramp;
        double total = fraction + (cadenceValue + rampEnd) * .5 * span.ramp / 60
                + target * (seconds - span.ramp) / 60;
        span.count = static_cast<uint64_t>(std::floor(total + 1e-10));
        fraction = std::max(0.0, total - double(span.count));
        cadenceValue = seconds >= rampRequired ? target : rampEnd;
        last = now;
        return span;
    }
};
}
