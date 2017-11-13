package cn.bubi.access.adaptation.blockchain.bc.response.converter;

import cn.bubi.access.adaptation.blockchain.bc.response.Account;
import cn.bubi.component.http.util.serialize.SerializeUtils;

/**
 * 解析rpc返回结果中的result
 *
 * @author hobo
 */
public class GetAccountResponseConverter extends AbstractResponseConverter{

    @Override
    public Object dealResult(ServiceResponse serviceResponse){
        //		return JSONObject.toJavaObject(serviceResponse.getResult(), Account.class);
        return SerializeUtils.deserializeAs(serviceResponse.getResult(), Account.class);
    }


}
