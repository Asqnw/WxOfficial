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
    private static final long BEFORE_UPDATE_TIME = 50;

    @Override
    public void run()
    {
        this.running = true;
        while (true)
        {
            try
            {
                if (this.getExpiresIn() < 0 || this.token.isEmpty())
                {
                    for (int i = 0; i < 3; i++)
                    {
                        if (this.refreshAccessToken())
                        {
                            Thread.sleep(Math.max(0, this.getExpiresIn()) * 1000);
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

    public long getExpiresIn()
    {
        return this.update + (this.expires - Math.min(BEFORE_UPDATE_TIME, (long) (expires * 0.8))) - this.currentTimeSecond();
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
                httpClient.requestProperty.put("ThroughProxy", "1");//配合Fiddler
                JSONObject json = new JSONObject(httpClient.getReqStr(SHA1.DOMAIN + "/cgi-bin/token?grant_type=client_credential&appid=" + Main.APPID + "&secret=" + Main.APP_SECRET));
                if (json.has("access_token") && json.has("expires_in"))
                {
                    this.token = json.getString("access_token");
                    this.expires = json.getInt("expires_in");
                    this.update = this.currentTimeSecond();
                    System.out.println("更新ACCESS_TOKEN成功");
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
