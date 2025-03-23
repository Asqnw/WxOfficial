package asqnw.project.config;

import lombok.Data;
import org.json.JSONObject;
import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Author 暗影之风
 * @CreateTime 2024-03-31 22:35:56
 * @Description 配置文件
 */
@SuppressWarnings("JavadocDeclaration")
public class ConfigLoader
{
    private final Properties properties;

    public ConfigLoader(String fileName) throws IOException
    {
        this.properties = new Properties();
        try (FileInputStream fis = new FileInputStream(fileName))
        {
            this.properties.load(fis);
        }
    }

    public String getProperty(String key)
    {
        return new String(this.properties.getProperty(key).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    public Config getConfigs(String template, XPath xPath, Document document)
    {
        String[] result = new String[5];
        Config config = new Config();
        this.properties.forEach((key, value) -> {
            String keyStr = (String) key;
            try
            {
                String MsgType;
                String baseKey;
                if (keyStr.endsWith(".MsgType") && ((template != null && "template".equals(MsgType = this.getProperty(keyStr))) || (xPath != null && document != null && xPath.evaluate("/xml/MsgType/text()", document, XPathConstants.STRING).equals(MsgType = this.getProperty(keyStr)))))
                {
                    baseKey = keyStr.substring(0, keyStr.lastIndexOf(".MsgType"));
                    if (MsgType.equals("event"))
                    {
                        config.setText(false);
                        String event = (String) xPath.evaluate("/xml/Event/text()", document, XPathConstants.STRING);
                        if (this.getProperty(baseKey + ".Event").equals(event))
                        {
                            switch (event)
                            {
                                case "subscribe", "unsubscribe" ->//订阅/取消订阅用户
                                    setValue(result, baseKey);
                                case "CLICK" -> {//点击菜单选项
                                    String eventKey = (String) xPath.evaluate("/xml/EventKey/text()", document, XPathConstants.STRING);
                                    if (this.getProperty(baseKey + ".EventKey").equals(eventKey))
                                        setValue(result, baseKey);
                                }
                                case "SCAN" -> {//扫码
                                    String eventKey = (String) xPath.evaluate("/xml/EventKey/text()", document, XPathConstants.STRING);
                                    if (Pattern.matches(this.getProperty(baseKey + ".EventKey"), eventKey))
                                        setValue(result, baseKey);
                                }
                            }
                        }
                    }
                    else if (MsgType.equals("text"))//发送消息
                    {
                        config.setText(true);
                        String content = (String) xPath.evaluate("/xml/Content/text()", document, XPathConstants.STRING);
                        if (Pattern.matches(this.getProperty(baseKey + ".Content"), content))
                            setValue(result, baseKey);
                    }
                    else if (MsgType.equals("template"))//模板消息
                    {
                        if (this.getProperty(baseKey + ".Content").equals(template))
                        {
                            config.setText(false);
                            String templateConfig = this.getProperty(baseKey + ".templateKeyMapping");
                            if (!templateConfig.isEmpty())
                            {
                                JSONObject templateJson = new JSONObject(templateConfig);
                                Set<String> keys = templateJson.keySet();
                                String[] tConfig = new String[keys.size() * 2];
                                int index = 0;
                                for (String k : keys)
                                {
                                    tConfig[index++] = k;
                                    tConfig[index++] = templateJson.getString(k);
                                }
                                result[0] = Arrays.toString(tConfig);
                            }
                            else
                                result[0] = "";
                        }
                    }
                }
            }
            catch (XPathExpressionException ignored)
            {}
        });
        config.setConfig(result);
        return config;
    }

    private void setValue(String[] result, String baseKey)
    {
        result[0] = this.getProperty(baseKey + ".Type");
        if (result[0].equals("proxyText"))
        {
            result[2] = this.getProperty(baseKey + ".Proxy");
            result[3] = this.getProperty(baseKey + ".ProxyResponse");
        }
        result[1] = this.getProperty(baseKey + ".Msg");
        if (result[0].equals("urlText"))
        {
            result[2] = this.getProperty(baseKey + ".urlTextHint");
            result[3] = this.getProperty(baseKey + ".urlTextSend");
            result[4] = this.getProperty(baseKey + ".urlTextResponse");
        }
    }

    @Data
    public static class Config
    {
        private String[] config;
        private boolean isText;
    }
}
