package cn.bubi.sdk.core.event.handle;

import cn.bubi.sdk.core.event.message.TransactionExecutedEventMessage;
import cn.bubi.sdk.core.event.source.TransactionNotifyEventSource;
import cn.bubi.sdk.core.seq.SequenceManager;
import cn.bubi.sdk.core.transaction.sync.TransactionSyncManager;
import cn.bubi.sdk.core.utils.GsonUtil;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 上午10:20.
 * 交易通知器
 */
public class TransactionNotifyEventHandler implements EventHandler{

    private SequenceManager sequenceManager;
    private TransactionSyncManager transactionSyncManager;

    public TransactionNotifyEventHandler(SequenceManager sequenceManager, TransactionSyncManager transactionSyncManager){
        this.sequenceManager = sequenceManager;
        this.transactionSyncManager = transactionSyncManager;
    }

    @Override
    public String eventSourceCode(){
        return TransactionNotifyEventSource.CODE;
    }

    @Override
    public void onEvent(String message){
        TransactionExecutedEventMessage executedEventMessage = GsonUtil.fromJson(message, TransactionExecutedEventMessage.class);

        if (!executedEventMessage.getSuccess()) {
            fail(executedEventMessage);
        }

        // 同步通知
        transactionSyncManager.notifyTransactionAsyncFutures(executedEventMessage);
    }

    private void fail(TransactionExecutedEventMessage executedEventMessage){
        // 重置seq
        sequenceManager.reset(executedEventMessage.getSponsorAddress());
    }

}
