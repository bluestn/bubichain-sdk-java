package cn.bubi.sdk.core.transaction.model;

import cn.bubi.access.utils.codec.HexUtils;
import cn.bubi.access.utils.io.ByteBlob;
import cn.bubi.access.utils.security.ShaUtils;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/10/25 上午11:20.
 */
public class TransactionBlob{
    private String hash;
    private ByteBlob bytesBlob;

    public TransactionBlob(byte[] bytes){
        this.bytesBlob = ByteBlob.wrap(bytes);
        this.hash = initHash(bytes);
    }

    private String initHash(byte[] bytes){
        byte[] hashBytes = ShaUtils.hash_256(bytes);
        return HexUtils.encode(hashBytes);
    }

    public String getHash(){
        return hash;
    }

    public ByteBlob getBytes(){
        return bytesBlob;
    }

    public String getHex(){
        return bytesBlob.toHexString();
    }

}
