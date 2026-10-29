# Native dependency

Dobby: https://github.com/jmpews/Dobby

Pinned source revision: `67b8367da581b86ef58c4692a241bc20c352b711`.
The unmodified source and its Apache-2.0 license are included in `vendor/Dobby`.
The compiled hook library is statically linked into `libwander_native.so`.

The sensor wire layout and interface names are compatible with Android Open Source Project
SensorService / BitTube. Runtime ABI checks reject unsupported layouts before installing hooks.
No Android mock location provider or sensor data injection mode is used.
