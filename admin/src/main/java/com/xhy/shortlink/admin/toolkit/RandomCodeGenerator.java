package com.xhy.shortlink.admin.toolkit;

import java.util.Random;

public class RandomCodeGenerator {
    // 定义字符集（包含大小写字母和数字）
    private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成指定长度的随机字符串
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    public static String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            // 随机选择字符集中的一个字符
            int index = random.nextInt(CHAR_SET.length());
            sb.append(CHAR_SET.charAt(index));
        }

        return sb.toString();
    }

    /**
     * 生成 6 位随机英文或数字字符串
     *
     * @return 6 位随机字符串
     */
    public static String generateSixDigitCode() {
        return generateRandomCode(6);
    }
}
