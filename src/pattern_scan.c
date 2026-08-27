#include "pattern_scan.h"

#include <string.h>

typedef struct {
    uint8_t value;
    uint8_t is_wildcard;
} PatByte;

#define PATTERN_MAX 256

static size_t parse_pattern(const char *pattern, PatByte *out, size_t out_cap) {
    size_t n = 0;
    const char *p = pattern;
    while (*p) {
        while (*p == ' ')
            p++;
        if (!*p)
            break;
        if (n >= out_cap)
            return 0;
        if (p[0] == '?') {
            out[n].is_wildcard = 1;
            out[n].value = 0;
            n++;
            p++;
            if (*p == '?')
                p++;
        } else {

            uint8_t hi, lo;
            if (!p[0] || !p[1])
                return 0;
            for (int k = 0; k < 2; k++) {
                char c = p[k];
                uint8_t v;
                if (c >= '0' && c <= '9')
                    v = (uint8_t)(c - '0');
                else if (c >= 'a' && c <= 'f')
                    v = (uint8_t)(c - 'a' + 10);
                else if (c >= 'A' && c <= 'F')
                    v = (uint8_t)(c - 'A' + 10);
                else
                    return 0;
                if (k == 0)
                    hi = v;
                else
                    lo = v;
            }
            out[n].is_wildcard = 0;
            out[n].value = (uint8_t)((hi << 4) | lo);
            n++;
            p += 2;
        }
    }
    return n;
}

static uint16_t read_u16(uintptr_t addr) {
    uint16_t v;
    memcpy(&v, (const void *)addr, sizeof v);
    return v;
}

static uint32_t read_u32(uintptr_t addr) {
    uint32_t v;
    memcpy(&v, (const void *)addr, sizeof v);
    return v;
}

static int find_section(uintptr_t module_base, const char *name, uintptr_t *out_start,
                        uintptr_t *out_size) {
    if (read_u16(module_base) != 0x5A4D)
        return 0;
    uintptr_t nt = module_base + read_u32(module_base + 0x3C);
    if (read_u32(nt) != 0x00004550)
        return 0;

    uintptr_t file_header = nt + 4;
    uint16_t num_sections = read_u16(file_header + 2);
    uint16_t opt_header_size = read_u16(file_header + 16);
    uintptr_t sec = file_header + 20 + opt_header_size;

    size_t name_len = strlen(name);
    for (uint16_t i = 0; i < num_sections; i++) {
        const char *sec_name = (const char *)sec;

        int match = 1;
        for (size_t k = 0; k < 8; k++) {
            char want = (k < name_len) ? name[k] : '\0';
            if (sec_name[k] != want) {
                match = 0;
                break;
            }
        }
        if (match) {
            *out_size = read_u32(sec + 8);
            *out_start = module_base + read_u32(sec + 12);
            return 1;
        }
        sec += 40;
    }
    return 0;
}

static int matches_at(uintptr_t addr, const PatByte *pat, size_t len) {
    const uint8_t *mem = (const uint8_t *)addr;
    for (size_t j = 0; j < len; j++) {
        if (!pat[j].is_wildcard && mem[j] != pat[j].value)
            return 0;
    }
    return 1;
}

uintptr_t pattern_scan_find(uintptr_t module_base, const char *section_name,
                            const char *pattern) {
    PatByte pat[PATTERN_MAX];
    size_t len = parse_pattern(pattern, pat, PATTERN_MAX);
    if (len == 0)
        return 0;

    uintptr_t start, size;
    if (!find_section(module_base, section_name, &start, &size))
        return 0;
    if (len > size)
        return 0;

    uintptr_t last = start + size - len;
    for (uintptr_t addr = start; addr <= last; addr++) {
        if (matches_at(addr, pat, len))
            return addr;
    }
    return 0;
}
