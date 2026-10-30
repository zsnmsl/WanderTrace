# Pace, speed variation, and synthetic GPS drift

## Pace display

The route start dialog continues to accept speed in m/s. A live readout converts it to minutes and seconds per kilometre (`1000 / speed` seconds). For example, 3 m/s displays **5′33″ /km**. Editing the speed, selecting a movement profile, or changing the randomization percentage updates the readout without starting simulation.

For nonzero speed variation, the dialog also shows the instantaneous pace range corresponding to the configured speed bounds, ordered from fastest to slowest. This is a reference for the randomized portion of a route, not a guarantee for each completed kilometre or for endpoint braking. Zero speed is displayed as stationary rather than an infinite pace; invalid input does not produce a misleading value.

New configurations default to 10% speed randomization. Explicitly saved 0% settings remain constant-speed settings; no existing route is silently changed to a nonzero percentage.

## Speed and distance

The previous curve repeated a 64-target table every 640 seconds. The revised curve combines four seeded, bounded Haar components at different timescales, with independent block amplitudes and phases. Targets are interpolated linearly every ten seconds. The session start time seeds the curve, so a new session differs while all readers of the same configuration remain synchronized.

Full component blocks have zero sums, allowing a closed-form prefix sum and exact trapezoid integration. Distance is derived from the same speed curve, with constant work per query even after long elapsed times. Position does not restart its speed pattern at kilometre markers, and no artificial rule forces adjacent kilometre times to be different. Individual rounded splits may coincide naturally. Non-looping routes retain integrated endpoint braking.

When the start time is absent or zero, route previews stay at the first point rather than treating the Unix epoch as the beginning of a decades-long run. The braking cache includes the session seed.

## GPS drift

The previous offset moved along straight segments between four-second samples. The replacement mixes bounded two-dimensional disk samples at 61, 13, and 2.3 second timescales, weighted 70%, 24%, and 6%. Quintic interpolation avoids abrupt changes in drift velocity at sample boundaries. The resulting offset combines slow bias with smaller short-term variation, and remains within the configured 1–3 metre radius.

This is a deterministic synthetic error model, not a calibrated model of a particular receiver or a prediction of its real accuracy. Real positioning accuracy also depends on satellite geometry, obstruction, atmospheric conditions, and receiver characteristics; see [GPS.gov's accuracy explanation](https://www.gps.gov/gps-accuracy). The configured radius bounds the added offset, not every source of error in a real device.

The model does not modify accelerometer data, vendor health services, the sensor bridge, or application-specific acceptance rules. It does not establish compatibility with 云运动 or any other fitness application.

## Updating and validation

This update changes the Java motion engine and app UI. The native sensor library remains byte-identical to the 2.4.2 step fix. Existing framework and resident-bridge compatibility conditions still apply. Stop and start a route after upgrading the motion model so an active session is not reinterpreted midway through its trajectory.

Checks cover pace conversion and live UI updates; speed bounds and continuity; distance/speed integration; varied kilometre splits; zero-randomness compatibility; session-specific curves; long seeks; bounded continuous drift and smooth drift velocity; and route-end braking. These are local checks, not physical-device validation of GPS behavior or third-party fitness applications.
