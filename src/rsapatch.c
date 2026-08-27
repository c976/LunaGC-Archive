#include "rsapatch.h"

#include <string.h>
#include <windows.h>

#include "MinHook.h"
#include "expected_key.h"
#include "il2cpp.h"
#include "pattern_scan.h"
#include "server_der.h"

static const char RSA_ENCRYPT_SECTION[] = "il2cpp";
static const char RSA_ENCRYPT_PATTERN[] =
    "41 57 41 56 41 55 41 54 56 57 55 53 48 83 EC 78 49 89 D6 48 89 CE 48 8B "
    "0D 53 8A FB EC";
static const char ASN1_ITEM_D2I_SECTION[] = ".text";
static const char ASN1_ITEM_D2I_PATTERN[] =
    "41 57 41 56 41 55 41 54 56 57 55 53 48 81 EC ?? ?? ?? ?? 48 89 CF 44 8B "
    "AC 24 20 01 00 00";

typedef void *(*asn1_item_d2i_t)(void **, const unsigned char **, long,
                                 const void *);
static asn1_item_d2i_t g_orig_asn1 = NULL;

static void *detour_asn1_item_d2i(void **val, const unsigned char **in,
                                  long len, const void *it) {
  static const unsigned char MOD_FP[8] = {0x94, 0x2c, 0x1d, 0x62,
                                          0xbb, 0xde, 0xa3, 0x36};
  if (in != NULL && *in != NULL && memcmp(*in + 9, MOD_FP, 8) == 0) {
    *in = SERVER_DER;
    len = (long)SERVER_DER_LEN;
  }
  return g_orig_asn1(val, in, len, it);
}

typedef void *(*rsa_encrypt_t)(void *, void *, void *, void *);
static rsa_encrypt_t g_orig_rsa_encrypt = NULL;

static void *detour_rsa_encrypt(void *rcx, void *rdx, void *r8, void *r9) {
  if (rcx == NULL)
    return g_orig_rsa_encrypt(rcx, rdx, r8, r9);

  const Il2CppString *str = (const Il2CppString *)rcx;
  if (str->length != EXPECTED_RSA_KEY_VALUE_LEN)
    return g_orig_rsa_encrypt(rcx, rdx, r8, r9);

  const uint16_t *chars = il2cpp_string_chars(str);
  for (int i = 0; i < EXPECTED_RSA_KEY_VALUE_LEN; i++) {
    if (chars[i] != (uint16_t)(unsigned char)EXPECTED_RSA_KEY_VALUE[i])
      return g_orig_rsa_encrypt(rcx, rdx, r8, r9);
  }
  return rdx;
}

static void install(const char *name, void *target, void *detour, void **orig) {
  if (target == NULL)
    return;
  if (MH_CreateHook(target, detour, orig) != MH_OK)
    return;
  MH_EnableHook(target);
  (void)name;
}

void rsapatch_init(uintptr_t module_base) {
  uintptr_t asn1 = pattern_scan_find(module_base, ASN1_ITEM_D2I_SECTION,
                                     ASN1_ITEM_D2I_PATTERN);
  install("ASN1_ITEM_d2i", (void *)asn1, (void *)&detour_asn1_item_d2i,
          (void **)&g_orig_asn1);

  uintptr_t rsa =
      pattern_scan_find(module_base, RSA_ENCRYPT_SECTION, RSA_ENCRYPT_PATTERN);
  install("RSA encrypt", (void *)rsa, (void *)&detour_rsa_encrypt,
          (void **)&g_orig_rsa_encrypt);
}
