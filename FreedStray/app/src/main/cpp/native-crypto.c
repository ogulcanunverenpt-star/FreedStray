#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "NativeCrypto"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* --- Standalone AES-256 implementation --- */

static const uint8_t sbox[256] = {
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
};

static const uint8_t Rcon[11] = {
    0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36
};

typedef struct {
    uint32_t erk[60];
    uint32_t drk[60];
    int nr;
} aes256_ctx;

static void aes256_key_expand(aes256_ctx *ctx, const uint8_t *key) {
    ctx->nr = 14;
    for (int i = 0; i < 8; i++) {
        ctx->erk[i] = ((uint32_t)key[4*i] << 24) | ((uint32_t)key[4*i+1] << 16) |
                      ((uint32_t)key[4*i+2] << 8) | ((uint32_t)key[4*i+3]);
    }
    for (int i = 8; i < 60; i++) {
        uint32_t temp = ctx->erk[i - 1];
        if (i % 8 == 0) {
            temp = ((uint32_t)sbox[(temp >> 16) & 0xFF] << 24) ^
                   ((uint32_t)sbox[(temp >> 8) & 0xFF] << 16) ^
                   ((uint32_t)sbox[temp & 0xFF] << 8) ^
                   ((uint32_t)sbox[(temp >> 24) & 0xFF]) ^
                   ((uint32_t)Rcon[i / 8] << 24);
        } else if (i % 8 == 4) {
            temp = ((uint32_t)sbox[(temp >> 24) & 0xFF] << 24) ^
                   ((uint32_t)sbox[(temp >> 16) & 0xFF] << 16) ^
                   ((uint32_t)sbox[(temp >> 8) & 0xFF] << 8) ^
                   ((uint32_t)sbox[temp & 0xFF]);
        }
        ctx->erk[i] = ctx->erk[i - 8] ^ temp;
    }
}

static inline uint8_t g2(uint8_t x) {
    return (x & 0x80) ? ((x << 1) ^ 0x1b) : (x << 1);
}

static void aes256_encrypt_block(const aes256_ctx *ctx, const uint8_t in[16], uint8_t out[16]) {
    uint8_t state[4][4];
    for (int i = 0; i < 16; i++) state[i % 4][i / 4] = in[i];

    // AddRoundKey 0
    for (int r = 0; r < 4; r++) {
        uint32_t k = ctx->erk[r];
        state[0][r] ^= (k >> 24) & 0xFF;
        state[1][r] ^= (k >> 16) & 0xFF;
        state[2][r] ^= (k >> 8) & 0xFF;
        state[3][r] ^= k & 0xFF;
    }

    for (int round = 1; round <= 14; round++) {
        // SubBytes
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                state[i][j] = sbox[state[i][j]];

        // ShiftRows
        uint8_t tmp;
        tmp = state[1][0]; state[1][0] = state[1][1]; state[1][1] = state[1][2]; state[1][2] = state[1][3]; state[1][3] = tmp;
        tmp = state[2][0]; uint8_t tmp2 = state[2][1]; state[2][0] = state[2][2]; state[2][1] = state[2][3]; state[2][2] = tmp; state[2][3] = tmp2;
        tmp = state[3][3]; state[3][3] = state[3][2]; state[3][2] = state[3][1]; state[3][1] = state[3][0]; state[3][0] = tmp;

        // MixColumns (Skip for round 14)
        if (round < 14) {
            for (int c = 0; c < 4; c++) {
                uint8_t a0 = state[0][c], a1 = state[1][c], a2 = state[2][c], a3 = state[3][c];
                state[0][c] = g2(a0) ^ (g2(a1) ^ a1) ^ a2 ^ a3;
                state[1][c] = a0 ^ g2(a1) ^ (g2(a2) ^ a2) ^ a3;
                state[2][c] = a0 ^ a1 ^ g2(a2) ^ (g2(a3) ^ a3);
                state[3][c] = (g2(a0) ^ a0) ^ a1 ^ a2 ^ g2(a3);
            }
        }

        // AddRoundKey
        for (int r = 0; r < 4; r++) {
            uint32_t k = ctx->erk[round * 4 + r];
            state[0][r] ^= (k >> 24) & 0xFF;
            state[1][r] ^= (k >> 16) & 0xFF;
            state[2][r] ^= (k >> 8) & 0xFF;
            state[3][r] ^= k & 0xFF;
        }
    }

    for (int i = 0; i < 16; i++) out[i] = state[i % 4][i / 4];
}

/* --- Galois Hash (GHASH) for AES-GCM --- */
static void ghash_mult(const uint8_t X[16], const uint8_t H[16], uint8_t Z[16]) {
    uint8_t V[16];
    memcpy(V, H, 16);
    memset(Z, 0, 16);

    for (int i = 0; i < 128; i++) {
        if ((X[i / 8] >> (7 - (i % 8))) & 1) {
            for (int j = 0; j < 16; j++) Z[j] ^= V[j];
        }
        int lsb = V[15] & 1;
        for (int j = 15; j > 0; j--) {
            V[j] = (V[j] >> 1) | ((V[j - 1] & 1) << 7);
        }
        V[0] >>= 1;
        if (lsb) V[0] ^= 0xe1; // R polynomial for GCM
    }
}

/* --- AES-256-GCM Encrypt Function --- */
static void aes256_gcm_encrypt(
    const uint8_t *key,
    const uint8_t *iv, size_t iv_len,
    const uint8_t *pt, size_t pt_len,
    uint8_t *ct,
    uint8_t tag[16]
) {
    aes256_ctx ctx;
    aes256_key_expand(&ctx, key);

    uint8_t H[16] = {0};
    uint8_t zero[16] = {0};
    aes256_encrypt_block(&ctx, zero, H);

    uint8_t J0[16] = {0};
    if (iv_len == 12) {
        memcpy(J0, iv, 12);
        J0[15] = 1;
    } else {
        // GHASH for non-12-byte IV
        uint8_t block[16] = {0};
        for (size_t i = 0; i < iv_len; i += 16) {
            size_t chunk = (iv_len - i < 16) ? (iv_len - i) : 16;
            memset(block, 0, 16);
            memcpy(block, iv + i, chunk);
            for (int j = 0; j < 16; j++) J0[j] ^= block[j];
            uint8_t tmp[16];
            ghash_mult(J0, H, tmp);
            memcpy(J0, tmp, 16);
        }
    }

    uint8_t counter[16];
    memcpy(counter, J0, 16);

    // CTR mode encryption
    for (size_t i = 0; i < pt_len; i += 16) {
        // inc32 counter
        uint32_t val = (counter[12] << 24) | (counter[13] << 16) | (counter[14] << 8) | counter[15];
        val++;
        counter[12] = (val >> 24) & 0xFF;
        counter[13] = (val >> 16) & 0xFF;
        counter[14] = (val >> 8) & 0xFF;
        counter[15] = val & 0xFF;

        uint8_t mask[16];
        aes256_encrypt_block(&ctx, counter, mask);

        size_t chunk = (pt_len - i < 16) ? (pt_len - i) : 16;
        for (size_t j = 0; j < chunk; j++) {
            ct[i + j] = pt[i + j] ^ mask[j];
        }
    }

    // GHASH over Ciphertext
    uint8_t S[16] = {0};
    for (size_t i = 0; i < pt_len; i += 16) {
        uint8_t block[16] = {0};
        size_t chunk = (pt_len - i < 16) ? (pt_len - i) : 16;
        memcpy(block, ct + i, chunk);
        for (int j = 0; j < 16; j++) S[j] ^= block[j];
        uint8_t tmp[16];
        ghash_mult(S, H, tmp);
        memcpy(S, tmp, 16);
    }

    // Length block (AAD len 0, CT len in bits)
    uint8_t len_block[16] = {0};
    uint64_t ct_bits = (uint64_t)pt_len * 8;
    for (int i = 0; i < 8; i++) len_block[15 - i] = (ct_bits >> (i * 8)) & 0xFF;

    for (int j = 0; j < 16; j++) S[j] ^= len_block[j];
    uint8_t tmp[16];
    ghash_mult(S, H, tmp);

    // Tag = S ^ E_K(J0)
    uint8_t ekJ0[16];
    aes256_encrypt_block(&ctx, J0, ekJ0);
    for (int j = 0; j < 16; j++) tag[j] = tmp[j] ^ ekJ0[j];
}

/* --- AES-256-GCM Decrypt Function --- */
static int aes256_gcm_decrypt(
    const uint8_t *key,
    const uint8_t *iv, size_t iv_len,
    const uint8_t *ct, size_t ct_len,
    const uint8_t tag[16],
    uint8_t *pt
) {
    aes256_ctx ctx;
    aes256_key_expand(&ctx, key);

    uint8_t H[16] = {0};
    uint8_t zero[16] = {0};
    aes256_encrypt_block(&ctx, zero, H);

    uint8_t J0[16] = {0};
    if (iv_len == 12) {
        memcpy(J0, iv, 12);
        J0[15] = 1;
    }

    // GHASH over Ciphertext
    uint8_t S[16] = {0};
    for (size_t i = 0; i < ct_len; i += 16) {
        uint8_t block[16] = {0};
        size_t chunk = (ct_len - i < 16) ? (ct_len - i) : 16;
        memcpy(block, ct + i, chunk);
        for (int j = 0; j < 16; j++) S[j] ^= block[j];
        uint8_t tmp[16];
        ghash_mult(S, H, tmp);
        memcpy(S, tmp, 16);
    }

    uint8_t len_block[16] = {0};
    uint64_t ct_bits = (uint64_t)ct_len * 8;
    for (int i = 0; i < 8; i++) len_block[15 - i] = (ct_bits >> (i * 8)) & 0xFF;

    for (int j = 0; j < 16; j++) S[j] ^= len_block[j];
    uint8_t tmp[16];
    ghash_mult(S, H, tmp);

    uint8_t ekJ0[16];
    aes256_encrypt_block(&ctx, J0, ekJ0);
    uint8_t calc_tag[16];
    for (int j = 0; j < 16; j++) calc_tag[j] = tmp[j] ^ ekJ0[j];

    // Verify Auth Tag
    if (memcmp(calc_tag, tag, 16) != 0) {
        LOGE("Tag verification failed!");
        return -1;
    }

    // Decrypt CTR mode
    uint8_t counter[16];
    memcpy(counter, J0, 16);

    for (size_t i = 0; i < ct_len; i += 16) {
        uint32_t val = (counter[12] << 24) | (counter[13] << 16) | (counter[14] << 8) | counter[15];
        val++;
        counter[12] = (val >> 24) & 0xFF;
        counter[13] = (val >> 16) & 0xFF;
        counter[14] = (val >> 8) & 0xFF;
        counter[15] = val & 0xFF;

        uint8_t mask[16];
        aes256_encrypt_block(&ctx, counter, mask);

        size_t chunk = (ct_len - i < 16) ? (ct_len - i) : 16;
        for (size_t j = 0; j < chunk; j++) {
            pt[i + j] = ct[i + j] ^ mask[j];
        }
    }

    return 0;
}

/* --- JNI Exported Functions --- */

JNIEXPORT jbyteArray JNICALL
Java_com_godot_freedstray_crypto_NativeCryptoBridge_encryptNative(
    JNIEnv *env,
    jobject instance,
    jbyteArray keyArray,
    jbyteArray plainArray
) {
    if (!keyArray || !plainArray) return NULL;

    jsize keyLen = (*env)->GetArrayLength(env, keyArray);
    jsize plainLen = (*env)->GetArrayLength(env, plainArray);

    if (keyLen != 32) {
        LOGE("Key length must be 32 bytes (256 bits)");
        return NULL;
    }

    jbyte *key = (*env)->GetByteArrayElements(env, keyArray, NULL);
    jbyte *plain = (*env)->GetByteArrayElements(env, plainArray, NULL);

    // Generate 12 bytes IV
    uint8_t iv[12];
    for (int i = 0; i < 12; i++) iv[i] = (uint8_t)(rand() % 256);

    uint8_t *ct = (uint8_t *)malloc(plainLen);
    uint8_t tag[16];

    aes256_gcm_encrypt((uint8_t *)key, iv, 12, (uint8_t *)plain, plainLen, ct, tag);

    // Result buffer: IV (12) + CT (plainLen) + TAG (16)
    jsize resultLen = 12 + plainLen + 16;
    jbyteArray resultArray = (*env)->NewByteArray(env, resultLen);

    (*env)->SetByteArrayRegion(env, resultArray, 0, 12, (jbyte *)iv);
    (*env)->SetByteArrayRegion(env, resultArray, 12, plainLen, (jbyte *)ct);
    (*env)->SetByteArrayRegion(env, resultArray, 12 + plainLen, 16, (jbyte *)tag);

    free(ct);
    (*env)->ReleaseByteArrayElements(env, keyArray, key, 0);
    (*env)->ReleaseByteArrayElements(env, plainArray, plain, 0);

    return resultArray;
}

JNIEXPORT jbyteArray JNICALL
Java_com_godot_freedstray_crypto_NativeCryptoBridge_decryptNative(
    JNIEnv *env,
    jobject instance,
    jbyteArray keyArray,
    jbyteArray encryptedArray
) {
    if (!keyArray || !encryptedArray) return NULL;

    jsize keyLen = (*env)->GetArrayLength(env, keyArray);
    jsize encLen = (*env)->GetArrayLength(env, encryptedArray);

    if (keyLen != 32 || encLen < 12 + 16) {
        LOGE("Invalid key length or encrypted data format");
        return NULL;
    }

    jbyte *key = (*env)->GetByteArrayElements(env, keyArray, NULL);
    jbyte *enc = (*env)->GetByteArrayElements(env, encryptedArray, NULL);

    uint8_t iv[12];
    memcpy(iv, enc, 12);

    jsize ctLen = encLen - 12 - 16;
    uint8_t *ct = (uint8_t *)(enc + 12);
    uint8_t *tag = (uint8_t *)(enc + 12 + ctLen);

    uint8_t *pt = (uint8_t *)malloc(ctLen);

    int res = aes256_gcm_decrypt((uint8_t *)key, iv, 12, ct, ctLen, tag, pt);

    (*env)->ReleaseByteArrayElements(env, keyArray, key, 0);
    (*env)->ReleaseByteArrayElements(env, encryptedArray, enc, 0);

    if (res != 0) {
        free(pt);
        return NULL;
    }

    jbyteArray plainArray = (*env)->NewByteArray(env, ctLen);
    (*env)->SetByteArrayRegion(env, plainArray, 0, ctLen, (jbyte *)pt);

    free(pt);
    return plainArray;
}
