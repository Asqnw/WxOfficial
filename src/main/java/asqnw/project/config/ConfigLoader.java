package asqnw.project.config;

import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
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

    public String[] getConfigs(XPath xPath, Document document)
    {
        String[] result = new String[3];
        this.properties.forEach((key, value) -> {
            String keyStr = (String) key;
            try
            {
                String MsgType;
                String baseKey;
                if (keyStr.endsWith(".MsgType") && xPath.evaluate("/xml/MsgType/text()", document, XPathConstants.STRING).equals(MsgType = this.getProperty(keyStr)))
                {
                    baseKey = keyStr.substring(0, keyStr.lastIndexOf(".MsgType"));
                    if (MsgType.equals("event"))
                    {
                        String event = (String) xPath.evaluate("/xml/Event/text()", document, XPathConstants.STRING);
                        if (this.getProperty(baseKey + ".Event").equals(event))
                        {
                            switch (event)
                            {
                                case "subscribe", "unsubscribe" -> {//订阅/取消订阅用户
                                    result[0] = this.getProperty(baseKey + ".Type");
                                    if (result[0].equals("proxyText"))
                                        result[2] = this.getProperty(baseKey + ".Proxy");
                                    result[1] = this.getProperty(baseKey + ".Msg");
                                }
                                case "CLICK" -> {//点击菜单选项
                                    String eventKey = (String) xPath.evaluate("/xml/EventKey/text()", document, XPathConstants.STRING);
                                    if (this.getProperty(baseKey + ".EventKey").equals(eventKey))
                                    {
                                        result[0] = this.getProperty(baseKey + ".Type");
                                        if (result[0].equals("proxyText"))
                                            result[2] = this.getProperty(baseKey + ".Proxy");
                                        result[1] = this.getProperty(baseKey + ".Msg");
                                    }
                                }
                                case "SCAN" -> {//扫码
                                    String eventKey = (String) xPath.evaluate("/xml/EventKey/text()", document, XPathConstants.STRING);
                                    if (Pattern.matches(this.getProperty(baseKey + ".EventKey"), eventKey))
                                    {
                                        result[0] = this.getProperty(baseKey + ".Type");
                                        if (result[0].equals("proxyText"))
                                            result[2] = this.getProperty(baseKey + ".Proxy");
                                        result[1] = this.getProperty(baseKey + ".Msg");
                                    }
                                }
                            }
                        }
                    }
                    else if (MsgType.equals("text"))//发送消息
                    {
                        String content = (String) xPath.evaluate("/xml/Content/text()", document, XPathConstants.STRING);
                        if (Pattern.matches(this.getProperty(baseKey + ".Content"), content))
                        {
                            result[0] = this.getProperty(baseKey + ".Type");
                            if (result[0].equals("proxyText"))
                                result[2] = this.getProperty(baseKey + ".Proxy");
                            result[1] = this.getProperty(baseKey + ".Msg");
                        }
                    }
                }
            }
            catch (XPathExpressionException ignored)
            {}
        });
        return result;
    }
}
