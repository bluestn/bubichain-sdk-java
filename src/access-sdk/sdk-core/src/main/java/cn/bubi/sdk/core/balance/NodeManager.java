package cn.bubi.sdk.core.balance;

import cn.bubi.access.adaptation.blockchain.bc.RpcService;
import cn.bubi.component.http.core.HttpServiceAgent;
import cn.bubi.component.http.core.connection.ServiceEndpoint;
import cn.bubi.sdk.core.balance.model.RpcServiceConfig;
import cn.bubi.sdk.core.exception.SdkError;
import cn.bubi.sdk.core.exception.SdkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/26 下午5:05.
 * 节点管理器，控制所有节点的访问优先级，动态路由
 */
public class NodeManager{

    private Logger logger = LoggerFactory.getLogger(NodeManager.class);

    private final Object lock = new Object();
    private volatile String host;
    private volatile long seq;

    // 指定一个默认的host作为初始值
    public NodeManager(List<RpcServiceConfig> rpcServiceConfigs) throws SdkException{
        for (RpcServiceConfig rpcServiceConfig : rpcServiceConfigs) {
            try {
                logger.info("node manager init try host :" + rpcServiceConfig.getHost());
                HttpServiceAgent.clearMemoryCache();
                ServiceEndpoint serviceEndpoint = new ServiceEndpoint(rpcServiceConfig.getHost(), rpcServiceConfig.getPort(), rpcServiceConfig.isHttps());
                RpcService rpcService = HttpServiceAgent.createService(RpcService.class, serviceEndpoint);
                this.seq = rpcService.getLedger().getHeader().getSeq();
                this.host = rpcServiceConfig.getHost();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (StringUtils.isEmpty(host)) {
            throw new SdkException(SdkError.NODE_MANAGER_INIT_ERROR);
        }
    }

    /**
     * 获得最高节点
     */
    public String getLastHost(){
        return host;
    }

    /**
     * 获得最新的seq
     */
    public long getLastSeq(){
        return seq;
    }

    /**
     * 通知更新
     */
    public void notifySeqUpdate(String host, long newSeq){
        synchronized (lock) {
            if (seq < newSeq) {
                seq = newSeq;
                this.host = host;
            }
        }
    }

}
