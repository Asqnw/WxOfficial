package asqnw.project;

import asqnw.project.config.ConfigLoader;
import asqnw.project.http.HttpClient;
import asqnw.project.http.HttpServer;
import asqnw.project.wxutil.SHA1;
import asqnw.project.wxutil.TokenManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main
{
    public static String APPID;
    public static String APP_SECRET;
    public static String TOKEN;
    public static String CRT_FILE;
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static final int ONCE_MESSAGE_LIMIT = 5;
    private static ConfigLoader configLoader;
    private static final Object[] urlTextSendArrayWait = new Object[]{false, false, 0};//是否需要等待，需要发送等待，剩余发送次数

    public static void main(String[] args)
    {
        int port = 0;
        ArrayList<String> WX_IPS = new ArrayList<>();
        System.out.println("欢迎使用影幽网络工作室产品--微信公众号服务器\n\n现在准备启动必须内容，请稍等");
        System.out.println("一、设置服务器端口");
        for (int i = 0; i < args.length; i++)
        {
            if (args[i].startsWith("-port"))
            {
                try
                {
                    port = Integer.parseInt(args[i + 1]);
                }
                catch (Exception e)
                {
                    System.out.println("1.1读取端口参数异常，使用随机端口");
                }
            }
        }
        if (port == 0)
            System.out.println("1.1未设置端口参数，使用随机端口");

        System.out.println("二、加载配置文件");
        try
        {
            configLoader = new ConfigLoader("config.properties");
            APPID = configLoader.getProperty("APPID");
            System.out.println("APPID：" + APPID.substring(0, 5) + "***");
            APP_SECRET = configLoader.getProperty("APPSECRET");
            System.out.println("APPSECRET：" + APP_SECRET.substring(0, 6) + "***");
            TOKEN = configLoader.getProperty("TOKEN");
            CRT_FILE = Optional.ofNullable(configLoader.getProperty("CRT_FILE")).orElse("");
            System.out.println("TOKEN：" + TOKEN.substring(0, 8) + "***");
        }
        catch (IOException e)
        {
            System.out.println("2.1配置文件不存在，退出执行");
            System.exit(0);
        }
        catch (StringIndexOutOfBoundsException ignored)
        {
            System.out.println("2.1配置文件设置异常，退出执行");
            System.exit(0);
        }

        TokenManager.instance.start();
        System.out.println("三、获取微信服务器IP组");
        System.out.println("3.1获取公众号ACCESS_TOKEN");
        try
        {
            HttpClient httpClient = new HttpClient();
            System.out.println("3.1获取ACCESS_TOKEN成功(为了某些情况下安全，只显示部分)：" + (TokenManager.instance.getAccessToken().substring(0, 10)) + "***");
            System.out.println("3.2准备获取微信服务器的IP组");
            JSONArray array = new JSONObject(httpClient.getReqStr(SHA1.DOMAIN + "/cgi-bin/get_api_domain_ip?access_token=" + TokenManager.instance.getAccessToken())).getJSONArray("ip_list");
            for (int i = 0; i < array.length(); i++)
                WX_IPS.add(array.getString(i));
            System.out.println("3.2获取微信服务器IP组成功，数量：" + WX_IPS.size());
        }
        catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError | JSONException ignored)
        {
            System.out.println("获取失败，程序无法继续运行");
            System.exit(0);
        }

        System.out.println("准备完成，启动服务器");
        new HttpServer().start(request -> {
            HashMap<String, String> response = new HashMap<>();
            if (request.get(HttpServer.URL).get(0).equals("/auth"))
            {
//                String userIp = HttpServer.findHeader(request.get(HttpServer.HEADER), "x-forwarded-for");//这里无需担心该请求头伪造问题，在Nginx中已经处理
                String body;
                System.out.println(request.toString().replaceAll("\n", "\\\\n"));
                ArrayList<String> postBody = request.get(HttpServer.BODY);
                if ((body = (postBody == null ? "" : postBody.get(0))).isEmpty())//GET请求
                {
                    String[] state = checkSign(request);
                    switch (state[0])
                    {
                        case "0", "1" -> response.put("200 OK", state[1]);
                        case "2" -> response.put("400 Bad Request", state[1]);
                        default -> response.put("", "");
                    }
                }
                else
                {
                    String[] state = checkSign(request);
                    if (state[0].equals("0"))
                    {
                        try
                        {
                            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(body)));
                            XPath xpath = XPathFactory.newInstance().newXPath();
                            String toUserName = (String) xpath.evaluate("/xml/ToUserName/text()", document, XPathConstants.STRING);
                            String fromUserName = (String) xpath.evaluate("/xml/FromUserName/text()", document, XPathConstants.STRING);
                            String MsgId = (String) xpath.evaluate("/xml/MsgId/text()", document, XPathConstants.STRING);
                            String CreateTime = (String) xpath.evaluate("/xml/CreateTime/text()", document, XPathConstants.STRING);
                            ConfigLoader.Config configC = configLoader.getConfigs(null, xpath, document);
                            if (configC.getConfig()[0] == null)
                                throw new IOException();
                            AtomicReference<String> ars = new AtomicReference<>();
                            if (configC.isText())
                                ars.set((String) xpath.evaluate("/xml/Content/text()", document, XPathConstants.STRING));
                            String[] config = configC.getConfig();
                            String type = config[0];
                            switch (type)
                            {
                                case "text" ->
                                    response.put("200 OK", "<xml><ToUserName><![CDATA[" + fromUserName + "]]></ToUserName><FromUserName><![CDATA[" + toUserName + "]]></FromUserName><CreateTime>" + System.currentTimeMillis() / 1000 + "</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[" + config[1] + "]]></Content></xml>");
                                case "url" -> {
                                    try
                                    {
                                        response.put("200 OK", new HttpClient().postReqStr(config[1] + "?signature=" + state[2] + "&timestamp=" + state[3] + "&nonce=" + state[4] + "&openid=" + state[1], body));
                                    }
                                    catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError ignored)
                                    {
                                        response.put("200 OK", "success");
                                    }
                                }
                                case "proxyText" -> {
                                    try
                                    {
                                        HashMap<String, String> hm = new HashMap<>();
                                        hm.put("ToUserName", toUserName);
                                        hm.put("FromUserName", fromUserName);
                                        String contentResp = new HttpClient().postReqStr(config[1] + "?signature=" + state[2] + "&timestamp=" + state[3] + "&nonce=" + state[4] + "&openid=" + state[1], executeText(config[2], hm, false));
                                        if (!config[3].isEmpty())
                                        {
                                            Document document2 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(contentResp)));
                                            XPath xpath2 = XPathFactory.newInstance().newXPath();
                                            Node contentNode = (Node) xpath2.evaluate("/xml/Content", document2, XPathConstants.NODE);
                                            if (contentNode != null)
                                            {
                                                hm.put("Content", contentNode.getTextContent());
                                                contentNode.setTextContent(executeText(config[3], hm, false));
                                            }
                                            StringWriter writer = new StringWriter();
                                            Transformer transformer = TransformerFactory.newInstance().newTransformer();
                                            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); //去掉XML
                                            transformer.transform(new DOMSource(document2), new StreamResult(writer));
                                            response.put("200 OK", writer.toString());
                                        }
                                        else
                                            response.put("200 OK", contentResp);
                                    }
                                    catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError | TransformerException ignored)
                                    {
                                        response.put("200 OK", "success");
                                    }
                                }
                                case "urlText" -> {
                                    response.put("200 OK", "<xml><ToUserName><![CDATA[" + fromUserName + "]]></ToUserName><FromUserName><![CDATA[" + toUserName + "]]></FromUserName><CreateTime>" + System.currentTimeMillis() / 1000 + "</CreateTime><MsgType><![CDATA[text]]></MsgType><Content><![CDATA[" + (((boolean) urlTextSendArrayWait[0]) ? ((!((boolean) urlTextSendArrayWait[1])) ? "有正在发送的消息，无法接受指令" : "正在继续发送") : config[2]) + "]]></Content></xml>");
                                    if (!((boolean) urlTextSendArrayWait[0]) || ((boolean) urlTextSendArrayWait[1]))
                                    {
                                        if (!((boolean) urlTextSendArrayWait[1]) || ((int) urlTextSendArrayWait[2]) > 1)
                                        {
                                            if (((boolean) urlTextSendArrayWait[1]))
                                            {
                                                urlTextSendArrayWait[2] = ((int) urlTextSendArrayWait[2]) - 1;
                                                urlTextSendArrayWait[1] = false;
                                            }
                                        }
                                        if (!((boolean) urlTextSendArrayWait[0]))
                                        {
                                            thread(() -> {
                                                Matcher urlSend = Pattern.compile("(get|post),(.*)").matcher(config[3]);
                                                if (urlSend.matches())
                                                {
                                                    String requestFunc = urlSend.group(1);
                                                    String resp = "";
                                                    HashMap<String, String> hm = new HashMap<>();
                                                    hm.put("ToUserName", toUserName);
                                                    hm.put("FromUserName", fromUserName);
                                                    hm.put("CreateTime", CreateTime);
                                                    hm.put("MsgId", MsgId);
                                                    if (configC.isText())
                                                        hm.put("Content", ars.get());
                                                    else
                                                        hm.put("Content", "");

                                                    String reqParam = executeText(urlSend.group(2), hm, true);
                                                    if (requestFunc.equals("get"))
                                                    {
                                                        try
                                                        {
                                                            resp = new HttpClient().getReqStr(config[1] + reqParam);
                                                        }
                                                        catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError | JSONException e)
                                                        {
                                                            resp = e.getMessage();
                                                        }
                                                    }
                                                    else if (requestFunc.equals("post"))
                                                    {
                                                        try
                                                        {
                                                            resp = new HttpClient().postReqStr(config[1], reqParam);

                                                        }
                                                        catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError | JSONException e)
                                                        {
                                                            resp = e.getMessage();
                                                        }
                                                    }
                                                    Matcher urlResp = Pattern.compile("(str|array),(.*)").matcher(config[4]);
                                                    if (urlResp.matches())
                                                    {
                                                        String[] splitKey = urlResp.group(2).split("\\.");
                                                        JSONObject json = new JSONObject(resp);
                                                        for (int i = 0; i < splitKey.length - 1; i++)
                                                            json = new JSONObject(json.get(splitKey[i]).toString());
                                                        if (urlResp.group(1).equals("str"))
                                                        {
                                                            String message = json.getString(splitKey[splitKey.length - 1]);
                                                            sendMsg(fromUserName, message);
                                                        }
                                                        else if (urlResp.group(1).equals("array"))
                                                        {
                                                            JSONArray ja = json.getJSONArray(splitKey[splitKey.length - 1]);
                                                            int dataArrayLen = ja.length();
                                                            if (dataArrayLen > ONCE_MESSAGE_LIMIT)
                                                            {
                                                                int realSend = ONCE_MESSAGE_LIMIT - 1;
                                                                int bigParagraphRemainder = dataArrayLen % realSend;
                                                                int bigParagraph = dataArrayLen / realSend + (bigParagraphRemainder == 0 || bigParagraphRemainder == 1 ? 0 : 1);
                                                                int alreadySend = 0;
                                                                urlTextSendArrayWait[0] = true;
                                                                urlTextSendArrayWait[2] = bigParagraph;
                                                                for (int i = 0; i < bigParagraph; i++)
                                                                {
                                                                    int k = (i == bigParagraph - 1) ? (bigParagraphRemainder == 0 ? realSend : (bigParagraphRemainder == 1 ? ONCE_MESSAGE_LIMIT : bigParagraphRemainder)) : realSend;
                                                                    for (int j = alreadySend; j < alreadySend + k; j++)
                                                                        sendMsg(fromUserName, ja.getString(j));
                                                                    alreadySend += k;
                                                                    if (i != bigParagraph - 1)
                                                                    {
                                                                        sendMsg(fromUserName, "*:由于微信API限制，主动给用户发送消息超过" + ONCE_MESSAGE_LIMIT + "条会禁止发送，必须用户主动发消息才能继续补发剩余内容，请发送开头带你向AI发送指令的头部，后面加上任意文字，才能继续回复，限制30s，超过时间默认放弃继续回复");
                                                                        urlTextSendArrayWait[1] = true;
                                                                        int wait = 1;
                                                                        do
                                                                        {
                                                                            try
                                                                            {
                                                                                Thread.sleep(1000);
                                                                                wait++;
                                                                            }
                                                                            catch (InterruptedException ignored)
                                                                            {}
                                                                        }
                                                                        while (((boolean) urlTextSendArrayWait[1]) && wait <= 30);
                                                                        if (((boolean) urlTextSendArrayWait[1]))
                                                                            break;
                                                                    }
                                                                }
                                                                urlTextSendArrayWait[0] = false;
                                                                urlTextSendArrayWait[1] = false;
                                                                urlTextSendArrayWait[2] = 0;
                                                            }
                                                            else
                                                            {
                                                                for (int i = 0; i < dataArrayLen; i++)
                                                                    sendMsg(fromUserName, ja.getString(i));
                                                            }
                                                        }
                                                    }
//                                            else
//                                                throw new IOException();
                                                }
//                                        else
//                                            throw new IOException();
                                            });
                                        }
                                    }
                                }
                            }
                        }
                        catch (JSONException | XPathExpressionException | ParserConfigurationException | IOException | SAXException | NullPointerException e)
                        {
                            response.put("200 OK", "success");
                        }
                    }
                    else
                        response.put("400 Bad Request", "");
                }
            }
            else if (request.get(HttpServer.URL).get(0).equals("/access_token"))
            {
                System.out.println(request.toString().replaceAll("\n", "\\\\n"));
                ArrayList<String> postBody = request.get(HttpServer.BODY);
                if ((postBody == null ? "" : postBody.get(0)).isEmpty())//GET请求
                {
                    ArrayList<String> params = request.get(HttpServer.PARAM);
                    if (HttpServer.findParam(params, "secret").equals(Main.APP_SECRET))
                        response.put("200 OK", new JSONObject().put("access_token", TokenManager.instance.getAccessToken()).put("expires_in", TokenManager.instance.getExpiresIn()).toString());
                    else
                        response.put("401 Unauthorized", "");
                }
                else
                    response.put("400 Bad Request", "");
            }
            else if (request.get(HttpServer.URL).get(0).equals("/template"))
            {
                System.out.println(request.toString().replaceAll("\n", "\\\\n"));
                ArrayList<String> postBody = request.get(HttpServer.BODY);
                String body;
                if (!(body = (postBody == null ? "" : postBody.get(0))).isEmpty())//GET请求
                {
                    ArrayList<String> params = request.get(HttpServer.PARAM);
                    if (HttpServer.findParam(params, "secret").equals(Main.APP_SECRET))
                    {
                        try
                        {
                            String str;
                            JSONObject templateJson = new JSONObject(body);
                            ConfigLoader.Config configC = configLoader.getConfigs(templateJson.getString("template_id"), null, null);
                            if (configC.getConfig()[0] == null)
                                throw new JSONException("null body");
                            JSONObject sendJson;
                            if (!configC.getConfig()[0].isEmpty())
                            {
                                String[] config = (str = (configC.getConfig()[0].replaceAll(" ", ""))).substring(1, str.length() - 1).split(",");
                                JSONObject templateData = templateJson.getJSONObject("data");
                                for (int i = 0; i < config.length; i++)
                                {
                                    Object oldData = templateData.get(config[i]);
                                    templateData.remove(config[i++]);
                                    templateData.put(config[i], oldData);
                                }
                                templateJson.remove("data");
                                sendJson = templateJson.put("data", templateData);
                            }
                            else
                                sendJson = templateJson;

                            response.put("200 OK", new HttpClient().postReqStr(SHA1.DOMAIN + "/cgi-bin/message/template/send?access_token=" + TokenManager.instance.getAccessToken(), sendJson.toString()));
                        }
                        catch (JSONException | HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError ignored)
                        {
                            response.put("400 Bad Request", "");
                        }
                    }
                    else
                        response.put("401 Unauthorized", "");
                }
                else
                    response.put("400 Bad Request", "");
            }
            else if (request.get(HttpServer.URL).get(0).equals("/"))
                response.put("200 OK", "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>影幽网络-公众号服务器代理</title><style>body {font-family: \"Microsoft YaHei\", sans-serif;text-align: center;margin: 40px;}h1 {color: #339900;}p {font-size: 18px;}.contact-info {margin-top: 20px;}.contact-info a {color: #0066cc;text-decoration: none;}.contact-info a:hover {text-decoration: underline;}</style></head><body><h1>欢迎访问</h1><h1>影幽网络公众号服务器代理程序</h1><p>您已成功访问我们的服务器默认界面</p><p>数据URL：/auth<br>获取ACCESS_TOKEN URL：/access_token?secret=填写配置文件的，GET请求<br>动态修改模板URL：/template?secret=填写配置文件的，POST请求</p><div class=\"contact-info\"><p>如有任何问题或建议，请通过以下方式联系我们：</p><p>Github: <a href=\"https://github.com/Asqnw/WxOfficial\">点击进入</a></p></div></body></html>");
            else
                response.put("403 Forbidden", "");

            return response;
        }, port);
    }

    private static String[] checkSign(HashMap<String, ArrayList<String>> request)
    {
        String signature;
        String timestamp;
        String nonce;
        String str;
        ArrayList<String> params = request.get(HttpServer.PARAM);
        if ((!(signature = HttpServer.findParam(params, "signature")).isEmpty()) && (!(timestamp = HttpServer.findParam(params, "timestamp")).isEmpty()) && (!(nonce = HttpServer.findParam(params, "nonce")).isEmpty()))//判断验证参数是否正常
        {
            if (SHA1.getSHA1(TOKEN, timestamp, nonce).equals(signature))
            {
                if ((!(str = HttpServer.findParam(params, "echoStr".toLowerCase())).isEmpty()) || (!(str = HttpServer.findParam(params, "openId".toLowerCase())).isEmpty()))//验证
                    return new String[]{"0", str, signature, timestamp, nonce};
                else
                    return new String[]{"1", "success"};
            }
            else
                return new String[]{"1", "success"};
        }
        else
            return new String[]{"2", ""};
    }

    private static void sendMsg(String fromUserName, String message)
    {
        try
        {
            System.out.println(new HttpClient().postReqStr(SHA1.DOMAIN + "/cgi-bin/message/custom/send?access_token=" + TokenManager.instance.getAccessToken(), "{\"touser\":\"" + fromUserName + "\",\"msgtype\":\"text\",\"text\":" + (new JSONObject().put("content", message).toString()) + "}"));
        }
        catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError e)
        {
            PrintStream ps = System.out;
            ps.print("主动发送消息异常：");
            e.printStackTrace(ps);
        }
    }

    private static String executeText(String msg, HashMap<String, String> body, boolean isEn)
    {
        return msg.replaceAll("%ToUserName%", body.get("ToUserName")).
                replaceAll("%FromUserName%", body.get("FromUserName")).
                replaceAll("%MsgId%", body.get("MsgId")).
                replaceAll("%CreateTime%", body.containsKey("CreateTime") ? body.get("CreateTime") : String.valueOf(System.currentTimeMillis() / 1000)).
                replaceAll("%Content%", isEn ? Base64.getEncoder().encodeToString(body.getOrDefault("Content", "").getBytes()) : body.getOrDefault("Content", ""));
    }

    public static void thread(Runnable runnable)
    {
        threadPool.execute(runnable);
    }
}