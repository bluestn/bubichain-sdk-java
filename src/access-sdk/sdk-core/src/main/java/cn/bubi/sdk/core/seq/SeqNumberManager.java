package cn.bubi.sdk.core.seq;

import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.sdk.core.exception.SdkException;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/24 下午4:58.
 * 一个简单的seq管理器，可拓展为服务功能
 */
public class SeqNumberManager implements SequenceManager{

    private SimpleSequenceManager simpleSequenceManager;

    public SeqNumberManager(RpcService rpcService){
        simpleSequenceManager = new SimpleSequenceManager(rpcService);
    }


    @Override
    public void init(){
        simpleSequenceManager.init();
    }

    @Override
    public void destroy(){
        simpleSequenceManager.destroy();
    }

    @Override
    public long getSequenceNumber(String address) throws SdkException{
        return simpleSequenceManager.getSequenceNumber(address);
    }

    @Override
    public void reset(String address){
        simpleSequenceManager.reset(address);
    }

}
