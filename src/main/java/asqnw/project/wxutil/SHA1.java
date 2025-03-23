/**
 * 对公众平台发送给公众账号的消息加解密示例代码.
 *
 * @copyright Copyright (c) 1998-2014 Tencent Inc.
 */

// ------------------------------------------------------------------------

package asqnw.project.wxutil;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * SHA1 class
 * <p>
 * 计算公众平台的消息签名接口.
 */
public class SHA1
{
    public static final String DOMAIN = "https://api.weixin.qq.com";

    /**
     * 用SHA1算法生成安全签名
     *
     * @param token     票据
     * @param timestamp 时间戳
     * @param nonce     随机字符串
     * @return 安全签名
     */
    public static String getSHA1(String token, String timestamp, String nonce)
    {
        try
        {
            String[] array = new String[]{token, timestamp, nonce};
            StringBuilder sb = new StringBuilder();
            // 字符串排序
            Arrays.sort(array);
            for (int i = 0; i < 3; i++)
                sb.append(array[i]);
            String str = sb.toString();
            // SHA1签名生成
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(str.getBytes());
            byte[] digest = md.digest();

            StringBuilder hexStr = new StringBuilder();
            String shaHex;
            for (byte b : digest)
            {
                shaHex = Integer.toHexString(b & 0xFF);
                if (shaHex.length() < 2)
                    hexStr.append(0);
                hexStr.append(shaHex);
            }
            return hexStr.toString();
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
