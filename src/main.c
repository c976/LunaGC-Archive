#include <windows.h>
#include <stdio.h>

#include "MinHook.h"

#include "ccp.h"
#include "rsapatch.h"

static void init_console(void) {
    if (GetConsoleWindow() != NULL)
        return;

    if (!AllocConsole())
        return;

    freopen("CONOUT$", "w", stdout);
    freopen("CONOUT$", "w", stderr);

    SetConsoleOutputCP(65001);
    SetConsoleTitleW(L"hipa-patch");

    printf("[hipa-patch] Console initialized.\n");
}

static void disable_vmprotect(void) {
    HMODULE ntdll = GetModuleHandleW(L"ntdll.dll");
    if (ntdll == NULL)
        return;

    void *nt_query_section = (void *)GetProcAddress(ntdll, "NtQuerySection");
    void *nt_protect_virtual_memory =
        (void *)GetProcAddress(ntdll, "NtProtectVirtualMemory");
    if (nt_query_section == NULL || nt_protect_virtual_memory == NULL)
        return;

    unsigned char syscall_num = ((const unsigned char *)nt_query_section)[4];
    const unsigned char patch[] = {
        0x4C, 0x8B, 0xD1,
        0xB8,
        (unsigned char)(syscall_num - 1),
    };

    DWORD old_protect = 0;
    if (!VirtualProtect(nt_protect_virtual_memory, sizeof patch, PAGE_EXECUTE_READWRITE,
                        &old_protect))
        return;

    unsigned char *dest = (unsigned char *)nt_protect_virtual_memory;
    for (size_t i = 0; i < sizeof patch; i++)
        dest[i] = patch[i];

    VirtualProtect(nt_protect_virtual_memory, sizeof patch, old_protect, &old_protect);

    printf("[hipa-patch] VMProtect disabled successfully.\n");
}

static DWORD WINAPI main_thread(LPVOID param) {
    (void)param;

    init_console();
    disable_vmprotect();

    HMODULE main_module = GetModuleHandleW(NULL);
    if (main_module == NULL) {
        printf("[hipa-patch] GetModuleHandleW(NULL) failed.\n");
        return 0;
    }

    if (MH_Initialize() != MH_OK) {
        printf("[hipa-patch] MH_Initialize failed.\n");
        return 0;
    }

    ccp_init();
    rsapatch_init((uintptr_t)main_module);
    printf("[hipa-patch] hipa patch initialized.\n");
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    (void)hinstDLL;
    (void)lpvReserved;

    if (fdwReason == DLL_PROCESS_ATTACH) {
        HANDLE thread = CreateThread(NULL, 0, main_thread, NULL, 0, NULL);
        if (thread == NULL)
            return FALSE;
        CloseHandle(thread);
    }
    return TRUE;
}
