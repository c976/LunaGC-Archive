#ifndef HIPA_PATCH_EXPECTED_KEY_H
#define HIPA_PATCH_EXPECTED_KEY_H

static const char EXPECTED_RSA_KEY_VALUE[] =
    "<RSAKeyValue>\r\n"
    "                <Exponent>AQAB</Exponent>\r\n"
    "                <Modulus>\r\n"
    "                  4PMS2JVMwBsOIrYWRluYwEiFZL7Aphtm9z5Eu/anzJ09nB00uhW+ScrDWFECPwpQto/GlOJYCUwVM/raQpAj/xvcjK5tNVzzK94mhk+j9RiQ+aWHaTXmOgurhxSp3YbwlRDvOgcq5yPiTz0+kSeKZJcGeJ95bvJ+hJ/UMP0Zx2qB5PElZmiKvfiNqVUk8A8oxLJdBB5eCpqWV6CUqDKQKSQP4sM0mZvQ1Sr4UcACVcYgYnCbTZMWhJTWkrNXqI8TMomekgny3y+d6NX/cFa66jozFIF4HCX5aW8bp8C8vq2tFvFbleQ/Q3CU56EWWKMrOcpmFtRmC18s9biZBVR/8Q==\r\n"
    "                </Modulus>\r\n"
    "              </RSAKeyValue>";

#define EXPECTED_RSA_KEY_VALUE_LEN ((int)(sizeof(EXPECTED_RSA_KEY_VALUE) - 1))

#endif
