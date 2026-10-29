#include "../wander_native.cpp"
int main() {
    void* sensor = dlopen("/system/lib64/libsensorservice.so", RTLD_NOW);
    if (!sensor) { printf("load failed: %s\n", dlerror()); return 1; }
    auto result=install({191,181},{19,18});
    printf("native ABI / hook check in isolated process: %s\n",result.c_str());
    return result == "ready" ? 0 : 1;
}
