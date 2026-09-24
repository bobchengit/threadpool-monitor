package io.itbob.threadpool.monitor.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES/GCM/NoPadding 加密器，用于数据源鉴权密钥的落库加密。
 * 密文格式：Base64(iv[12] + cipherText + tag[16])
 * 密钥来源：monitor.crypto.key（至少 16 字符，取前 16 字节作为 128 位密钥），
 * 可通过环境变量 MONITOR_CRYPTO_KEY 覆盖。
 */
@Component
public class AesGcmEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(@Value("${monitor.crypto.key}") String keyStr) {
        byte[] keyBytes = keyStr == null ? new byte[0] : keyStr.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 16) {
            throw new IllegalStateException("monitor.crypto.key 配置长度不足：至少需要 16 个字符");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");
    }

    /**
     * 加密；空值原样返回
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("鉴权密钥加密失败", e);
        }
    }

    /**
     * 解密；空值原样返回
     */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("鉴权密钥解密失败，请检查 monitor.crypto.key 是否变更", e);
        }
    }
}
