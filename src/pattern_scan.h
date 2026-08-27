#ifndef HIPA_PATCH_PATTERN_SCAN_H
#define HIPA_PATCH_PATTERN_SCAN_H

#include <stddef.h>
#include <stdint.h>

uintptr_t pattern_scan_find(uintptr_t module_base, const char *section_name,
                            const char *pattern);

#endif
