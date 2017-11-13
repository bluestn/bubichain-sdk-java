package cn.bubi.sdk.sample;

import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.sdk.core.balance.NodeManager;
import cn.bubi.sdk.core.balance.RpcServiceLoadBalancing;
import cn.bubi.sdk.core.balance.model.RpcServiceConfig;
import cn.bubi.sdk.core.event.EventBusService;
import cn.bubi.sdk.core.event.bottom.BlockchainMqHandler;
import cn.bubi.sdk.core.event.bottom.TxFailManager;
import cn.bubi.sdk.core.event.bottom.TxMqHandleProcess;
import cn.bubi.sdk.core.event.handle.EventHandler;
import cn.bubi.sdk.core.event.handle.LedgerSeqIncreaseEventHandler;
import cn.bubi.sdk.core.event.handle.TransactionNotifyEventHandler;
import cn.bubi.sdk.core.exception.SdkException;
import cn.bubi.sdk.core.seq.SeqNumberManager;
import cn.bubi.sdk.core.seq.SequenceManager;
import cn.bubi.sdk.core.spi.BcOperationService;
import cn.bubi.sdk.core.spi.BcOperationServiceImpl;
import cn.bubi.sdk.core.spi.BcQueryService;
import cn.bubi.sdk.core.spi.BcQueryServiceImpl;
import cn.bubi.sdk.core.transaction.sync.TransactionSyncManager;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 下午3:34.
 * 对sdk的简单配置
 */
public class Config{

    private BcOperationService operationService;
    private BcQueryService queryService;

    public void configSdk() throws SdkException{

        String eventUtis = "ws://192.168.10.100:7053,ws://192.168.10.110:7053,ws://192.168.10.120:7053,ws://192.168.10.130:7053";
        String ips = "192.168.10.100:29333,192.168.10.110:29333,192.168.10.120:29333,192.168.10.130:29333";

        // 解析原生配置参数
        List<RpcServiceConfig> rpcServiceConfigList = Stream.of(ips.split(","))
                .map(ip -> {
                    if (!ip.contains(":") || ip.length() < 5) {
                        return null;
                    }
                    return new RpcServiceConfig(ip.split(":")[0], Integer.valueOf(ip.split(":")[1]));
                })
                .filter(Objects:: nonNull).collect(Collectors.toList());

        // 1 配置nodeManager
        NodeManager nodeManager = new NodeManager(rpcServiceConfigList);

        // 2 配置rpcService
        RpcService rpcService = new RpcServiceLoadBalancing(rpcServiceConfigList, nodeManager);

        // 3 配置mq以及配套设施 可以配置多个节点监听，收到任意监听结果均可处理
        TxFailManager txFailManager = new TxFailManager(rpcService);
        txFailManager.init();

        TxMqHandleProcess mqHandleProcess = new TxMqHandleProcess(txFailManager);
        for (String uri : eventUtis.split(",")) {
            new BlockchainMqHandler(uri, mqHandleProcess).init();
        }

        // 4 配置seqManager
        SequenceManager sequenceManager = new SeqNumberManager(rpcService);
        sequenceManager.init();

        // 5 配置transactionSyncManager
        TransactionSyncManager transactionSyncManager = new TransactionSyncManager();
        transactionSyncManager.init();

        // 6 初始化同步通知器与区块增长通知器
        EventHandler notifyEventHandler = new TransactionNotifyEventHandler(sequenceManager, transactionSyncManager);
        EventHandler seqIncreaseEventHandler = new LedgerSeqIncreaseEventHandler(txFailManager, nodeManager);

        // 7 配置事件总线
        EventBusService.addEventHandler(notifyEventHandler);
        EventBusService.addEventHandler(seqIncreaseEventHandler);

        // 8 初始化spi
        BcOperationService operationService = new BcOperationServiceImpl(sequenceManager, rpcService, transactionSyncManager, nodeManager, txFailManager);
        BcQueryService queryService = new BcQueryServiceImpl(rpcService);

        this.operationService = operationService;
        this.queryService = queryService;
    }

    BcOperationService getOperationService(){
        return operationService;
    }

    BcQueryService getQueryService(){
        return queryService;
    }
}
