#include "ccp.h"

#include <winsock2.h>
#include <windows.h>
#include <string.h>

#include "MinHook.h"

typedef int(WSAAPI *getaddrinfo_t)(const char *, const char *, const void *, void *);
static getaddrinfo_t g_orig_getaddrinfo = NULL;

static const char *const BLOCKED_HOSTS[] = {
    "dispatchosglobal.yuanshen.com",
    "dispatchcnglobal.yuanshen.com",
};

static int WSAAPI detour_getaddrinfo(const char *node, const char *service,
                                     const void *hints, void *result) {
    for (size_t i = 0; node && i < sizeof BLOCKED_HOSTS / sizeof *BLOCKED_HOSTS; i++) {
        if (strcmp(node, BLOCKED_HOSTS[i]) == 0) {
            node = "0.0.0.0";
            break;
        }
    }
    return g_orig_getaddrinfo(node, service, hints, result);
}

void ccp_init(void) {
    HMODULE ws2 = NULL;
    for (int i = 0; (ws2 = GetModuleHandleA("Ws2_32.dll")) == NULL && i < 150; i++)
        Sleep(200);
    if (ws2 == NULL)
        return;

    void *target = (void *)GetProcAddress(ws2, "getaddrinfo");
    if (target == NULL)
        return;
    if (MH_CreateHook(target, (void *)&detour_getaddrinfo,
                      (void **)&g_orig_getaddrinfo) == MH_OK)
        MH_EnableHook(target);
}
