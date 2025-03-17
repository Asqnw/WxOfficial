package asqnw.project.wxutil;

import asqnw.project.Main;
import asqnw.project.http.HttpClient;
import lombok.Data;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @Author 暗影之风
 * @CreateTime 2025-03-17 11:40:42
 * @Description 微信公众号ACCESS_TOKEN
 */
@SuppressWarnings("JavadocDeclaration")
public class TokenManager
{
    private static volatile TokenManager instance;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<TokenInfo> tokenInfo = new AtomicReference<>();
    private static final int REFRESH_MARGIN = 50;//提前50秒刷新
    private static final HttpClient httpClient = new HttpClient();

    @Data
    private static class TokenInfo
    {
        private String token;
        private long expiresAt;
    }

    private TokenManager()
    {
        refreshAccessToken();
    }

    public static TokenManager getInstance()
    {
        if (instance == null)
        {
            synchronized (TokenManager.class)
            {
                if (instance == null)
                    instance = new TokenManager();
            }
        }
        return instance;
    }

    public String getAccessToken()
    {
        TokenInfo current = tokenInfo.get();
        if (shouldRefresh(current))
        {
           synchronized (this)
           {
                current = tokenInfo.get();
                if (shouldRefresh(current))
                    refreshAccessTokenSync();
            }
        }
        return tokenInfo.get().token;
    }

    private boolean shouldRefresh(TokenInfo tokenInfo)
    {
        return tokenInfo == null || System.currentTimeMillis() >= tokenInfo.expiresAt - REFRESH_MARGIN * 1000;
    }

    private void refreshAccessTokenSync()
    {
        try
        {
            JSONObject json = WeChatApi.refreshAccessToken();
            updateTokenAndScheduleRefresh(json);
        }
        catch (Exception e)
        {
            handleRefreshFailure(e);
        }
    }

    private void updateTokenAndScheduleRefresh(JSONObject json)
    {
        String newToken = json.getString("access_token");
        int expiresIn = json.getInt("expires_in");
        long newExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        TokenInfo ti = new TokenInfo();
        ti.setToken(newToken);
        ti.setExpiresAt(newExpiresAt);
        System.out.println("ACCESS_TOKEN更新: " + (ti.getToken().substring(0, 10)) + "***");
        tokenInfo.set(ti);

        long delay = Math.max(expiresIn - REFRESH_MARGIN, 0);
        scheduler.schedule(this::refreshAccessToken, delay, TimeUnit.SECONDS);
    }

    private void handleRefreshFailure(Exception e)
    {
        int maxRetries = 3;
        for (int retry = 0; retry < maxRetries; retry++)
        {
            try
            {
                Thread.sleep(1000L * (1 << retry)); //指数退避
                JSONObject json = WeChatApi.refreshAccessToken();
                updateTokenAndScheduleRefresh(json);
                return;
            }
            catch (InterruptedException ie)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException("刷新Token被中断", ie);
            }
            catch (Exception ex)
            {
                if (retry == maxRetries - 1) {
                    throw new RuntimeException("Token刷新失败，已重试" + maxRetries + "次", ex);
                }
            }
        }
        e.printStackTrace(System.out);
    }

    private void refreshAccessToken()
    {
        try
        {
            JSONObject json = WeChatApi.refreshAccessToken();
            updateTokenAndScheduleRefresh(json);
        }
        catch (Exception e)
        {
            scheduler.schedule(this::refreshAccessToken, 10, TimeUnit.SECONDS);
        }
    }

    public void shutdown()
    {
        scheduler.shutdown();
    }

    private static class WeChatApi
    {
        static JSONObject refreshAccessToken() throws HttpClient.HttpException.UnAuthorize, HttpClient.HttpException.Unknown, HttpClient.HttpException.ServerError, HttpClient.HttpException.Forbidden
        {
            JSONObject accessTokenJson = new JSONObject(httpClient.getReqStr(Main.WxServerInfo.DOMAIN + "/cgi-bin/token?grant_type=client_credential&appid=" + Main.APPID + "&secret=" + Main.APP_SECRET));
            if (accessTokenJson.has("access_token"))
                return accessTokenJson;
            else
                throw new JSONException(accessTokenJson.toString());
        }
    }
}
