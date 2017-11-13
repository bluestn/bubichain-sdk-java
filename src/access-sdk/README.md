# Access-SDK使用说明

Access-SDK 使用说明

---
## 目录
 1. [前言](#前言)
 2. [概览](#概览)
 3. [配置说明](#配置说明)  
    - [1简单的config配置使用](#1简单的config配置使用)  
    - [2基于Spring Boot的starter方式自动配置](#2基于spring-boot的starter方式自动配置)  
 4. [使用说明](#使用说明)  
    - [1创建账户操作](#1创建账户操作)  
    - [2发行和转移资产操作](#2发行和转移资产操作)  
    - [3设置和修改metadata](#3设置和修改metadata)  
    - [4设置 修改权重](#4设置-修改权重)  
    - [5设置 修改门限](#5设置-修改门限)  
    - [6合约调用](#6合约调用)  
    - [7业务分支返回形式](#7业务分支返回形式)  


----------


## <div id="1">前言</div>  ##
本SDK目的为便捷的使用Java语法访问区块链，屏蔽底层细节。最大限度的方便应用开发者访问区块链。本SDK并无额外抽象与转换，所有的特性，抽象都以底层描述为准。现只支持3.0区块链的访问。


----------
## 概览 ##
现主要说明SDK功能  
1提供负载访问底层的能力  
2对底层通知的监听，同步交易  
3对底层支持的操作便捷创建，生成blob  
4本SDK的交易发起人seq现采用内存管理，如果使用方应用集群，那么必须配置seq服务，否则将会导致交易失败率很高。  


----------
## 配置说明 ##
SDK本身无任何依赖框架，使用时载入配置即可运行，考虑到实际应用中的web项目大部分都与Spring框架融合，现提供了sdk-starter项目供使用方快速使用。关于具体的项目配置实践可以参考sdk-sample项目，该项目提供了简单的配置访问与Spring Boot的接入访问两种方式的简单项目搭建。



> 使用前必读：由于使用了监听地址映射访问地址(也就是配置代码里的eventUtis和ips)，所以监听地址列表对应的ip或者域名必须要在访问地址里也有，否则负载失败会导致访问不了区块链服务！

> 其它说明
1JDK需要1.8以上
2具体操作参考区块链文档：https://github.com/bubicn/bubichain-v3/blob/master/docs/develop.md



### 1简单的config配置使用
``` java
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

```

> 基于简单配置引入sdk-core依赖即可，如：
```java
<dependency>
    <groupId>cn.bubi.access.sdk</groupId>
    <artifactId>sdk-core</artifactId>
    <version>${access-sdk.version}</version>
</dependency>
```

### 2基于Spring Boot的starter方式自动配置

在application.properties中配置参数，如：
```java 
# sdk config
blockchain.event.uri=ws://192.168.10.100:7053,ws://192.168.10.110:7053,ws://192.168.10.120:7053,ws://192.168.10.130:7053
blockchain.node.ip=192.168.10.100:29333,192.168.10.110:29333,192.168.10.120:29333,192.168.10.130:29333
```
还需要项目依赖引入sdk-starter
```java
<dependency>
    <groupId>cn.bubi.access.sdk</groupId>
    <artifactId>sdk-starter</artifactId>
    <version>${access-sdk-starter.version}</version>
</dependency>
```

> 在完成上述配置之后，通过使用BcOperationService和BcQueryService对象来完成具体操作。如果项目并未采用Spring Boot框架，可以参考Config类将相应对象以xml形式托管到Spring容器中管理，这里不再提供xml配置。

----------
## 使用说明  ##
完成配置之后，即可进行相应的操作了。这里列举所有支持的操作。也可以参考项目sdk-test查看所有操作的单元测试。

### 1创建账户操作

```java
/**
 * 1创建账户
 */
@Test
public void createAccount(){

    Transaction transaction = getOperationService().newTransaction(CREATOR_ADDRESS);

    BlockchainKeyPair keyPair = SecureKeyGenerator.generateBubiKeyPair();
    LOGGER.info(GsonUtil.toJson(keyPair));
    try {
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

        // 可以拿到blob,让前端签名
        TransactionBlob blob = transaction.buildAddOperation(createAccountOperation).generateBlob();

        // 签名完成之后可以继续提交,需要自己维护transaction保存
        TransactionCommittedResult result = transaction
                .buildAddSigner(CREATOR_PUBLIC_KEY, CREATOR_PRIVATE_KEY)
                //.buildAddDigest("公钥",new byte[]{}) 可以让前端的签名在这里加进来
                .commit();
        resultProcess(result, "创建账号状态:");

    } catch (SdkException e) {
        e.printStackTrace();
    }

    Account account = getQueryService().getAccount(keyPair.getBubiAddress());
    LOGGER.info("新建的账号:" + GsonUtil.toJson(account));
    Assert.assertNotNull("新建的账号不能查询到", account);
}
```

> 需要注意的通过操作对象获得的交易对象Transaction本身是线程不安全的，不要多线程引用。并且所有的操作对象和Transaction本身都是有状态的，不可以重复使用，每一个操作完成就可以丢弃了，如有新操作需要新建操作对象，交易对象也是如此。


> 提示：这里做了个让前端签名的模拟操作，如果调用方有需要可以参考。注意generateBlob()方法要先调用，这样生成了blob之后可以通过getTransactionBlob()方法再次从交易对象中拿到blob，但是如果没有生成blob就直接调用getTransactionBlob()会得到异常.



### 2发行和转移资产操作
```java
/**
 * 2发行和转移资产操作
 */
@Test
public void AssetOperation(){

    String assetCode = "asset-code";
    long amount = 100;
    long transferAmount = 9;
    try {
        BlockchainKeyPair user1 = createAccountOperation();

        Transaction issueTransaction = getOperationService().newTransaction(user1.getBubiAddress());

        issueTransaction
                .buildAddOperation(new IssueAssetOperation.Builder().buildAmount(amount).buildAssetCode(assetCode).build())
                .buildAddSigner(user1.getPubKey(), user1.getPriKey())
                .commit();

        Account account = getQueryService().getAccount(user1.getBubiAddress());
        LOGGER.info("user1资产:" + GsonUtil.toJson(account.getAssets()));
        Assert.assertNotNull("发行资产不能为空", account.getAssets());

        Transaction transferTransaction = getOperationService().newTransaction(user1.getBubiAddress());
        BlockchainKeyPair user2 = createAccountOperation();

        transferTransaction
                .buildAddOperation(OperationFactory.newPaymentOperation(user2.getBubiAddress(), user1.getBubiAddress(), assetCode, transferAmount))
                .buildAddSigner(user1.getPubKey(), user1.getPriKey())
                .commit();

    } catch (SdkException e) {
        e.printStackTrace();
    }
}

```

> 这里使用的OperationFactory是对操作的简单参数封装，应用调用方可根据实际的使用参数自行封装操作对象，这里列出所有可以进行的操作供参考。这里列出的是操作对象，所有的操作创建方法都应使用对应操作对象的Builder对象，比如创建账户操作需要CreateAccountOperation.Builder对象来创建。
```java
// 创建账户操作
CreateAccountOperation
// 调用合约方法（并不映射底层操作，仅仅是访问方法）
InvokeContractOperation
// 发行资产操作
IssueAssetOperation
// 转移资产操作
PaymentOperation
// 设置/修改metadata操作
SetMetadataOperation
// 设置/修改权重/签名列表操作
SetSignerWeightOperation
// 设置/修改交易门限操作
SetThresholdOperation
```  

   
### 3设置和修改metadata
   

```java
/**
 * 设置和修改metadata
 */
public void updateMetadata(){
    BlockchainKeyPair user = createAccountOperation();

    String key1 = "..自定义key1";
    String key2 = "..自定义key2";

    SetMetadata setMetadata = getQueryService().getAccount(user.getBubiAddress(), key1);
    setMetadata.setValue("这是新设置的value1");

    Transaction updateMetadataTransaction = getOperationService().newTransaction(user.getBubiAddress());
    updateMetadataTransaction
            .buildAddOperation(OperationFactory.newUpdateSetMetadataOperation(setMetadata))
            .buildAddSigner(user.getPubKey(), user.getPriKey())
            .commit();

    Account account = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("修改metadata结果:" + GsonUtil.toJson(account.getMetadatas()));
  

    Transaction newMetadataTransaction = getOperationService().newTransaction(user.getBubiAddress());
    newMetadataTransaction
            .buildAddOperation(OperationFactory.newSetMetadataOperation("newMetadataKey2", "newMetadataValue2"))
            .buildAddSigner(user.getPubKey(), user.getPriKey())
            .commit();

    Account account2 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("新建metadata结果:" + GsonUtil.toJson(account2.getMetadatas()));

    SetMetadata setMetadata2 = getQueryService().getAccount(user.getBubiAddress(), key2);
    setMetadata2.setValue("这是新设置的value222");

    Transaction updateMetadataTransaction2 = getOperationService().newTransaction(user.getBubiAddress());
    updateMetadataTransaction2
            .buildAddOperation(OperationFactory.newSetMetadataOperation(setMetadata2.getKey(), setMetadata2.getValue(), setMetadata2.getVersion()))
            .buildAddSigner(user.getPubKey(), user.getPriKey())
            .commit();

    Account account3 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("修改metadata2结果:" + GsonUtil.toJson(account3.getMetadatas()));
    
}
```

> 这里需要注意，如果是修改metadata值，必须先查询出来然后通过将整个SetMetadata对象传入到修改metadata操作中去，因为在修改metadata是需要做版本号递增控制的，如果自行提供版本也可以，SDK在生成操作时默认的行为是将传入的版本号+1。这种查询再修改可以避免自己做版本号管理。

### 4设置 修改权重

```java
/**
 * 设置/修改权重
 */
public void setSignerWeight(){
    BlockchainKeyPair user = createAccountOperation();

    BlockchainKeyPair keyPair = SecureKeyGenerator.generateBubiKeyPair();

    getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newSetSignerWeightOperation(keyPair.getBubiAddress(), 8))
            .commit(user.getPubKey(), user.getPriKey());

    Account account = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("增加一个签名人权重8:" + GsonUtil.toJson(account.getPriv()));

    Transaction setSignerWeightTransaction = getOperationService().newTransaction(user.getBubiAddress());
    TransactionCommittedResult setSignerWeightResult = setSignerWeightTransaction
            .buildAddOperation(OperationFactory.newSetSignerWeightOperation(20))
            .commit(user.getPubKey(), user.getPriKey());

    resultProcess(setSignerWeightResult, "修改权重结果状态:");

    Account account2 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("修改权重到20:" + GsonUtil.toJson(account2.getPriv()));


    getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newSetSignerWeightOperation(keyPair.getBubiAddress(), 0))
            .commit(user.getPubKey(), user.getPriKey());

    Account account3 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("移除一个签名人:" +    GsonUtil.toJson(account3.getPriv()));
}

```

### 5设置 修改门限

```java
/**
 * 设置/修改门限
 */
public void setThreshold(){
    BlockchainKeyPair user = createAccountOperation();

    getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newSetThresholdOperation(14))
            .commit(user.getPubKey(), user.getPriKey());

    Account account = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("更新交易门限到14:" + GsonUtil.toJson(account.getPriv()));

    getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newSetThresholdOperation(OperationTypeV3.CREATE_ACCOUNT, 10))
            .commit(user.getPubKey(), user.getPriKey());

    Account account2 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("更新创建账号1到10:" + GsonUtil.toJson(account2.getPriv()));

    getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newSetThresholdOperation(OperationTypeV3.SET_THRESHOLD, 2))
            .commit(user.getPubKey(), user.getPriKey());

    Account account3 = getQueryService().getAccount(user.getBubiAddress());
    LOGGER.info("新增设置门限6到2:" + GsonUtil.toJson(account3.getPriv()));

}

```

### 6合约调用

```java

/**
 * 合约调用
 */
public void invokeContract(){
    BlockchainKeyPair user = createAccountOperation();
    BlockchainKeyPair user2 = createAccountOperation();

    TransactionCommittedResult result = getOperationService()
            .newTransaction(user.getBubiAddress())
            .buildAddOperation(OperationFactory.newInvokeContractOperation(user2.getBubiAddress(), "inputdata"))
            .commit(user.getPubKey(), user.getPriKey());

    resultProcess(result, "合约调用。。。");

    TransactionHistory transactionHistory = getQueryService().getTransactionHistoryByHash(result.getHash());
}
```

> 合约定义通过创建账号传入script定义.

### 7业务分支返回形式

这里需要明确指出，如果提交的交易并没有正常处理，那么返回的信息SDK将统一处理成SdkException返回，这是一个可预期的异常，调用方必须对其处理，具体的错误码和错误信息描述可以参考底层3.0文档。这里做个示例:
```java
/**
 * 参数错误，公钥非法
 *
 * @see cn.bubi.access.adaptation.blockchain.exception.BlockchainError.WRONG_ARGUMENT
 */
public void illegalPublicKeyTest(){

    BlockchainKeyPair user = createAccountOperation();

    try {
        Transaction setSignerWeightTransaction = getOperationService().newTransaction(user.getBubiAddress());
        setSignerWeightTransaction
                .buildAddOperation(OperationFactory.newSetSignerWeightOperation(14))
                .commit("illegal public key", user.getPriKey());
    } catch (SdkException e) {
        LOGGER.info(" SdkException:" + GsonUtil.toJson(e));
        //  对业务提交交易失败时可以检查失败原因做对应的失败操作
    }

}

```

> 大多数失败情况的业务就是回滚数据库和转换调用方异常，主要是确保，交易失败时不要继续执行调用方正常业务



![结束][1]
 


  [1]: http://file26.mafengwo.net/M00/B6/C3/wKgB4lJyOiGAOryaAA6u2rm6dKs69.jpeg





