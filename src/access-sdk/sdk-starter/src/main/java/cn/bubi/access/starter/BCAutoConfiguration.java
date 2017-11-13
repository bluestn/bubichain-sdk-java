package cn.bubi.access.starter;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Stream;

/**
 * 自动配置类，详细类说明参考simple项目的config类
 */
@Configuration
@EnableConfigurationProperties(BlockchainProperties.class)
public class BCAutoConfiguration{

    /**
     * 提供操作功能
     */
    @Bean
    @ConditionalOnMissingBean
    public BcOperationService bcOperationService(SequenceManager bcSequenceManager, RpcService bcRpcService, TransactionSyncManager bcTransactionSyncManager, NodeManager bcNodeManager, TxFailManager txFailManager){
        return new BcOperationServiceImpl(bcSequenceManager, bcRpcService, bcTransactionSyncManager, bcNodeManager, txFailManager);
    }

    /**
     * 提供查询功能
     */
    @Bean
    @ConditionalOnMissingBean
    public BcQueryService bcQueryService(RpcService bcRpcService){
        return new BcQueryServiceImpl(bcRpcService);
    }


    /**
     * 其它配置组件
     */

    @Bean
    @ConditionalOnMissingBean
    public NodeManager bcNodeManager(BlockchainProperties blockchainProperties) throws SdkException{
        List<RpcServiceConfig> bcRpcServiceConfigs = blockchainProperties.getNode().converterRpcServiceConfig();
        return new NodeManager(bcRpcServiceConfigs);
    }

    @Bean
    @ConditionalOnMissingBean
    public RpcService bcRpcService(BlockchainProperties blockchainProperties, NodeManager bcNodeManager) throws SdkException{
        return new RpcServiceLoadBalancing(blockchainProperties.getNode().converterRpcServiceConfig(), bcNodeManager);
    }

    @Bean(initMethod = "init")
    @ConditionalOnMissingBean
    public TxFailManager bcTxFailManager(RpcService bcRpcService){
        return new TxFailManager(bcRpcService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TxMqHandleProcess bcTxMqHandleProcess(BlockchainProperties blockchainProperties, TxFailManager bcTxFailManager) throws SdkException{
        TxMqHandleProcess bcTxMqHandleProcess = new TxMqHandleProcess(bcTxFailManager);

        // 初始化mq监听
        List<String> uris = blockchainProperties.getEvent().converterUri();
        for (String uri : uris) {
            new BlockchainMqHandler(uri, bcTxMqHandleProcess).init();
        }

        return bcTxMqHandleProcess;
    }


    @Bean(initMethod = "init", destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public SequenceManager bcSequenceManager(RpcService bcRpcService){
        return new SeqNumberManager(bcRpcService);
    }

    @Bean(initMethod = "init", destroyMethod = "destroy")
    @ConditionalOnMissingBean
    public TransactionSyncManager bcTransactionSyncManager(){
        return new TransactionSyncManager();
    }

    @Bean("bcTransactionNotifyEventHandler")
    public EventHandler bcTransactionNotifyEventHandler(SequenceManager bcSequenceManager, TransactionSyncManager bcTransactionSyncManager){
        return new TransactionNotifyEventHandler(bcSequenceManager, bcTransactionSyncManager);
    }

    @Bean("bcSeqIncreaseEventHandler")
    public EventHandler bcSeqIncreaseEventHandler(TxFailManager bcTxFailManager, NodeManager bcNodeManager){
        return new LedgerSeqIncreaseEventHandler(bcTxFailManager, bcNodeManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventBusService bcEventBusService(EventHandler[] eventHandlers){
        if (eventHandlers != null && eventHandlers.length > 0) {
            Stream.of(eventHandlers).forEach(EventBusService:: addEventHandler);
        }
        return null;
    }

}
