package net.oneki.mtac.util.exception;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.mutable.MutableObject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import net.oneki.mtac.util.exception.BusinessException;
import net.oneki.mtac.util.exception.ICustomException;
import net.oneki.mtac.util.exception.JsonException;
import net.oneki.mtac.util.exception.JsonExceptionConvertible;
import net.oneki.mtac.util.exception.TechnicalException;
import net.oneki.mtac.util.exception.UnexpectedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(Include.NON_NULL)
public class JsonException implements JsonExceptionConvertible {

    // -- Properties ---------------------------------------------------------

    private String id; // short ID that can be used to find the error in Kibana. To be displayed to end user

     private String code;

    private String message;

    private String trace;

   private Map<String, Object> params;

    // -- Method(s) ----------------------------------------------------------
    
    public BusinessException asBusinessException() {
        return asBusinessException(null);
    }
    
    public BusinessException asBusinessException(Integer status) {
        return fillParams(new BusinessException(code, message), status);
    }

    public TechnicalException asTechnicalException() {
        return asTechnicalException(null);
    }
    
    public TechnicalException asTechnicalException(Integer status) {
        return fillParams(new TechnicalException(code, message), status);
    }

    public UnexpectedException asUnexpectedException() {
        return asUnexpectedException(null);
    }
    
    public UnexpectedException asUnexpectedException(Integer status) {
        return fillParams(new UnexpectedException(code, message), status);
    }

    public RuntimeException fromStatus(Integer status) {

        MutableObject<RuntimeException> resultHolder = new MutableObject<>();
        
        fromStatus(status, new Handler() {
            @Override
            public void business(BusinessException e) {
                resultHolder.setValue(e);
            }
            @Override
            public void technical(TechnicalException e) {
                resultHolder.setValue(e);
            }
            @Override
            public void unexpected(UnexpectedException e) {
                resultHolder.setValue(e);
            }
        });
        
        return resultHolder.getValue();
    }
    
    public void fromStatus(Integer status, Handler handler) {

        // 4XX error -> business exception
        if (status >= 400 && status < 500) {
            handler.business(asBusinessException(status));
            return;
        }

        // 5XX error -> technical exception
        if (status >= 500 && status < 600) {
            handler.technical(asTechnicalException(status));
            return;
        }

        // Other error -> unepected exception
        handler.unexpected(asUnexpectedException(status));
    }    
    

    public interface Handler {
        
        void business(BusinessException e);
        
        void technical(TechnicalException e);
        
        void unexpected(UnexpectedException e);
    }

    private <T extends ICustomException> T fillParams(T exception, Integer status) {
        Optional.ofNullable(params).ifPresent(p -> p.forEach((k, v) -> exception.param(k, v)));
        exception.setStatus(status);
        return exception;
    }

    @Override
    public JsonException asJsonException() {
        return this;
    }
    
    public JsonException applyDefaultCode(String defaultCode) {
        
        code = Optional.ofNullable(code).orElse(defaultCode);
        return this;
    }
}