package cn.bubi.sdk.test;

import cn.bubi.access.adaptation.blockchain.bc.OperationTypeV3;
import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.access.adaptation.blockchain.bc.response.Account;
import cn.bubi.access.utils.blockchain.BlockchainKeyPair;
import cn.bubi.access.utils.blockchain.SecureKeyGenerator;
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
import cn.bubi.sdk.core.operation.impl.CreateAccountOperation;
import cn.bubi.sdk.core.seq.SeqNumberManager;
import cn.bubi.sdk.core.seq.SequenceManager;
import cn.bubi.sdk.core.spi.BcOperationService;
import cn.bubi.sdk.core.spi.BcOperationServiceImpl;
import cn.bubi.sdk.core.spi.BcQueryService;
import cn.bubi.sdk.core.spi.BcQueryServiceImpl;
import cn.bubi.sdk.core.transaction.Transaction;
import cn.bubi.sdk.core.transaction.model.TransactionCommittedResult;
import cn.bubi.sdk.core.transaction.sync.TransactionSyncManager;
import cn.bubi.sdk.core.utils.GsonUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/27 下午2:36.
 * 测试部分主要测试了1负载能力，2正常操作，3部分常见异常
 */
public abstract class TestConfig{

    static final Logger LOGGER = LoggerFactory.getLogger(TestConfig.class);

    static final String CREATOR_ADDRESS = "a0012ea403227b861289ed5fcedd30e51e85ef7397ebc6";
    static final String CREATOR_PUBLIC_KEY = "b001e9fd31a0fc25af3123f67575cdd0c6b8c2192eead9f58728a3fb46accdc0faa67f";
    static final String CREATOR_PRIVATE_KEY = "c0018335e8c3e34cceaa24027207792318bc388bea443b53d5ba9e00e5adb6739bb61b";


    private static BcOperationService operationService;
    private static BcQueryService queryService;

    @BeforeClass
    public static void configSdk() throws SdkException{

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

        TestConfig.operationService = operationService;
        TestConfig.queryService = queryService;
    }

    /**
     * 创建账户操作
     */
    BlockchainKeyPair createAccountOperation() throws SdkException{

        Transaction transaction = getOperationService().newTransaction(CREATOR_ADDRESS);

        BlockchainKeyPair keyPair = SecureKeyGenerator.generateBubiKeyPair();
        LOGGER.info(GsonUtil.toJson(keyPair));

        CreateAccountOperation createAccountOperation = new CreateAccountOperation.Builder()
                .buildDestAddress(keyPair.getBubiAddress())
                .buildScript("function main(input) { /*do what ever you want*/ }")
                .buildAddMetadata("boot自定义key1", "boot自定义value1").buildAddMetadata("boot自定义key2", "boot自定义value2")
                // 权限部分
                .buildPriMasterWeight(15)
                .buildPriTxThreshold(15)
                .buildAddPriTypeThreshold(OperationTypeV3.CREATE_ACCOUNT, 8)
                .buildAddPriTypeThreshold(OperationTypeV3.SET_METADATA, 6)
                .buildAddPriTypeThreshold(OperationTypeV3.ISSUE_ASSET, 4)
                .buildAddPriSigner(SecureKeyGenerator.generateBubiKeyPair().getBubiAddress(), 10)
                .buildOperationMetadata("操作metadata")// 这个操作应该最后build
                .build();

        TransactionCommittedResult result = transaction.buildAddOperation(createAccountOperation)
                .buildTxMetadata("交易metadata")
                .buildAddSigner(CREATOR_PUBLIC_KEY, CREATOR_PRIVATE_KEY)
                .commit();

        resultProcess(result, "创建账号状态:");

        Account account = getQueryService().getAccount(keyPair.getBubiAddress());
        LOGGER.info("新建的账号:" + GsonUtil.toJson(account));
        Assert.assertNotNull("新建的账号不能查询到", account);

        return keyPair;
    }

    void resultProcess(TransactionCommittedResult result, String debugMessage){
        Assert.assertNotNull("result must not null", result);
        Assert.assertNotNull("交易hash不能为空", result.getHash());
        LOGGER.info(debugMessage + GsonUtil.toJson(result));
    }

    static BcOperationService getOperationService(){
        return operationService;
    }

    static BcQueryService getQueryService(){
        return queryService;
    }

}
