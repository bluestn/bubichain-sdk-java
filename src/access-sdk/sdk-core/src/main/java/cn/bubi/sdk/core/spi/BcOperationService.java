package cn.bubi.sdk.core.spi;

import cn.bubi.sdk.core.transaction.Transaction;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/24 上午11:39.
 * 区块链操作服务
 */
public interface BcOperationService{

    /**
     * 开启一笔交易
     *
     * @param sponsorAddress 发起人
     * @see cn.bubi.sdk.core.operation.OperationFactory 操作工厂
     */
    Transaction newTransaction(String sponsorAddress);

}
