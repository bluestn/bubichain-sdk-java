package cn.bubi.access.starter;

import cn.bubi.sdk.core.balance.model.RpcServiceConfig;
import cn.bubi.sdk.core.exception.SdkError;
import cn.bubi.sdk.core.exception.SdkException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 区块链的属性；
 */
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties{

    static final String IP_SEPARATOR = ",";
    static final String PORT_SEPARATOR = ":";


    private NodeProperties node = new NodeProperties();

    private Server event = new Server();


    public NodeProperties getNode(){
        return node;
    }

    public void setNode(NodeProperties node){
        this.node = node;
    }

    public Server getEvent(){
        return event;
    }

    public void setEvent(Server event){
        this.event = event;
    }


    /**
     * 区块链节点的属性；
     */
    public static class NodeProperties{
        /**
         * 区块链节点的主机名；
         * demo 192.168.10.100:29333,192.168.10.110:29333,192.168.10.120:29333,192.168.10.130:29333
         */
        @NotNull
        private String ip;

        /**
         * 区块链节点的连接是否使用 https ；
         */
        private boolean https = false;


        List<RpcServiceConfig> converterRpcServiceConfig() throws SdkException{
            try {
                return Stream.of(ip.split(IP_SEPARATOR))
                        .map(ip -> {
                            if (!ip.contains(PORT_SEPARATOR) || ip.length() < 5) {
                                return null;
                            }
                            return new RpcServiceConfig(ip.split(PORT_SEPARATOR)[0], Integer.valueOf(ip.split(PORT_SEPARATOR)[1]), https);
                        })
                        .filter(Objects:: nonNull).collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
                throw new SdkException(SdkError.PARSE_IP_ERROR);
            }
        }

        public String getIp(){
            return ip;
        }

        public void setIp(String ip){
            this.ip = ip;
        }

        public boolean isHttps(){
            return https;
        }

        public void setHttps(boolean https){
            this.https = https;
        }

    }

    public static class Server{
        /**
         * 区块链节点事件通知监听地址；
         * demo ws://192.168.10.100:7053,ws://192.168.10.110:7053,ws://192.168.10.120:7053,ws://192.168.10.130:7053
         */
        @NotNull
        private String uri;


        List<String> converterUri() throws SdkException{
            try {
                return Stream.of(uri.split(IP_SEPARATOR)).collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
                throw new SdkException(SdkError.PARSE_URI_ERROR);
            }
        }

        public String getUri(){
            return uri;
        }

        public void setUri(String uri){
            this.uri = uri;
        }


    }
}
