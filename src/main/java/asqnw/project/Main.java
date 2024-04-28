package asqnw.project;

import asqnw.project.config.ConfigLoader;
import asqnw.project.http.HttpClient;
import asqnw.project.http.HttpServer;
import asqnw.project.wxutil.SHA1;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main
{
    public static String APPID;
    public static String APP_SECRET;
    public static String TOKEN;
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static ConfigLoader configLoader;

    public static void main(String[] args)
    {
        int port = 10000;
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
                    System.out.println("1.1设置端口：" + port);
                }
                catch (Exception e)
                {
                    System.out.println("1.1读取端口参数异常，使用默认参数10000");
                }
            }
        }
        if (port == 10000)
            System.out.println("1.1未设置端口参数，使用默认参数10000");
        System.out.println("二、加载配置文件");
        try
        {
            configLoader = new ConfigLoader("config.properties");
            APPID = configLoader.getProperty("APPID");
            System.out.println("APPID：" + APPID.substring(0, 5) + "***");
            APP_SECRET = configLoader.getProperty("APPSECRET");
            System.out.println("APPSECRET：" + APP_SECRET.substring(0, 6) + "***");
            TOKEN = configLoader.getProperty("TOKEN");
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
        System.out.println("三、获取微信服务器IP组");
        System.out.println("3.1获取公众号ACCESS_TOKEN");
        try
        {
            HttpClient httpClient;
            String ACCESS_TOKEN = new JSONObject((httpClient = new HttpClient()).getReqStr("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + APPID + "&secret=" + APP_SECRET)).getString("access_token");
            System.out.println("3.1获取ACCESS_TOKEN成功(为了某些情况下安全，只显示部分)：" + (ACCESS_TOKEN.substring(0, 10)) + "***");
            System.out.println("3.2准备获取微信服务器的IP组");
            JSONArray array = new JSONObject(httpClient.getReqStr("https://api.weixin.qq.com/cgi-bin/get_api_domain_ip?access_token=" + ACCESS_TOKEN)).getJSONArray("ip_list");
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
            if (request.get(HttpServer.URL).get(0).equals("/"))
            {
//            String userIp = HttpServer.findHeader(request.get(HttpServer.HEADER), "x-forwarded-for");//这里无需担心该请求头伪造问题，在Nginx中已经处理
                String body;
                System.out.println(request);
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
                            String[] config = configLoader.getConfigs(xpath, document);
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
                                        response.put("200 OK", new HttpClient().postReqStr(config[1] + "?signature=" + state[2] + "&timestamp=" + state[3] + "&nonce=" + state[4] + "&openid=" + state[1], executeText(config[2], hm)));
                                    }
                                    catch (HttpClient.HttpException.UnAuthorize | HttpClient.HttpException.Forbidden | HttpClient.HttpException.Unknown | HttpClient.HttpException.ServerError ignored)
                                    {
                                        response.put("200 OK", "success");
                                    }
                                }
                            }
                        }
                        catch (JSONException | XPathExpressionException | ParserConfigurationException | IOException | SAXException ignored)
                        {
                            response.put("200 OK", "success");
                        }
                    }
                    else
                        response.put("400 Bad Request", "");
                }
            }
            return response;
        }, port);
    }

    public static void thread(Runnable runnable)
    {
        threadPool.execute(runnable);
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

    private static String executeText(String msg, HashMap<String, String> body)
    {
        return msg.replaceAll("%ToUserName%", body.get("ToUserName")).
                replaceAll("%FromUserName%", body.get("FromUserName")).
                replaceAll("%MsgId%", body.get("MsgId")).
                replaceAll("%CreateTime%", String.valueOf(System.currentTimeMillis() / 1000));
    }
}