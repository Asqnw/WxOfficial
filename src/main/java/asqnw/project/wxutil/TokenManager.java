package asqnw.project.wxutil;

import asqnw.project.Main;
import asqnw.project.http.HttpClient;
import org.json.JSONObject;

import java.time.Instant;

/**
 * @Author 暗影之风
 * @CreateTime 2025-03-17 11:40:42
 * @Description 微信公众号ACCESS_TOKEN
 */
@SuppressWarnings("JavadocDeclaration")
public class TokenManager implements Runnable
{
    public static final TokenManager instance = new TokenManager();
    private boolean running = false;
    private Thread thread = null;
    private String token = "";
    private long expires = 0;
    private long update = 0;

    @Override
    public void run()
    {
        this.running = true;
        while (true)
        {
            try
            {
                long currentTime = currentTimeSecond();
                if (this.update + (this.expires - 50) < currentTime || this.token.isEmpty())
                {
                    for (int i = 0; i < 3; i++)
                    {
                        if (this.refreshAccessToken())
                        {
                            Thread.sleep(Math.max(0, this.update + (this.expires - 50) - currentTime) * 1000);
                            break;
                        }
                    }
                }
            }
            catch (InterruptedException ignored)
            {
                break;
            }
        }
        this.running = false;
    }

    public void start()
    {
        if (this.running)
            return;
        this.thread = new Thread(this);
        this.thread.start();
        while (!this.running || token.isEmpty())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException ignored)
            {}
        }
    }

    public void stop()
    {
        if (!this.running || this.thread == null)
            return;
        try
        {
            this.thread.interrupt();
            if (this.thread.isAlive())
                this.thread.join();
        }
        catch (InterruptedException ignored)
        {}
    }

    public String getAccessToken()
    {
        return this.token.isEmpty() ? this.positiveRefreshAccessToken() : this.token;
    }

    private String positiveRefreshAccessToken()
    {
        return this.refreshAccessToken() ? this.token : "";
    }

    private boolean refreshAccessToken()
    {
        for (int i = 0; i < 3; i++)
        {
            try
            {
                HttpClient httpClient = new HttpClient();
                JSONObject json = new JSONObject(httpClient.getReqStr(Main.WxServerInfo.DOMAIN + "/cgi-bin/token?grant_type=client_credential&appid=" + Main.APPID + "&secret=" + Main.APP_SECRET));
                System.out.println("更新ACCESS_TOKEN：" + json);
                if (json.has("access_token") && json.has("expires_in"))
                {
                    this.token = json.getString("access_token");
                    this.expires = json.getInt("expires_in");
                    this.update = currentTimeSecond();
                    return true;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace(System.out);
            }
        }
        return false;
    }

    private long currentTimeSecond()
    {
        return Instant.now().getEpochSecond();
    }
}
