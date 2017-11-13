package cn.bubi.sdk.core.utils;

import cn.bubi.sdk.core.exception.SdkError;
import cn.bubi.sdk.core.exception.SdkException;

import java.io.UnsupportedEncodingException;

/**
 * @author xiezhengchao@bubi.cn
 * @since 17/11/8 上午10:48.
 */
public class SwallowUtil{

    public static byte[] getBytes(String originStr){
        try {
            return originStr.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public static void swallowException(SDKConsume consume, SdkError sdkError) throws SdkException{
        try {
            consume.doSelf();
        } catch (Exception e) {
            // swallow exception
            if (e instanceof SdkException)
                throw (SdkException) e;

            throw new SdkException(sdkError);
        }
    }

    @FunctionalInterface
    public interface SDKConsume{

        void doSelf() throws Exception;

    }
}
