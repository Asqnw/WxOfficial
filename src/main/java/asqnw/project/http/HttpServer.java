package asqnw.project.http;

import asqnw.project.Main;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @Author 暗影之风
 * @CreateTime 2024-01-30 18:52:17
 * @Description HTTP服务器
 */
@SuppressWarnings("JavadocDeclaration")
public final class HttpServer
{
    @Getter
    private final HashMap<String, ArrayList<String>> request = new HashMap<>();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String URL = "URL";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String TYPE = "TYPE";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String PARAM = "PARAM";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String BODY = "BODY";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String COOKIE = "COOKIE";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    public static final String HEADER = "HEADER";

    public void start(ServerCallBack callBack, int port)
    {
        try (ServerSocket serverSocket = new ServerSocket(port))
        {
            System.out.println("服务器所使用端口：" + serverSocket.getLocalPort());
            while (true)
            {
                Socket socket = serverSocket.accept();
                Main.thread(() -> {
                    try
                    {
                        BufferedReader bd = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        String requestHeader;
                        String GET_TAG = "GET";
                        String POST_TAG = "POST";
                        String URL_END_TAG = "HTTP/";
                        String POST_LEN_TAG = "content-length:";
                        String SET_COOKIE_TAG = "cookie:";
                        boolean isPost = false;
                        boolean isGet = false;
                        int postBodyLen = 0;
                        this.request.put(COOKIE, new ArrayList<>());
                        this.request.put(HEADER, new ArrayList<>());
                        for (int i = 0; (requestHeader = bd.readLine()) != null && !requestHeader.isEmpty(); i++)
                        {
                            if (i == 0 && requestHeader.startsWith(GET_TAG))//GET请求
                            {
                                isGet = true;
                                this.request.put(TYPE, new ArrayList<>() {{this.add(GET_TAG);}});
                                String url = requestHeader.substring(GET_TAG.length(), requestHeader.lastIndexOf(URL_END_TAG)).trim();
                                this.request.put(URL, new ArrayList<>(){{
                                    this.add(url.indexOf('?') < 1 ? url : url.substring(0, url.indexOf('?')));
                                }});
                                this.request.put(PARAM, new ArrayList<>() {{
                                    if (url.indexOf('?') > 0)
                                    {
                                        ArrayList<HashMap<String, String>> params = getParam(url.substring(url.indexOf('?') + "?".length()));
                                        String key;
                                        for (HashMap<String, String> p : params)
                                            this.add((key = (String) p.keySet().toArray()[0]) + "=" + p.get(key));
                                    }
                                }});
                            }
                            else if (i == 0 && requestHeader.startsWith(POST_TAG))//POST请求
                            {
                                isPost = true;
                                String url = requestHeader.substring(POST_TAG.length(), requestHeader.lastIndexOf(URL_END_TAG)).trim();
                                this.request.put(TYPE, new ArrayList<>() {{this.add(POST_TAG);}});
                                this.request.put(URL, new ArrayList<>(){{
                                    this.add(url.indexOf('?') < 1 ? url : url.substring(0, url.indexOf('?')));
                                }});
                                this.request.put(PARAM, new ArrayList<>() {{
                                    if (url.indexOf('?') > 0)
                                    {
                                        ArrayList<HashMap<String, String>> params = getParam(url.substring(url.indexOf('?') + "?".length()));
                                        String key;
                                        for (HashMap<String, String> p : params)
                                            this.add((key = (String) p.keySet().toArray()[0]) + "=" + p.get(key));
                                    }
                                }});
                            }
                            else if (i == 0)//其它类型请求
                                break;
                            else if (isPost && requestHeader.toLowerCase().startsWith(POST_LEN_TAG))//POST请求头长度
                            {
                                String str;
                                postBodyLen = Integer.parseInt((str = requestHeader.substring(POST_LEN_TAG.length()).trim()).matches("[0-9]+") ? str : "0");
                            }
                            else if (requestHeader.toLowerCase().startsWith(SET_COOKIE_TAG))//设置Cookie
                            {
                                String[] itemCookie = requestHeader.substring(SET_COOKIE_TAG.length()).replaceAll("\\s*", "").split(";");//去除全部空格分隔;
                                for (String s : itemCookie)//key=value
                                {
                                    final String cookieTag = "=";
                                    String key;
                                    String value;
                                    if (!s.contains(cookieTag))//特殊的不符合键值类型的cookie，通常将该内容存为键，值是true
                                    {
                                        key = s;
                                        value = String.valueOf(true);
                                    }
                                    else
                                    {
                                        key = s.substring(0, s.indexOf(cookieTag));
                                        value = s.substring(s.indexOf(cookieTag) + cookieTag.length());
                                    }
                                    this.request.get(COOKIE).add(key + "=" + value);
                                }
                            }
                            else//其它HEADER
                                this.request.get(HEADER).add(requestHeader);
                        }
                        if (!isGet && !isPost)
                        {
                            PrintWriter pw = new PrintWriter(socket.getOutputStream());
                            pw.println("HTTP/1.1 501 Not Implemented");
                            pw.println();
                            pw.flush();
                            socket.close();
                            return;
                        }
                        if (isPost)
                        {
                            if (postBodyLen > 0)
                            {
                                StringBuilder sb = new StringBuilder();
                                while (bd.ready())
                                    sb.append((char) bd.read());

                                this.request.put(BODY, new ArrayList<>() {{
                                    this.add(sb.toString());
                                }});
                            }
                            else
                            {
                                PrintWriter pw = new PrintWriter(socket.getOutputStream());
                                pw.println("HTTP/1.1 411 Length Required");
                                pw.println();
                                pw.flush();
                                socket.close();
                                return;
                            }
                        }
                        HashMap<String, String> result = callBack.run(this.request);
                        String key;
                        PrintWriter pw = new PrintWriter(socket.getOutputStream());

                        pw.println("HTTP/1.1 " + (key = (String) result.keySet().toArray()[0]));
                        pw.println("Content-type:text/html");
                        pw.println();
                        pw.print(result.get(key));

                        pw.flush();
                        socket.close();
                    }
                    catch (Exception ignored)
                    {}
                });

            }
        }
        catch (Exception ignored)
        {}
    }

    public static ArrayList<HashMap<String, String>> getParam(String message)
    {
        return new ArrayList<>() {{
            String[] params = message.replaceAll("\\s*", "").split("&");
            int sub = 1;
            for (int j = 0; j < params.length; j++)
            {
                if ((params[j].indexOf('=') > 0 && params[j].indexOf('=') != params[j].length() - 1) || j == 0)
                {
                    int finalJ = j;
                    this.add(new HashMap<>() {{
                        this.put(params[finalJ].split("=")[0], params[finalJ].substring(params[finalJ].indexOf('=') + "=".length()));
                    }});
                }
                else
                {
                    String key = (String) this.get(j - sub).keySet().toArray()[0];
                    String s = this.get(j - sub).get(key) + params[j];
                    this.set(j - sub, new HashMap<>() {{
                        this.put(key, s);
                    }});
                    sub++;
                }
            }
        }};
    }

    public static String findParam(ArrayList<String> arrayList, String key)//a=b,c=d -> a = b
    {
        for (String s : arrayList)
            if ((s + "=").toLowerCase().startsWith(key))
                return s.substring(s.indexOf('=') + "=".length());

        return "";
    }

    public interface ServerCallBack
    {
        HashMap<String, String> run(HashMap<String, ArrayList<String>> request);
    }
}
