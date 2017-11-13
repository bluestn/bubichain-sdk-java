package cn.bubi.sdk.core.transaction;

import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.access.adaptation.blockchain.bc.request.SignatureRequest;
import cn.bubi.access.adaptation.blockchain.bc.request.SubTransactionRequest;
import cn.bubi.access.adaptation.blockchain.bc.request.TransactionRequest;
import cn.bubi.access.adaptation.blockchain.exception.BlockchainException;
import cn.bubi.access.utils.io.ByteBlob;
import cn.bubi.access.utils.security.ShaUtils;
import cn.bubi.blockchain.adapter3.Chain;
import cn.bubi.component.http.exception.HttpServiceException;
import cn.bubi.encryption.BubiKey;
import cn.bubi.sdk.core.balance.NodeManager;
import cn.bubi.sdk.core.event.bottom.TxFailManager;
import cn.bubi.sdk.core.exception.SdkError;
import cn.bubi.sdk.core.exception.SdkException;
import cn.bubi.sdk.core.operation.BcOperation;
import cn.bubi.sdk.core.operation.BuildConsume;
import cn.bubi.sdk.core.seq.SequenceManager;
import cn.bubi.sdk.core.transaction.model.Digest;
import cn.bubi.sdk.core.transaction.model.Signature;
import cn.bubi.sdk.core.transaction.model.TransactionBlob;
import cn.bubi.sdk.core.transaction.model.TransactionCommittedResult;
import cn.bubi.sdk.core.transaction.sync.AsyncFutureTx;
import cn.bubi.sdk.core.transaction.sync.TransactionSyncManager;
import cn.bubi.sdk.core.utils.Assert;
import cn.bubi.sdk.core.utils.SwallowUtil;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/24 下午5:21.
 * 表示一笔交易执行,并不能表示事务
 * 线程不安全，多个线程不要共享一个交易操作
 */
public class Transaction{

    private Logger logger = LoggerFactory.getLogger(Transaction.class);
    public static final int FAIL_LIMIT_SEQ = 2;// 间隔2个seq作为失败确认

    private String sponsorAddress;// 发起人
    private long nonce;// 生成blob时再去获取
    private List<BcOperation> operationList = new ArrayList<>();// 操作列表
    private List<Signature> signatures = new ArrayList<>();// 签名列表
    private List<Digest> digests = new ArrayList<>();// 签名摘要列表
    private TransactionBlob transactionBlob;// blob信息

    private String txMetadata;// 交易metadata

    private SequenceManager sequenceManager;
    private RpcService rpcService;
    private TransactionSyncManager transactionSyncManager;
    private NodeManager nodeManager;
    private TxFailManager txFailManager;

    private boolean complete = false;
    private static final int TX_COMMITING_TIMEOUT = 18 * 1000;

    public Transaction(String sponsorAddress, SequenceManager sequenceManager, RpcService rpcService, TransactionSyncManager transactionSyncManager, NodeManager nodeManager, TxFailManager txFailManager){
        this.sponsorAddress = sponsorAddress;
        this.sequenceManager = sequenceManager;
        this.rpcService = rpcService;
        this.transactionSyncManager = transactionSyncManager;
        this.nodeManager = nodeManager;
        this.txFailManager = txFailManager;
    }

    public Transaction buildAddSigner(String publicKey, String privateKey) throws SdkException{
        return buildTemplate(() -> signatures.add(new Signature(publicKey, privateKey)));
    }

    public Transaction buildAddDigest(String publicKey, byte[] originDigest) throws SdkException{
        return buildTemplate(() -> digests.add(new Digest(publicKey, originDigest)));
    }

    public Transaction buildAddOperation(BcOperation operation) throws SdkException{
        return buildTemplate(() -> {
            if (operation != null) {
                operationList.add(operation);
            }
        });
    }

    public Transaction buildTxMetadata(String txMetadata) throws SdkException{
        return buildTemplate(() -> this.txMetadata = txMetadata);
    }

    private Transaction buildTemplate(BuildConsume buildConsume) throws SdkException{
        checkCanExecute();
        buildConsume.build();
        return this;
    }

    private void checkCanExecute() throws SdkException{
        Assert.notTrue(complete, SdkError.TRANSACTION_ERROR_STATUS);
    }

    /**
     * 只有一个签名的快速访问方法
     */
    public TransactionCommittedResult commit(String publicKey, String privateKey) throws SdkException{
        buildAddSigner(publicKey, privateKey);
        return commit();
    }

    public TransactionCommittedResult commit() throws SdkException{
        return commit(true);
    }

    public TransactionCommittedResult commit(boolean sync) throws SdkException{
        if (transactionBlob == null) {
            generateBlob();
        }
        return submit(sync);
    }

    /**
     * 针对blob需要前端签名所提供出的blob获得方法
     */
    public TransactionBlob generateBlob() throws SdkException{
        checkGeneratorBlobStatus();
        nonce = sequenceManager.getSequenceNumber(sponsorAddress);
        transactionBlob = generateTransactionBlob();
        return transactionBlob;
    }

    public TransactionBlob getTransactionBlob() throws SdkException{
        Assert.notTrue(transactionBlob == null, SdkError.TRANSACTION_ERROR_BLOB_NOT_NULL);
        return transactionBlob;
    }

    private TransactionCommittedResult submit(boolean sync) throws SdkException{
        complete();
        checkCommitStatus();
        TransactionCommittedResult committedResult = new TransactionCommittedResult();
        try {
            String txHash = transactionBlob.getHash();

            logger.debug("提交交易txHash=" + txHash);

            AsyncFutureTx txFuture = new AsyncFutureTx(txHash);
            transactionSyncManager.addAsyncFutureTx(txFuture);

            try {
                SubTransactionRequest subTransactionRequest = getSubTransactionRequest();
                verifyPre(subTransactionRequest);
                rpcService.submitTransaction(subTransactionRequest);
            } catch (RuntimeException e) {
                transactionSyncManager.remove(txFuture);
                if (e instanceof HttpServiceException && e.getCause() instanceof BlockchainException) {
                    throw new SdkException((BlockchainException) e.getCause());
                }
                throw e;
            }

            if (sync) {

                boolean timeout = !txFuture.awaitUninterruptibly(TX_COMMITING_TIMEOUT);

                if (timeout) {
                    logger.warn(String.format("Transaction sync commit timeout! --[Hash=%s][MaxTimeout=%s]", txHash, TX_COMMITING_TIMEOUT));
                    throw new SdkException(SdkError.TRANSACTION_ERROR_TIMEOUT);
                }
                if (!success(txFuture.getErrorCode())) {
                    throw new SdkException(Integer.valueOf(txFuture.getErrorCode()), txFuture.getErrorMessage());
                }

            }

            committedResult.setHash(txHash);

            transactionSyncManager.remove(txFuture);
        } catch (Exception e) {
            sequenceManager.reset(sponsorAddress);
            throw e;
        }

        return committedResult;

    }

    private boolean success(String errorCode){
        String success = "0";
        return errorCode == null || success.equals(errorCode);
    }


    private TransactionBlob generateTransactionBlob() throws SdkException{
        Chain.Transaction.Builder builder = Chain.Transaction.newBuilder();
        if (txMetadata != null) {
            builder.setMetadata(ByteString.copyFromUtf8(Hex.encodeHexString(txMetadata.getBytes())));
        }
        builder.setSourceAddress(sponsorAddress);
        builder.setNonce(nonce);

        long nowSeq = nodeManager.getLastSeq();
        long maxSeq = nowSeq + FAIL_LIMIT_SEQ;
        logger.debug("last seq:" + maxSeq);

        for (BcOperation bcOperation : operationList) {
            bcOperation.buildTransaction(builder, maxSeq);
        }

        cn.bubi.blockchain.adapter3.Chain.Transaction transaction = builder.build();
        logger.info("transaction:" + transaction);
        byte[] bytesBlob = transaction.toByteArray();

        TransactionBlob transactionBlob = new TransactionBlob(bytesBlob);

        // 针对交易比较少时的通知机制
        txFailManager.addFailEventHash(nowSeq, transactionBlob.getHash());

        return transactionBlob;
    }

    private SubTransactionRequest getSubTransactionRequest() throws SdkException{
        TransactionRequest tranRequest = new TransactionRequest();
        tranRequest.setSignatures(getSignatures(transactionBlob.getBytes()));
        tranRequest.setTransactionBlob(transactionBlob.getHex());
        TransactionRequest[] transactionRequests = new TransactionRequest[1];
        transactionRequests[0] = tranRequest;
        SubTransactionRequest subTranRequest = new SubTransactionRequest();
        subTranRequest.setItems(transactionRequests);
        return subTranRequest;
    }

    private SignatureRequest[] getSignatures(ByteBlob byteBlob) throws SdkException{
        List<SignatureRequest> signatureRequests = new ArrayList<>();
        for (Signature signature : signatures) {
            SignatureRequest signatureRequest = new SignatureRequest();
            signatureRequest.setPublicKey(signature.getPublicKey());
            SwallowUtil.swallowException(() -> {
                signatureRequest.setSignData(Hex.encodeHexString(ShaUtils.signV3(byteBlob.toBytes(), signature.getPrivateKey(), signature.getPublicKey())));// 将签名信息转换为16进制
            }, SdkError.SIGNATURE_ERROR_PUBLIC_PRIVATE);
            signatureRequests.add(signatureRequest);
        }
        digests.forEach(digest -> {
            SignatureRequest signatureRequest = new SignatureRequest();
            signatureRequest.setPublicKey(digest.getPublicKey());
            signatureRequest.setSignData(Hex.encodeHexString(digest.getOriginDigest()));// // 将签名信息转换为16进制
            signatureRequests.add(signatureRequest);
        });
        return signatureRequests.toArray(new SignatureRequest[signatureRequests.size()]);
    }

    private void verifyPre(SubTransactionRequest subTransactionRequest) throws SdkException{
        TransactionRequest transactionRequest = subTransactionRequest.getItems()[0];
        for (SignatureRequest signatureRequest : transactionRequest.getSignatures()) {
            SwallowUtil.swallowException(() -> {
                boolean thisSignatureResult = BubiKey.verify(transactionBlob.getBytes().toBytes(), Hex.decodeHex(signatureRequest.getSignData().toCharArray()), signatureRequest.getPublicKey());
                Assert.isTrue(thisSignatureResult, SdkError.EVENT_ERROR_SIGNATURE_VERIFY_FAIL);
            }, SdkError.EVENT_ERROR_SIGNATURE_VERIFY_FAIL);
        }
    }

    private void complete() throws SdkException{
        Assert.notTrue(complete, SdkError.TRANSACTION_ERROR_STATUS);
        complete = true;
    }

    private void checkGeneratorBlobStatus() throws SdkException{
        Assert.notEmpty(sponsorAddress, SdkError.TRANSACTION_ERROR_SPONSOR);
        Assert.isNull(transactionBlob, SdkError.TRANSACTION_ERROR_BLOB_REPEAT_GENERATOR);
        Assert.notTrue(operationList.isEmpty(), SdkError.TRANSACTION_ERROR_OPERATOR_NOT_EMPTY);
        Assert.notTrue(complete, SdkError.TRANSACTION_ERROR_STATUS);
    }

    private void checkCommitStatus() throws SdkException{
        Assert.notTrue(signatures.isEmpty() && digests.isEmpty(), SdkError.TRANSACTION_ERROR_SIGNATURE);
        Assert.checkCollection(signatures, signature -> Assert.notEmpty(signature.getPublicKey(), SdkError.TRANSACTION_ERROR_PUBLIC_KEY_NOT_EMPTY));
        Assert.checkCollection(signatures, signature -> Assert.notEmpty(signature.getPrivateKey(), SdkError.TRANSACTION_ERROR_PRIVATE_KEY_NOT_EMPTY));
        Assert.notTrue(transactionBlob == null, SdkError.TRANSACTION_ERROR_BLOB_NOT_NULL);
        Assert.isTrue(complete, SdkError.TRANSACTION_ERROR_STATUS);
    }
}
