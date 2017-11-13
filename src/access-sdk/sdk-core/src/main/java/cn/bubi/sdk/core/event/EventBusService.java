package cn.bubi.sdk.core.event;

import cn.bubi.sdk.core.event.handle.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 上午10:15.
 * 简单的事件通知总线
 */
public class EventBusService{

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBusService.class);

    private static volatile Map<String, List<EventHandler>> eventHandleMap = new HashMap<>();

    private static volatile ExecutorService eventExecutor = new
            ThreadPoolExecutor(5, 200, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000));


    public static synchronized void addEventHandler(EventHandler eventHandle){
        List<EventHandler> eventHandlers = eventHandleMap.get(eventHandle.eventSourceCode());
        if (eventHandlers == null) {
            eventHandlers = new ArrayList<>();
        }
        eventHandlers.add(eventHandle);
        eventHandleMap.put(eventHandle.eventSourceCode(), eventHandlers);
    }

    public static void publishEvent(String eventCode, String message){
        List<EventHandler> eventHandlers = eventHandleMap.get(eventCode);
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            LOGGER.debug("not found event handle , event code:" + eventCode);
            return;
        }

        eventHandlers.forEach(eventHandler -> eventExecutor.execute(() -> eventHandler.onEvent(message)));
    }

}
