# Step simulation: timing, delivery, and compatibility

The system module generates standard non-wake-up STEP_COUNTER and STEP_DETECTOR events through tracked SensorService client channels. A loaded Hook is not evidence that a particular application received an event. Vendor health APIs, wake-up sensors, and applications that derive steps from accelerometer data are outside the current coverage.

## Timing and delivery corrections

- `native/step_clock.h` integrates the full elapsed boot-clock interval instead of truncating scheduler delays to one second. The cadence target remains limited to 0–240 steps/minute and changes at no more than 12 steps/minute per second.
- The integral is split at the time the target cadence is reached. Each detector event receives the calculated timestamp of its individual step; a delayed update does not assign all steps the same timestamp.
- `native/step_delivery.h` keeps a bounded per-channel queue. Nonblocking backpressure or an interrupted write retains the event for a later tick, including ticks that generate no new steps. Each socket packet contains one sensor event.
- Each channel buffers up to 512 events, with at most 64 send attempts per update. Very long pauses or prolonged congestion can overflow the queue: the oldest events are discarded and counted explicitly. The generated cumulative counter retains the full elapsed-time integral; this does not guarantee that every historical detector event reaches a slow client.
- Stopping clears pending simulated events. Disabling one sensor removes only that handle's pending events. Other sensor traffic and the existing real-counter offset behavior remain unchanged.

The target supplied at an update is used for that interval's ramp calculation. No software worker can generate live events while the CPU is suspended; catch-up reconstructs timestamps after it resumes. Reconstructed events may still be treated differently by individual clients.

## Diagnostics

The app's environment detail displays captured channel count and available transport counters. System status stores the following fields inside `sensor`:

| Field | Meaning |
| --- | --- |
| `stepPipelineRevision` | `2` identifies the corrected native implementation; absent in the older resident library |
| `channels`, `handles` | Captured client channels and registered supported handles |
| `cadence`, `generatedSteps` | Generated cadence and cumulative generated steps |
| `sentEvents`, `pendingEvents` | Events written to sockets and events awaiting a write |
| `retryWrites`, `writeErrors` | Retryable writes and other write failures |
| `droppedEvents` | Events discarded because of bounded catch-up or queue overflow |
| `coverage` | `non-wakeup-standard-step` for this backend |

These counters are aggregate backend observations. Successful socket writes do not prove that a particular application accepted the steps or used them in its own calculations. A zero channel count can indicate that the application uses an unsupported source or subscribed before the activation Hook was installed. The current code does not enumerate and capture all pre-existing subscriptions.

## Validation

Portable clock and queue checks can run without an Android device:

```sh
cmake -S native/tests -B native/tests/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/tests/build --config Release
ctest --test-dir native/tests/build -C Release --output-on-failure
```

With `WANDERTRACE_BUILD_CHECKS=ON`, the Android native build also produces `wander_step_pipeline_checks` and the socket-based `wander_native_checks`. Running those Android executables requires a compatible device; compiling them is not device validation.

The reported issue concerns 云运动 on PLQ110 / PLQ110 17.0.0.104. The timing and delivery defects above were reproducible from the implementation; the application's actual sensor subscription and acceptance behavior have not been checked on that device for this revision. Do not describe this as confirmed compatibility with 云运动.

## Loading a changed native library

The resident native bridge is not replaced by Java hot reload. This revision changes the native library hash, so an already resident older bridge rejects in-process replacement. Installing the APK alone does not make the new native implementation run in that existing process. The target system process must load the new library in a fresh lifetime. The application does not restart it automatically; see [hot reload conditions](HOT_RELOAD.md).
