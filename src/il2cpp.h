#ifndef HIPA_PATCH_IL2CPP_H
#define HIPA_PATCH_IL2CPP_H

#include <stdint.h>

typedef struct Il2CppString {
    uintptr_t klass;
    uintptr_t monitor;
    int32_t length;

} Il2CppString;

static inline const uint16_t *il2cpp_string_chars(const Il2CppString *s) {
    return (const uint16_t *)((const uint8_t *)s + 0x14);
}

#endif
