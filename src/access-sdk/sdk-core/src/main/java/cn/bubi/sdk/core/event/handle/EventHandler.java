package cn.bubi.sdk.core.event.handle;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 上午10:17.
 * 事件处理器
 */
public interface EventHandler{

    /**
     * 事件源code
     */
    String eventSourceCode();

    /**
     * 事件处理器
     */
    void onEvent(String message);

}
