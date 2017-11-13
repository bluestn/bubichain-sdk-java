package cn.bubi.sdk.core.transaction.sync;

import cn.bubi.sdk.core.event.message.TransactionExecutedEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理同步管理器
 */
public class TransactionSyncManager{

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionSyncManager.class);

    // 最大的异步超时时长；
    private static final long ASYNCFUTURE_EXPIRED_TIME = 60 * 1000;
    private volatile boolean running = true;
    private Map<String, AsyncFutureTx> txFutures = new ConcurrentHashMap<>();

    public void init(){
        Thread thread = new Thread(() -> {
            while (running) {
                evictExpiredListeners();
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    // swallow exception;
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void destroy(){
        running = false;
    }

    public void addAsyncFutureTx(AsyncFutureTx asyncFutureTx){
        asyncFutureTx.setTimestamp(System.currentTimeMillis());
        txFutures.put(asyncFutureTx.getSource(), asyncFutureTx);
    }

    public void remove(AsyncFutureTx result){
        txFutures.remove(result.getSource());
    }

    public void notifyTransactionAsyncFutures(TransactionExecutedEventMessage executedEventMessage){
        try {
            AsyncFutureTx asyncFutureTx = txFutures.remove(executedEventMessage.getHash());
            if (asyncFutureTx == null) {
                return;
            }
            if (executedEventMessage.getSuccess()) {
                asyncFutureTx.setSuccessFlag();
            } else {
                asyncFutureTx.setErrorFlag(executedEventMessage.getErrorCode(), executedEventMessage.getErrorMessage());
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Error occurred on notifying TRANSACTION_EXECUTED event! --[hash=%s] --[%s] %s", executedEventMessage.getHash(), e.getClass().getName(), e.getMessage()), e);
        }
    }

    private void evictExpiredListeners(){
        try {
            AsyncFutureTx[] asyncFutureTxs = txFutures.values().toArray(new AsyncFutureTx[0]);
            for (AsyncFutureTx asyncFuture : asyncFutureTxs) {
                long now = System.currentTimeMillis();
                long value = now - asyncFuture.getTimestamp();
                if (value > ASYNCFUTURE_EXPIRED_TIME) {
                    txFutures.remove(asyncFuture.getSource());
                    // 超时异常；
                    asyncFuture.setErrorFlag("900");
                }
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Error occurred on evicting expired the transaction state monitor futures! --[%s] %s", e.getClass().getName(), e.getMessage()), e);
        }
    }


}
