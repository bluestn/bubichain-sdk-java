package cn.bubi.access.adaptation.blockchain.bc.response.converter;

import cn.bubi.component.http.converters.JsonResponseConverter;
import cn.bubi.component.http.converters.ResponseConverter;
import cn.bubi.component.http.core.HttpServiceContext;
import cn.bubi.component.http.core.ServiceRequest;

import java.io.InputStream;

public abstract class AbstractResponseConverter implements ResponseConverter{
    private JsonResponseConverter jsonResponseConverter = new JsonResponseConverter(ServiceResponse.class);

    @Override
    public Object getResponse(ServiceRequest request, InputStream responseStream, HttpServiceContext serviceContext) throws Exception{
        ServiceResponse serviceResponse = (ServiceResponse) jsonResponseConverter.getResponse(request, responseStream, null);
        //		if (serviceResponse == null ||! "0".equals(serviceResponse.getErrorCode())) {
        //			throw new RuntimeException("errorCode:"+serviceResponse.getErrorCode());
        //		}
        return dealResult(serviceResponse);
    }

    public abstract Object dealResult(ServiceResponse serviceResponse);

}
