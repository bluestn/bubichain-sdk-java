package cn.bubi.sdk.core.event.bottom;

import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.access.adaptation.blockchain.bc.response.Transaction;
import cn.bubi.access.adaptation.blockchain.bc.response.TransactionHistory;
import cn.bubi.access.adaptation.blockchain.exception.BlockchainError;
import cn.bubi.sdk.core.event.EventBusService;
import cn.bubi.sdk.core.event.message.TransactionExecutedEventMessage;
import cn.bubi.sdk.core.event.source.TransactionNotifyEventSource;
import cn.bubi.sdk.core.utils.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/26 下午4:34.
 * 交易失败控制器
 */
public class TxFailManager{

    private static final Logger LOGGER = LoggerFactory.getLogger(TxMqHandleProcess.class);
    private final Object seqHashMappingLock = new Object();

    private volatile long currentSeq;// 当前区块seq
    private Set<String> currentFailTxHash = new HashSet<>();

    private Map<Long, Set<String>> seqHashMapping = new HashMap<>();// seq-hash映射
    private Map<String, Set<TransactionExecutedEventMessage>> hashMessageMapping = new HashMap<>();// hash-message映射

    private static ExecutorService failTxExecutor = new
            ThreadPoolExecutor(5, 200, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000));

    private RpcService rpcService;

    public TxFailManager(RpcService rpcService){
        this.rpcService = rpcService;
    }

    public void init(){
        currentSeq = rpcService.getLedger().getHeader().getSeq();
    }

    /**
     * 添加所有的失败事件
     */
    void addFailEventMessage(TransactionExecutedEventMessage message){
        Set<TransactionExecutedEventMessage> messageList = hashMessageMapping.computeIfAbsent(message.getHash(), hash -> new HashSet<>());
        messageList.add(message);
    }

    /**
     * 添加失败hash
     */
    void addFailEventHash(String hash){
        synchronized (seqHashMappingLock) {
            currentFailTxHash.add(hash);
        }
    }

    /**
     * 指定seq添加失败hash
     */
    public void addFailEventHash(long seq, String hash){
        synchronized (seqHashMappingLock) {
            if (currentSeq == seq) {
                currentFailTxHash.add(hash);
            } else {
                Set<String> hashSet = seqHashMapping.get(seq);
                if (hashSet == null) {
                    hashSet = new HashSet<>();
                }
                hashSet.add(hash);
            }
        }
    }

    /**
     * 区块增长，检查是否有需要查询的失败事件
     */
    public void notifySeqUpdate(long newSeq){
        synchronized (seqHashMappingLock) {
            long oldSeq = currentSeq;
            if (newSeq <= oldSeq) {
                return;
            }

            seqHashMapping.put(oldSeq, currentFailTxHash);

            currentSeq = newSeq;
            currentFailTxHash = new HashSet<>();
        }

        long wantExecutorFailTxLedger = newSeq - cn.bubi.sdk.core.transaction.Transaction.FAIL_LIMIT_SEQ;
        if (wantExecutorFailTxLedger > 0) {

            Set<String> waitExecutor = seqHashMapping.remove(wantExecutorFailTxLedger);

            if (waitExecutor != null && !waitExecutor.isEmpty()) {
                waitExecutor.forEach(hash -> failTxExecutor.execute(new FailProcess(rpcService, hashMessageMapping.remove(hash))));
            }
        }
    }

    /**
     * 失败处理器
     */
    private static class FailProcess implements Runnable{

        private RpcService rpcService;
        private Set<TransactionExecutedEventMessage> executedEventMessages;
        private long success = 0;
        private long notFound = -1;
        private long repeatReceive = 3;

        private FailProcess(RpcService rpcService, Set<TransactionExecutedEventMessage> executedEventMessages){
            this.rpcService = rpcService;
            this.executedEventMessages = executedEventMessages == null ? new HashSet<>() : executedEventMessages;
        }

        @Override
        public void run(){
            if (!executedEventMessages.iterator().hasNext()) {
                return;
            }
            TransactionExecutedEventMessage message = executedEventMessages.iterator().next();
            String txHash = message.getHash();
            TransactionHistory transactionHistory = rpcService.getTransactionHistoryByHash(txHash);

            long errorCode = getErrorCode(txHash, transactionHistory);

            // 没有生成交易记录，那么从错误堆中取出最合适的错误信息
            if (errorCode == notFound) {
                TransactionExecutedEventMessage failMessage = filterBestMessage();
                EventBusService.publishEvent(TransactionNotifyEventSource.CODE, GsonUtil.toJson(failMessage));
                return;
            }


            // 有交易记录生成，直接取交易记录的状态进行处理
            if (errorCode != success) {
                String errorDesc = BlockchainError.getDescription((int) errorCode);
                if (errorDesc == null) {
                    LOGGER.warn("errorCode mapping desc not found , errorCode=" + errorCode);
                }

                message.setErrorCode(String.valueOf(errorCode));
                message.setErrorMessage(errorDesc);
                EventBusService.publishEvent(TransactionNotifyEventSource.CODE, GsonUtil.toJson(message));
            }

        }

        private TransactionExecutedEventMessage filterBestMessage(){
            // 选出最合适的错误消息，1由于一定会收到errorCode3，那么它的优先级最低,其它错误有就返回
            for (TransactionExecutedEventMessage message : executedEventMessages) {
                if (Long.valueOf(message.getErrorCode()) != repeatReceive) {
                    return message;
                }
            }
            return executedEventMessages.iterator().next();
        }

        private long getErrorCode(String txHash, TransactionHistory transactionHistory){
            if (transactionHistory != null) {
                Transaction[] transactions = transactionHistory.getTransactions();
                if (transactions != null && transactions.length > 0) {
                    Transaction transaction = transactionHistory.getTransactions()[0];
                    LOGGER.debug("FailProcess:check txHash," + txHash + ",result:" + transaction.getErrorCode());
                    if (txHash.equals(transaction.getHash())) {
                        return transaction.getErrorCode();
                    }
                }
            }
            return notFound;
        }
    }

}
