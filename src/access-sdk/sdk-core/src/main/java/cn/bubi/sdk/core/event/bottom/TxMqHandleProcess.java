package cn.bubi.sdk.core.event.bottom;

import cn.bubi.sdk.core.event.EventBusService;
import cn.bubi.sdk.core.event.message.TransactionExecutedEventMessage;
import cn.bubi.sdk.core.event.source.TransactionNotifyEventSource;
import cn.bubi.sdk.core.utils.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 下午6:07.
 * 针对收到重复通知以及确认错误结果做的处理器,多个监听线程应共享一个后置处理器
 */
public class TxMqHandleProcess{

    private static Logger logger = LoggerFactory.getLogger(TxMqHandleProcess.class);

    private LimitQueue<String> successQueue = new LimitQueue<>(300);
    private LimitQueue<String> failQueue = new LimitQueue<>(300);
    private final Object lock = new Object();

    private TxFailManager txFailManager;

    public TxMqHandleProcess(TxFailManager txFailManager){
        this.txFailManager = txFailManager;
    }

    void process(TransactionExecutedEventMessage executedEventMessage){

        // 队列操作需要保证线程安全
        synchronized (lock) {
            txFailManager.addFailEventMessage(executedEventMessage);
            String txHash = executedEventMessage.getHash();

            // 成功队列存在，则直接返回
            if (successQueue.exist(txHash)) {
                logger.debug("successQueue exist txHash:" + txHash + ",ignore.");
                return;
            }

            if (!executedEventMessage.getSuccess()) {
                // 失败，入队列，交给后台线程去检查
                if (failQueue.exist(txHash)) {
                    logger.debug("failQueue exist txHash:" + txHash + ",ignore.");
                    return;
                }
                logger.debug("failQueue offer txHash:" + txHash);
                failQueue.offer(txHash);

                txFailManager.addFailEventHash(txHash);
                return;
            }

            // 成功，入成功队列，发通知
            successQueue.offer(txHash);
        }

        EventBusService.publishEvent(TransactionNotifyEventSource.CODE, GsonUtil.toJson(executedEventMessage));
    }


}
