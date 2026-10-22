package com.feng.dsagent.common;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class ClosedStreamErrorTest {
    @Test void clientDisconnectDoesNotAttemptAnotherBodyOnAClosedStream() throws Exception {
        var response=new MockHttpServletResponse();response.setContentType("text/event-stream");response.flushBuffer();
        var resolver=new ApiErrorFallbackResolver(JsonMapper.builder().build());
        assertNotNull(resolver.resolveException(new MockHttpServletRequest(),response,null,new AsyncRequestNotUsableException("client disconnected")));
        assertEquals("",response.getContentAsString());
    }
    @Test void ordinaryStreamAuthorizationFailureStillHasItsRealStatusAndEnvelope() throws Exception {
        var request=new MockHttpServletRequest();request.addHeader("Accept","text/event-stream");var response=new MockHttpServletResponse();
        var resolver=new ApiErrorFallbackResolver(JsonMapper.builder().build());
        assertNotNull(resolver.resolveException(request,response,null,new ApiException(HttpStatus.NOT_FOUND,"STREAM_NOT_FOUND","课堂进度不存在")));
        assertEquals(404,response.getStatus());assertTrue(response.getContentAsString().contains("STREAM_NOT_FOUND"));
    }
}
