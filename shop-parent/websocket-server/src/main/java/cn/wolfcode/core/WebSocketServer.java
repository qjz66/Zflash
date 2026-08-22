package cn.wolfcode.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ServerEndpoint("/ws/{token}")
@Component
public class WebSocketServer {

    public static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(@PathParam("token") String token, Session session) {
        log.info("[WebSocket 服务] 用户{}上线了...", token);
        SESSION_MAP.put(token, session);
    }

    @OnClose
    public void OnClose(@PathParam("token") String token) {
        log.info("[WebSocket 服务] 用户{}下线了...", token);
        SESSION_MAP.remove(token);
    }

    @OnError
    public void OnError(Throwable throwable) {
        log.warn("[WebSocket 服务] socket 连接出现异常：", throwable);
    }
}
