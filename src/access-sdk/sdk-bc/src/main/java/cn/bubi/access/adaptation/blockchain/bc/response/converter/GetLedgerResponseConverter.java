package cn.bubi.access.adaptation.blockchain.bc.response.converter;

import cn.bubi.access.adaptation.blockchain.bc.response.ledger.Ledger;
import cn.bubi.component.http.converters.JsonResponseConverter;
import cn.bubi.component.http.converters.ResponseConverter;
import cn.bubi.component.http.core.HttpServiceContext;
import cn.bubi.component.http.core.ServiceRequest;
import com.alibaba.fastjson.JSONObject;

import java.io.InputStream;

public class GetLedgerResponseConverter implements ResponseConverter{
    private JsonResponseConverter jsonResponseConverter = new JsonResponseConverter(ServiceResponse.class);

    @Override
    public Object getResponse(ServiceRequest request, InputStream responseStream, HttpServiceContext serviceContext) throws Exception{
        ServiceResponse serviceResponse = (ServiceResponse) jsonResponseConverter.getResponse(request, responseStream, null);
        if (serviceResponse == null || !"0".equals(serviceResponse.getErrorCode())) {
            return null;
        }
        return JSONObject.toJavaObject(serviceResponse.getResult(), Ledger.class);
    }


}
